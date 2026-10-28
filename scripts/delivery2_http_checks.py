"""HTTP checks invoked only by the disposable financial application runner."""
import calendar
from datetime import date, datetime, timedelta
from decimal import Decimal
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo


def verify_obligations_http(origin):
    address = urllib.parse.urlparse(origin)
    if address.scheme != 'http' or address.hostname != '127.0.0.1' or not address.port or address.port in (5173, 8080, 5432):
        raise RuntimeError('Delivery 2 HTTP checks require the disposable loopback application.')
    checks = 0

    def check(condition, label):
        nonlocal checks
        if not condition:
            raise AssertionError(label)
        checks += 1

    def client():
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

        def request(method, path, data=None, expected=200):
            headers = {'Origin': origin, 'Content-Type': 'application/json'}
            if method != 'GET':
                with opener.open(origin + '/api/auth/csrf', timeout=20) as response:
                    headers['X-XSRF-TOKEN'] = json.load(response)['token']
            req = urllib.request.Request(origin + '/api' + path,
                    data=None if data is None else json.dumps(data).encode(), method=method, headers=headers)
            try:
                response = opener.open(req, timeout=20)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                allowed = expected if isinstance(expected, tuple) else (expected,)
                if response.status not in allowed:
                    raise AssertionError(f'{method} {path}: HTTP {response.status}, expected {allowed}')
                raw = response.read()
                return json.loads(raw) if raw else None

        return request

    first, other = client(), client()
    email = f'obligations-{uuid.uuid4().hex}@example.com'
    password = 'DisposableFinance123!'
    first('POST', '/auth/register', {'name': 'Contas HTTP', 'email': email, 'password': password,
          'companyName': 'Empresa descartável Contas', 'companyPhone': '11999999999'}, 201)
    first('POST', '/auth/login', {'email': email, 'password': password})
    today = datetime.now(ZoneInfo('America/Sao_Paulo')).date()
    day = today.isoformat()
    account = first('GET', '/finance/accounts')[0]
    first('PUT', f'/finance/accounts/{account["id"]}', {'name': account['name'], 'type': account['type'],
          'openingBalance': '0.00', 'openingDate': (today - timedelta(days=365)).isoformat(),
          'active': True, 'version': account['version']})
    categories = first('GET', '/finance/categories')
    income = next(c for c in categories if c['kind'] == 'RECEITA')['id']
    expense = next(c for c in categories if c['kind'] == 'DESPESA')['id']

    def obligation(kind, amount, count=1, due=day, description='Obrigação HTTP isolada'):
        return {'requestId': str(uuid.uuid4()), 'kind': kind, 'amount': amount,
                'accountId': account['id'], 'categoryId': income if kind == 'RECEITA' else expense,
                'description': description, 'entryDate': day, 'dueDate': due,
                'installments': count, 'settled': False}

    def balance():
        return sum(Decimal(a['balance']) for a in first('GET', '/finance/accounts'))

    def rows(kind='DESPESA', **filters):
        query = urllib.parse.urlencode({'kind': kind, 'size': 100, **filters})
        return first('GET', '/finance/installments?' + query)['content']

    def pay(item, amount):
        return first('POST', f'/finance/entries/{item["id"]}/settle', {'requestId': str(uuid.uuid4()),
                     'version': item['version'], 'settlementDate': day, 'amount': amount})

    # The required R$1,500 -> R$500 + R$1,000 example, through the real proxy.
    payload = obligation('RECEITA', '1500.00')
    parent = first('POST', '/finance/obligations', payload, 201)
    replay = first('POST', '/finance/obligations', payload, 201)
    check(replay['id'] == parent['id'], 'Obligation creation is idempotent')
    item = parent['installments'][0]
    check(balance() == Decimal('0'), 'Pending obligations do not increase cash')
    payment = {'requestId': str(uuid.uuid4()), 'version': item['version'], 'settlementDate': day, 'amount': '500.00'}
    partial = first('POST', f'/finance/entries/{item["id"]}/settle', payment)
    check(partial['paidAmount'] == '500.00' and partial['remainingAmount'] == '1000.00' and partial['status'] == 'PARCIAL',
          'Partial receipt and exact outstanding amount')
    first('POST', f'/finance/entries/{item["id"]}/settle', payment)
    check(balance() == Decimal('500'), 'Replayed receipt does not duplicate cash')
    first('POST', f'/finance/entries/{item["id"]}/settle', {**payment, 'amount': '501.00'}, 409)
    first('POST', f'/finance/entries/{item["id"]}/settle', {'requestId': str(uuid.uuid4()),
          'version': partial['version'], 'settlementDate': day, 'amount': '1000.01'}, (400, 409))
    check(balance() == Decimal('500'), 'Excess receipt rejected atomically')
    full = pay(partial, '1000.00')
    check(full['remainingAmount'] == '0.00' and full['paidAmount'] == '1500.00' and full['status'] == 'LIQUIDADO', 'Full receipt in two parts')
    history = first('GET', f'/finance/entries/{item["id"]}/settlements')
    check(len(history) == 2 and balance() == Decimal('1500'), 'Two settlements preserved and cash correct')
    chart = first('POST', '/finance/charts/preview', {'title': 'Recebimentos parciais', 'type': 'BARRAS',
            'metrics': ['RECEITAS_RECEBIDAS'], 'grouping': 'DIA', 'period': 'ULTIMOS_7_DIAS',
            'categoryIds': [], 'accountIds': [], 'size': 'MEDIO', 'palette': 'PADRAO', 'showValues': False})
    check(sum(Decimal(p['values']['RECEITAS_RECEBIDAS']) for p in chart['points']) == Decimal('1500'), 'Charts sum actual settlements once')
    reversed_item = first('POST', f'/finance/entries/{item["id"]}/settlements/{history[0]["id"]}/reverse',
                          {'version': full['version'], 'reason': 'Correção HTTP descartável'})
    check(reversed_item['remainingAmount'] == '500.00' and balance() == Decimal('1000'), 'Individual reversal restores outstanding and cash')
    canceled = first('POST', f'/finance/entries/{item["id"]}/cancel', {'version': reversed_item['version']})
    check(canceled['remainingAmount'] == '0.00' and balance() == Decimal('0'), 'Cancel reverses remaining settlements and removes pending debt')
    check(len(first('GET', f'/finance/entries/{item["id"]}/settlements')) == 2, 'Cancellation keeps settlement history')

    # Six installments, exact cents and month-end anchoring.
    start = date(today.year + 1, 1, 31)
    installments = first('POST', '/finance/obligations', obligation('DESPESA', '1200.00', 6, start.isoformat()), 201)
    parts = installments['installments']
    check(len(parts) == 6 and all(p['amount'] == '200.00' for p in parts), 'R$1,200 in six R$200 installments')
    february = date(start.year, 2, calendar.monthrange(start.year, 2)[1]).isoformat()
    check(parts[1]['dueDate'] == february and parts[2]['dueDate'] == date(start.year, 3, 31).isoformat(), 'Month-end dates use original day')
    check(balance() == Decimal('0'), 'Installments do not reduce cash before payments')
    pay(parts[0], '200.00')
    current = first('GET', f'/finance/obligations/{installments["id"]}')
    check(current['installments'][0]['status'] == 'LIQUIDADO' and current['installments'][1]['status'] == 'PENDENTE'
          and balance() == Decimal('-200'), 'One installment payment does not pay others')
    cents = first('POST', '/finance/obligations', obligation('DESPESA', '100.00', 3), 201)
    check([p['amount'] for p in cents['installments']] == ['33.34', '33.33', '33.33'], 'Cents distributed without loss')
    overdue = first('POST', '/finance/obligations', obligation('DESPESA', '50.00', due=(today-timedelta(days=1)).isoformat()), 201)
    check(overdue['installments'][0]['id'] in [p['id'] for p in rows(status='VENCIDO')], 'Overdue filter uses real due date')

    # Bounded recurrence, edit one occurrence and preserve paid/partial futures.
    next_month = (today.replace(day=1) + timedelta(days=32)).replace(day=15)
    recurring_input = {'requestId': str(uuid.uuid4()), 'description': 'HTTP série mensal', 'amount': '90.00',
                       'categoryId': expense, 'accountId': account['id'], 'firstDueDate': next_month.isoformat(), 'occurrences': 6}
    recurring = first('POST', '/finance/recurrences', recurring_input, 201)
    recurring_replay = first('POST', '/finance/recurrences', recurring_input, 201)
    generated = sorted(rows(search='HTTP série'), key=lambda p: p['occurrenceNumber'])
    check(recurring['id'] == recurring_replay['id'] and len(generated) == 6, 'Finite recurrence generation is idempotent')
    one = generated[0]
    first('PUT', f'/finance/installments/{one["id"]}', {'version': one['version'], 'description': one['description'],
          'amount': '95.00', 'accountId': one['accountId'], 'categoryId': one['categoryId'], 'entryDate': one['entryDate'],
          'dueDate': one['dueDate'], 'notes': 'Somente esta ocorrência'})
    check(first('GET', f'/finance/installments/{generated[1]["id"]}')['amount'] == '90.00', 'One occurrence edit does not change others')
    pay(generated[3], '30.00')
    pay(generated[4], '90.00')
    before_future = balance()
    recurring = first('GET', f'/finance/recurrences/{recurring["id"]}')
    first('PUT', f'/finance/recurrences/{recurring["id"]}/future', {'version': recurring['version'],
          'fromObligationId': generated[1]['obligationId'], 'description': 'HTTP série mensal ajustada', 'amount': '100.00',
          'categoryId': expense, 'accountId': account['id'], 'dueDay': 15, 'notes': 'Somente futuras sem pagamento'})
    latest = [first('GET', f'/finance/installments/{p["id"]}') for p in generated]
    check([p['amount'] for p in latest] == ['95.00', '100.00', '100.00', '90.00', '90.00', '100.00'], 'Future edit preserves prior, partial and paid occurrences')
    check(balance() == before_future, 'Recurrence editing cannot move actual cash')
    first('POST', '/auth/logout', expected=204)
    first('POST', '/auth/login', {'email': email, 'password': password})
    check(first('GET', f'/finance/installments/{latest[3]["id"]}')['remainingAmount'] == '60.00', 'Partial obligation persists after new login')

    # Tenant boundaries, including references, all through the existing session.
    other_email = 'other-' + email
    other('POST', '/auth/register', {'name': 'Outra empresa', 'email': other_email, 'password': password,
          'companyName': 'Outra Contas HTTP', 'companyPhone': '11999999999'}, 201)
    other('POST', '/auth/login', {'email': other_email, 'password': password})
    other('GET', f'/finance/obligations/{parent["id"]}', expected=404)
    other('GET', f'/finance/installments/{latest[3]["id"]}', expected=404)
    other('GET', f'/finance/recurrences/{recurring["id"]}', expected=404)
    other('GET', f'/finance/entries/{latest[3]["id"]}/settlements', expected=404)
    other('GET', '/finance/installments?kind=DESPESA&accountId=' + str(account['id']), expected=404)
    check(other('GET', '/finance/installments?kind=DESPESA')['totalElements'] == 0, 'Tenant data and foreign reference isolation')
    check(bool(first('GET', f'/finance/history/OBRIGACAO/{parent["id"]}')), 'Obligation audit available to its company')
    print(f'Entrega 2 HTTP: {checks} verificações passaram (parciais, parcelas, recorrências, histórico e isolamento).', flush=True)
