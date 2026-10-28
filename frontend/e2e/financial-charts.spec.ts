import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

// This suite creates financial records. Only our disposable-app runner opts in.
test.skip(process.env.FINANCE_CHARTS_ISOLATED !== '1',
  'Execute scripts/test-charts-e2e-isolated.py; never use an existing database.');

async function api(page: Page, method: string, path: string, data?: unknown) {
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    const csrf = await page.request.get('/api/auth/csrf');
    headers['X-XSRF-TOKEN'] = (await csrf.json()).token;
  }
  const response = await page.request.fetch('/api' + path, { method, data, headers });
  expect(response.ok(), `${method} ${path}: HTTP ${response.status()}`).toBeTruthy();
  return response.status() === 204 ? null : response.json();
}

function total(data: { points: { values: Record<string, string> }[] }, metric: string) {
  return data.points.reduce((sum, point) => sum + BigInt(point.values[metric].replace('.', '')), 0n);
}

test('custom charts use real cash, persist, refresh, isolate and work on mobile', async ({ page, browser }, testInfo) => {
  test.setTimeout(240_000);
  const email = `charts-${Date.now()}@example.com`;
  const password = 'IsolatedChartsTest123!';
  const runtimeErrors: string[] = [];
  page.on('pageerror', error => runtimeErrors.push(error.message));
  await page.goto('/cadastro');
  await page.getByLabel('Nome do responsável').fill('Ana Gráficos');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByLabel('Nome da empresa').fill('Empresa de gráficos E2E');
  await page.getByLabel('Telefone da empresa').fill('11999999999');
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  await expect(page).toHaveURL(/dashboard/);

  const accounts = await api(page, 'GET', '/finance/accounts');
  const categories = await api(page, 'GET', '/finance/categories');
  const main = accounts[0];
  const transport = categories.find((c: { name: string }) => c.name === 'Transporte');
  const materials = categories.find((c: { name: string }) => c.name === 'Materiais');
  const revenue = categories.find((c: { kind: string }) => c.kind === 'RECEITA');
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
  await api(page, 'PUT', `/finance/accounts/${main.id}`, {
    name: main.name, type: main.type, openingBalance: '500.00', openingDate: today,
    active: true, version: main.version,
  });
  const cash = await api(page, 'POST', '/finance/accounts', {
    name: 'Dinheiro E2E', type: 'DINHEIRO', openingBalance: '0.00', openingDate: today, active: true,
  });
  async function entry(kind: string, amount: string, categoryId: number | null, settled = true) {
    return api(page, 'POST', '/finance/entries', {
      requestId: randomUUID(), kind, amount, categoryId, accountId: main.id,
      description: `${kind} para validar gráficos`, entryDate: today,
      settled, settlementDate: settled ? today : null,
    });
  }
  await entry('RECEITA', '1000.00', revenue.id);
  await entry('DESPESA', '120.00', transport.id);
  await entry('DESPESA', '80.00', materials.id);
  await entry('DESPESA', '400.00', transport.id, false);
  await entry('RETIRADA', '50.00', null);
  await api(page, 'POST', '/finance/transfers', {
    requestId: randomUUID(), sourceAccountId: main.id, targetAccountId: cash.id,
    amount: '100.00', transferDate: today, description: 'Transferência interna de teste',
  });

  await page.goto('/financeiro/graficos');
  await expect(page.getByRole('heading', { name: 'Meus gráficos', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Seções do financeiro' }).getByRole('link'))
    .toHaveText(['Movimentações', 'Gráficos', 'Contas e saldos']);
  await expect(page.getByRole('article')).toHaveCount(5);
  await page.getByRole('button', { name: 'Adicionar gráfico', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Adicionar gráfico', exact: true });
  const incomeCheck = dialog.getByRole('checkbox', { name: /^Receitas recebidas/ });
  if (await incomeCheck.isChecked()) await incomeCheck.uncheck();
  await dialog.getByRole('checkbox', { name: /^Despesas pagas/ }).check();
  await dialog.getByRole('checkbox', { name: 'Transporte', exact: true }).check();
  await dialog.getByRole('checkbox', { name: 'Conta principal', exact: true }).check();
  await dialog.getByRole('button', { name: 'Continuar', exact: true }).click();
  await dialog.getByRole('radio', { name: 'Barras', exact: true }).check();
  await dialog.getByLabel('Agrupamento', { exact: true }).selectOption('MES');
  await dialog.getByLabel('Período', { exact: true }).selectOption('ULTIMOS_12_MESES');
  await expect(dialog.locator('svg.chart-svg')).toBeVisible();
  await dialog.getByRole('button', { name: 'Continuar', exact: true }).click();
  await dialog.getByLabel('Título do gráfico', { exact: true }).fill('Meus gastos com combustível');
  await dialog.getByRole('button', { name: 'Salvar gráfico', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const fuelCard = page.getByRole('article', { name: 'Meus gastos com combustível', exact: true });
  await expect(fuelCard).toBeVisible();
  await fuelCard.getByRole('button', { name: /Despesas pagas: R\$ 120,00/ }).click();
  await expect(fuelCard.locator('.chart-tooltip')).toContainText('R$ 120,00');
  let panel = await api(page, 'GET', '/finance/charts');
  let fuel = panel.charts.find((c: { title: string }) => c.title === 'Meus gastos com combustível');
  expect(total(await api(page, 'GET', `/finance/charts/${fuel.id}/data`), 'DESPESAS_PAGAS')).toBe(12000n);
  expect(fuel.categoryIds).toEqual([transport.id]);
  expect(fuel.accountIds).toEqual([main.id]);

  // Create the required multi-metric comparison through the visual builder.
  await page.getByRole('button', { name: 'Adicionar gráfico', exact: true }).click();
  await dialog.getByRole('checkbox', { name: /^Receitas recebidas/ }).check();
  await dialog.getByRole('checkbox', { name: /^Despesas pagas/ }).check();
  await dialog.getByRole('button', { name: 'Continuar', exact: true }).click();
  await dialog.getByRole('radio', { name: 'Barras', exact: true }).check();
  await dialog.getByLabel('Agrupamento', { exact: true }).selectOption('MES');
  await dialog.getByLabel('Período', { exact: true }).selectOption('ULTIMOS_6_MESES');
  await dialog.getByRole('button', { name: 'Continuar', exact: true }).click();
  await dialog.getByLabel('Título do gráfico', { exact: true }).fill('Receitas x Despesas');
  await dialog.getByRole('button', { name: 'Salvar gráfico', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  panel = await api(page, 'GET', '/finance/charts');
  const comparison = panel.charts.find((c: { title: string }) => c.title === 'Receitas x Despesas');
  const compared = await api(page, 'GET', `/finance/charts/${comparison.id}/data`);
  expect(total(compared, 'RECEITAS_RECEBIDAS')).toBe(100000n);
  expect(total(compared, 'DESPESAS_PAGAS')).toBe(20000n);
  const stock = panel.charts.find((c: { title: string }) => c.title === 'Evolução do saldo');
  const stockData = await api(page, 'GET', `/finance/charts/${stock.id}/data`);
  expect(stockData.points.at(-1).values.SALDO_DISPONIVEL).toBe('1250.00');

  // Edit, select multiple categories, change grouping/period/type and save.
  await fuelCard.getByRole('button', { name: 'Ações de Meus gastos com combustível', exact: true }).click();
  await fuelCard.getByRole('button', { name: 'Editar gráfico', exact: true }).click();
  const editor = page.getByRole('dialog', { name: 'Editar gráfico', exact: true });
  await editor.getByRole('checkbox', { name: 'Materiais', exact: true }).check();
  await editor.getByRole('button', { name: 'Continuar', exact: true }).click();
  await editor.getByRole('radio', { name: 'Linhas', exact: true }).check();
  await editor.getByLabel('Agrupamento', { exact: true }).selectOption('SEMANA');
  await editor.getByLabel('Período', { exact: true }).selectOption('ULTIMOS_30_DIAS');
  await editor.getByRole('button', { name: 'Continuar', exact: true }).click();
  await editor.getByLabel('Título do gráfico', { exact: true }).fill('Transporte e materiais');
  await editor.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
  await expect(editor).not.toBeVisible();
  const editedCard = page.getByRole('article', { name: 'Transporte e materiais', exact: true });
  await expect(editedCard).toBeVisible();
  panel = await api(page, 'GET', '/finance/charts');
  fuel = panel.charts.find((c: { id: number }) => c.id === fuel.id);
  expect(fuel.type).toBe('LINHAS');
  expect(fuel.grouping).toBe('SEMANA');
  expect(new Set(fuel.categoryIds)).toEqual(new Set([transport.id, materials.id]));
  expect(total(await api(page, 'GET', `/finance/charts/${fuel.id}/data`), 'DESPESAS_PAGAS')).toBe(20000n);

  await editedCard.getByRole('button', { name: 'Ações de Transporte e materiais', exact: true }).click();
  await editedCard.getByRole('button', { name: 'Duplicar gráfico', exact: true }).click();
  const copyCard = page.getByRole('article', { name: 'Transporte e materiais (cópia)', exact: true });
  await expect(copyCard).toBeVisible();
  await expect(editor).toBeVisible();
  await editor.getByRole('button', { name: 'Fechar', exact: true }).click();
  panel = await api(page, 'GET', '/finance/charts');
  const copy = panel.charts.find((c: { title: string }) => c.title === 'Transporte e materiais (cópia)');
  expect(copy.id).not.toBe(fuel.id);
  expect(copy.categoryIds).toEqual(fuel.categoryIds);

  await page.getByRole('button', { name: 'Personalizar painel', exact: true }).click();
  // Accessible ordering control is independent of pointer drag-and-drop.
  await copyCard.getByRole('button', { name: /Mover.*(cima|antes)|Subir/i }).click();
  await expect.poll(async () => {
    const ordered = await api(page, 'GET', '/finance/charts');
    return ordered.charts.at(-2).id;
  }).toBe(copy.id);
  await page.reload();
  await expect(page.getByRole('article').nth(6)).toHaveAttribute('aria-label', 'Transporte e materiais (cópia)');

  // A new cash event changes the previously saved chart, without storing values.
  await page.goto('/financeiro/movimentacoes');
  await expect(page.getByText('DESPESA para validar gráficos', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Nova despesa', exact: true }).click();
  const expenseForm = page.getByRole('dialog', { name: 'Nova despesa operacional', exact: true });
  await expenseForm.getByLabel('Valor (R$)', { exact: true }).fill('30,00');
  await expenseForm.getByLabel('Descrição', { exact: true }).fill('Nova despesa para atualizar gráfico');
  await expenseForm.getByLabel('Categoria', { exact: true }).selectOption(String(transport.id));
  await expenseForm.getByRole('button', { name: 'Registrar lançamento', exact: true }).click();
  await expect(expenseForm).not.toBeVisible();
  await page.getByRole('link', { name: 'Gráficos', exact: true }).click();
  expect(total(await api(page, 'GET', `/finance/charts/${fuel.id}/data`), 'DESPESAS_PAGAS')).toBe(23000n);
  await expect(editedCard.locator('.chart-legend')).toContainText('R$ 230,00');

  // Re-login restores configuration from backend, not just the page cache.
  await page.getByText('Ana Gráficos', { exact: true }).click();
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL(/dashboard/);
  await page.goto('/financeiro/graficos');
  await expect(editedCard).toBeVisible();

  const otherContext = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const other = await otherContext.newPage();
    await other.goto('/cadastro');
    await other.getByLabel('Nome do responsável').fill('Outro usuário');
    await other.getByLabel('E-mail', { exact: true }).fill('other-' + email);
    await other.getByLabel('Senha', { exact: true }).fill(password);
    await other.getByLabel('Nome da empresa').fill('Outra empresa de teste');
    await other.getByLabel('Telefone da empresa').fill('11999999999');
    await other.getByRole('button', { name: 'Criar conta', exact: true }).click();
    await expect(other).toHaveURL(/dashboard/);
    await other.goto('/financeiro/graficos');
    await expect(other.getByRole('article')).toHaveCount(5);
    await expect(other.getByRole('article', { name: 'Transporte e materiais', exact: true })).toHaveCount(0);
    expect((await other.request.get(`/api/finance/charts/${fuel.id}/data`)).status()).toBe(404);
  } finally {
    await otherContext.close();
  }

  await page.screenshot({ path: testInfo.outputPath('charts-desktop.png'), fullPage: true });
  await page.getByRole('button', { name: 'Modo escuro', exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(editedCard).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('charts-mobile-dark.png'), fullPage: true });
  await page.getByRole('button', { name: 'Adicionar gráfico', exact: true }).click();
  await expect(dialog).toBeVisible();
  expect(await dialog.evaluate(el => el.getBoundingClientRect().width <= window.innerWidth)).toBe(true);
  await dialog.getByRole('button', { name: 'Fechar', exact: true }).click();

  await editedCard.getByRole('button', { name: 'Ações de Transporte e materiais', exact: true }).click();
  await editedCard.getByRole('button', { name: 'Excluir gráfico', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(editedCard).toHaveCount(0);
  const beforeRestore = await api(page, 'GET', '/finance/accounts');
  await page.getByRole('button', { name: 'Restaurar gráficos padrão', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(page.getByRole('article')).toHaveCount(5);
  const afterRestore = await api(page, 'GET', '/finance/accounts');
  expect(afterRestore.map((a: { balance: string }) => a.balance)).toEqual(beforeRestore.map((a: { balance: string }) => a.balance));
  expect(runtimeErrors).toEqual([]);
});
