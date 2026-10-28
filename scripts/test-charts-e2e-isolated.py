#!/usr/bin/env python3
"""Exercise charts in a fresh disposable app, without .env or existing services.

Requires prebuilt backend JAR/frontend, Docker images already present, and the
project's installed Playwright/Chromium. Does not download tools/dependencies.
"""
import argparse
from datetime import datetime
from decimal import Decimal
import http.cookiejar
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', required=True, type=Path)
parser.add_argument('--frontend', required=True, type=Path)
parser.add_argument('--http-only', action='store_true', help='Validate HTTP/session/API without launching Chromium.')
args = parser.parse_args()
jar = args.jar.resolve(strict=True)
frontend = args.frontend.resolve(strict=True)
if jar.suffix != '.jar' or not (frontend / 'index.html').is_file() or not (frontend / 'assets').is_dir():
    parser.error('Provide a built JAR and compiled frontend containing index.html and assets/.')
root = Path(__file__).resolve().parents[1]
work = Path(tempfile.mkdtemp(prefix='negocio-charts-e2e-'))
suffix = uuid.uuid4().hex[:12]
network = 'nf-charts-e2e-net-' + suffix
database = 'nf-charts-e2e-db-' + suffix
backend = 'nf-charts-e2e-api-' + suffix
web = 'nf-charts-e2e-web-' + suffix
owned = []
network_created = False


def docker(*parts):
    return subprocess.run(['docker', *parts], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def verify_http(origin):
    """Real proxy/API checks; called only after creating this run's empty DB."""
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
            payload = None if data is None else json.dumps(data).encode()
            req = urllib.request.Request(origin + '/api' + path, data=payload, method=method, headers=headers)
            try:
                response = opener.open(req, timeout=20)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                if response.status != expected:
                    raise AssertionError(f'{method} {path}: HTTP {response.status}, expected {expected}')
                raw = response.read()
                return json.loads(raw) if raw else None

        return request

    first, other = client(), client()
    password = 'DisposableCharts123!'
    email = f'http-{uuid.uuid4().hex}@example.com'
    first('POST', '/auth/register', {'name': 'Validação HTTP', 'email': email, 'password': password,
          'companyName': 'Empresa descartável HTTP', 'companyPhone': '11999999999'}, 201)
    first('POST', '/auth/login', {'email': email, 'password': password})
    check(first('GET', '/auth/me')['email'] == email, 'Session through proxy')
    with urllib.request.urlopen(origin + '/financeiro/graficos', timeout=20) as response:
        html = response.read().decode()
        check(response.status == 200 and '/assets/' in html, 'SPA route and compiled frontend')
    panel = first('GET', '/finance/charts')
    check(len(panel['charts']) == 5, 'Five supported defaults')
    catalog = first('GET', '/finance/charts/catalog')
    check(sum(metric['available'] for metric in catalog['metrics']) == 5, 'Supported metric catalog')
    account = first('GET', '/finance/accounts')[0]
    categories = first('GET', '/finance/categories')
    transport = next(c for c in categories if c['name'] == 'Transporte')['id']
    income = next(c for c in categories if c['kind'] == 'RECEITA')['id']
    today = datetime.now(ZoneInfo('America/Sao_Paulo')).date().isoformat()
    first('PUT', f'/finance/accounts/{account["id"]}', {'name': account['name'], 'type': account['type'],
          'openingBalance': '250.00', 'openingDate': today, 'active': True, 'version': account['version']})
    cash = first('POST', '/finance/accounts', {'name': 'Dinheiro HTTP', 'type': 'DINHEIRO',
                 'openingBalance': '0.00', 'openingDate': today, 'active': True}, 201)

    def entry(kind, amount, category_id, paid=True):
        return first('POST', '/finance/entries', {'requestId': str(uuid.uuid4()), 'kind': kind,
                     'amount': amount, 'categoryId': category_id, 'accountId': account['id'],
                     'description': 'Validação HTTP isolada', 'entryDate': today,
                     'settled': paid, 'settlementDate': today if paid else None}, 201)

    entry('RECEITA', '1000.00', income)
    entry('DESPESA', '120.00', transport)
    entry('DESPESA', '800.00', transport, False)
    entry('RETIRADA', '50.00', None)
    first('POST', '/finance/transfers', {'requestId': str(uuid.uuid4()), 'sourceAccountId': account['id'],
          'targetAccountId': cash['id'], 'amount': '100.00', 'transferDate': today}, 201)
    balances = first('GET', '/finance/accounts')
    check(sum(Decimal(a['balance']) for a in balances) == Decimal('1080'), 'Real cash balance')
    config = {'title': 'Meus gastos com combustível', 'type': 'BARRAS', 'metrics': ['DESPESAS_PAGAS'],
              'grouping': 'MES', 'period': 'ULTIMOS_12_MESES', 'from': None, 'to': None,
              'categoryIds': [transport], 'accountIds': [account['id']], 'size': 'MEDIO',
              'palette': 'PADRAO', 'showValues': True}

    def total(data, metric):
        return sum(Decimal(p['values'][metric]) for p in data['points'])

    preview = first('POST', '/finance/charts/preview', config)
    check(total(preview, 'DESPESAS_PAGAS') == Decimal('120'), 'Fuel category excludes pending')
    saved = first('POST', '/finance/charts', config, 201)
    check(first('GET', f'/finance/charts/{saved["id"]}')['categoryIds'] == [transport], 'Save filters')
    comparison = {**config, 'title': 'Receitas x Despesas', 'metrics': ['RECEITAS_RECEBIDAS', 'DESPESAS_PAGAS'],
                  'period': 'ULTIMOS_6_MESES', 'categoryIds': [], 'accountIds': []}
    compared = first('POST', '/finance/charts/preview', comparison)
    check(total(compared, 'RECEITAS_RECEBIDAS') == Decimal('1000') and total(compared, 'DESPESAS_PAGAS') == Decimal('120'),
          'Comparison excludes transfers and withdrawals')
    balance_config = {**comparison, 'metrics': ['SALDO_DISPONIVEL'], 'type': 'LINHAS'}
    stock = first('POST', '/finance/charts/preview', balance_config)
    check(stock['points'][-1]['values']['SALDO_DISPONIVEL'] == '1080.00', 'Stock closing with opening and transfers')
    copied = first('POST', f'/finance/charts/{saved["id"]}/duplicate', {'version': saved['version']}, 201)
    check(copied['id'] != saved['id'] and copied['categoryIds'] == saved['categoryIds'], 'Independent copy')
    edited = first('PUT', f'/finance/charts/{copied["id"]}', {**config, 'title': 'Cópia em linhas',
                   'type': 'LINHAS', 'version': copied['version']})
    check(edited['type'] == 'LINHAS' and first('GET', f'/finance/charts/{saved["id"]}')['type'] == 'BARRAS', 'Independent edit')
    entry('DESPESA', '40.00', transport)
    check(total(first('GET', f'/finance/charts/{saved["id"]}/data'), 'DESPESAS_PAGAS') == Decimal('160'), 'Live recomputation')
    panel = first('GET', '/finance/charts')
    ids = [c['id'] for c in reversed(panel['charts'])]
    first('PUT', '/finance/charts/order', {'ids': ids, 'version': panel['version']})
    check([c['id'] for c in first('GET', '/finance/charts')['charts']] == ids, 'Persisted order')
    first('POST', '/auth/logout', expected=204)
    first('POST', '/auth/login', {'email': email, 'password': password})
    check(first('GET', f'/finance/charts/{saved["id"]}')['title'] == config['title'], 'Persisted after new login')
    second_email = 'other-' + email
    other('POST', '/auth/register', {'name': 'Outro usuário', 'email': second_email, 'password': password,
          'companyName': 'Outra empresa HTTP', 'companyPhone': '11999999999'}, 201)
    other('POST', '/auth/login', {'email': second_email, 'password': password})
    other('GET', f'/finance/charts/{saved["id"]}/data', expected=404)
    other('POST', '/finance/charts/preview', config, 404)
    check(len(other('GET', '/finance/charts')['charts']) == 5, 'Tenant isolation and foreign filters')
    first('POST', '/finance/charts/preview', {**config, 'customerIds': [1]}, 400)
    check(True, 'Unavailable filter rejected')
    copied = first('GET', f'/finance/charts/{copied["id"]}')
    first('DELETE', f'/finance/charts/{copied["id"]}?version={copied["version"]}', expected=204)
    check(len(first('GET', '/finance/charts')['charts']) == 6, 'Delete configuration')
    balances = first('GET', '/finance/accounts')
    panel = first('GET', '/finance/charts')
    first('POST', '/finance/charts/restore', {'version': panel['version']})
    check(len(first('GET', '/finance/charts')['charts']) == 5, 'Restore defaults')
    check(first('GET', '/finance/accounts') == balances, 'Restore preserves financial accounts and cash')
    for _ in range(5):
        chart = first('GET', '/finance/charts')['charts'][0]
        first('DELETE', f'/finance/charts/{chart["id"]}?version={chart["version"]}', expected=204)
    check(first('GET', '/finance/charts')['charts'] == [], 'Empty panel stays empty')
    print(f'Smoke HTTP: {checks} verificações passaram (proxy, sessão, cálculos, preferências e isolamento).', flush=True)


try:
    for image in ['postgres:17-alpine', 'negocio-facil-backend:latest',
                  'negocio-facil-frontend:latest']:
        # Fail instead of implicitly pulling/installing anything.
        docker('image', 'inspect', '--format', '{{.Id}}', image)
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    origin = f'http://127.0.0.1:{port}'
    docker('network', 'create', network)
    network_created = True
    owned.append(database)
    docker('run', '-d', '--name', database, '--network', network,
           '--network-alias', 'finance-db', '--tmpfs', '/var/lib/postgresql/data',
           '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
           '-e', 'POSTGRES_DB=negocio_charts_e2e_test', 'postgres:17-alpine')
    for attempt in range(60):
        ready = subprocess.run(['docker', 'exec', database, 'pg_isready', '-U', 'postgres',
                                '-d', 'negocio_charts_e2e_test'], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(0.5)
    else:
        raise RuntimeError('Disposable PostgreSQL did not become ready.')
    owned.append(backend)
    docker('run', '-d', '--name', backend, '--network', network,
           '--network-alias', 'backend', '--entrypoint', 'java',
           '--mount', f'type=bind,src={jar},dst=/validation/app.jar,readonly',
           '-e', 'DATABASE_URL=jdbc:postgresql://finance-db:5432/negocio_charts_e2e_test',
           '-e', 'DATABASE_USER=postgres', '-e', 'DATABASE_PASSWORD=unused',
           '-e', 'APP_ORIGIN=' + origin, '-e', 'COOKIE_SECURE=false',
           '-e', 'SPRING_PROFILES_ACTIVE=default',
           'negocio-facil-backend:latest', '-jar', '/validation/app.jar')
    owned.append(web)
    docker('run', '-d', '--name', web, '--network', network,
           '-p', f'127.0.0.1:{port}:8080',
           '--mount', f'type=bind,src={frontend},dst=/usr/share/nginx/html,readonly',
           '--mount', f'type=bind,src={root / "frontend/nginx.conf"},dst=/etc/nginx/conf.d/default.conf,readonly',
           '--mount', f'type=bind,src={root / "frontend/trusted-proxies.conf"},dst=/etc/nginx/trusted-proxies.conf,readonly',
           'negocio-facil-frontend:latest')
    print('Aplicação E2E descartável: banco novo em tmpfs; acesso somente por loopback.', flush=True)
    for attempt in range(240):
        try:
            with urllib.request.urlopen(origin + '/api/health', timeout=2) as response:
                if response.status == 200:
                    break
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(0.5)
    else:
        raise RuntimeError('Disposable application did not become healthy.')
    verify_http(origin)
    if args.http_only:
        print('Navegador não solicitado nesta execução; somente HTTP real.', flush=True)
        print('Artefatos HTTP: ' + str(work), flush=True)
        raise SystemExit(0)
    result = subprocess.run(
        ['node', 'node_modules/@playwright/test/cli.js', 'test', 'financial-charts.spec.ts',
         '--workers=1', '--output', str(work / 'test-results')],
        cwd=root / 'frontend',
        env={**os.environ, 'E2E_BASE_URL': origin, 'FINANCE_CHARTS_ISOLATED': '1'})
    print('Artefatos E2E: ' + str(work), flush=True)
    raise SystemExit(result.returncode)
finally:
    for name in reversed(owned):
        logs = subprocess.run(['docker', 'logs', name], capture_output=True, text=True)
        (work / (name + '.log')).write_text(logs.stdout + logs.stderr)
        subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
    if network_created:
        subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
