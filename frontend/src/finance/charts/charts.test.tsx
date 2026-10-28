import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api';
import { today } from '../../money';
import { Notifications } from '../../components/ui';
import type { FinanceAccount, FinanceCategory } from '../types';
import ChartBuilder from './ChartBuilder';
import Charts from './Charts';
import { configurationError } from './rules';
import { initialConfig, metricLabels, groupingLabels } from './types';
import type { ChartCatalog, ChartConfig, ChartData, ChartPanel, ChartView } from './types';

vi.mock('../../api', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }, errorMessage: (error: unknown) => error instanceof Error ? error.message : 'Falha na consulta.' }));
const accounts: FinanceAccount[] = [{ id: 1, name: 'Conta principal', type: 'BANCO', primaryAccount: true, active: true, openingBalance: '100.00', openingDate: today(), balance: '100.00', version: 0 }];
const categories: FinanceCategory[] = [{ id: 2, name: 'Transporte', kind: 'DESPESA' }, { id: 3, name: 'Materiais', kind: 'DESPESA' }, { id: 4, name: 'Serviços', kind: 'RECEITA' }];
const catalog: ChartCatalog = {
  metrics: [
    ...Object.entries(metricLabels).map(([id, label]) => ({ id, label, description: `Acompanhe ${label.toLowerCase()}.`, available: true, reason: null, stock: id === 'SALDO_DISPONIVEL' })),
    { id: 'CONTAS_VENCIDAS', label: 'Contas vencidas', description: 'Depende de vencimentos.', available: false, reason: 'Vencimentos não implementados.', stock: true },
  ], groupings: Object.entries(groupingLabels).map(([id, label]) => ({ id: id as ChartConfig['grouping'], label })),
  unavailableFilters: [{ id: 'CLIENTE', label: 'Cliente', reason: 'Não há vínculo financeiro com clientes.' }, { id: 'SERVICO', label: 'Serviço', reason: 'Não há módulo de serviços.' }], maxCharts: 24, maxPoints: 370,
};
const sampleData: ChartData = { from: today(), to: today(), grouping: 'MES', series: [{ metric: 'DESPESAS_PAGAS', label: 'Despesas pagas' }], points: [{ key: '2026-10', label: 'out/2026', values: { DESPESAS_PAGAS: '125.50' } }], hasData: true, openingBalance: null, warnings: [] };
const makeChart = (id: number, title: string): ChartView => ({ ...initialConfig(), id, title, position: id - 1, version: 0, createdAt: today(), updatedAt: today() });
let saved: ChartView[], panelVersion: number;
function setup(element: React.ReactNode) {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><MemoryRouter><Notifications>{element}</Notifications></MemoryRouter></QueryClientProvider>);
}
function builder(chart?: ChartView) { return setup(<ChartBuilder chart={chart} catalog={catalog} accounts={accounts} categories={categories} onClose={vi.fn()} />); }
function panel(): ChartPanel { return { charts: [...saved], version: panelVersion }; }
beforeEach(() => {
  vi.resetAllMocks(); saved = []; panelVersion = 0;
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function(this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function(this: HTMLDialogElement) { this.open = false; } });
  vi.mocked(api.get).mockImplementation(async url => ({ data: url === '/finance/accounts' ? accounts : url === '/finance/categories' ? categories : url === '/finance/charts/catalog' ? catalog : url === '/finance/charts' ? panel() : sampleData }));
  vi.mocked(api.post).mockImplementation(async (url, body) => {
    if (url === '/finance/charts/preview') return { data: sampleData };
    if (url === '/finance/charts/restore') { saved = [makeChart(1, 'Padrão restaurado')]; panelVersion++; return { data: panel() }; }
    if (url?.endsWith('/duplicate')) { const duplicate = { ...saved[0], id: 30, title: `${saved[0].title} (cópia)` }; saved.push(duplicate); panelVersion++; return { data: duplicate }; }
    const created = { ...(body as ChartConfig), id: 20, position: saved.length, version: 0, createdAt: today(), updatedAt: today() }; saved.push(created); panelVersion++; return { data: created };
  });
  vi.mocked(api.put).mockImplementation(async (url, body) => {
    if (url === '/finance/charts/order') { const ids = (body as { ids: number[] }).ids; saved = ids.map((id, position) => ({ ...saved.find(chart => chart.id === id)!, position })); panelVersion++; return { data: panel() }; }
    const value = body as ChartView; saved = saved.map(chart => chart.id === Number(url.split('/').pop()) ? { ...chart, ...value } : chart); return { data: value };
  });
  vi.mocked(api.delete).mockImplementation(async url => { saved = saved.filter(chart => chart.id !== Number(url.split('/').pop())); panelVersion++; return { data: {} }; });
});

describe('chart builder', () => {
  it('saves fuel expenses using an existing category and real aggregate preview', async () => {
    builder(); const user = userEvent.setup();
    await user.click(screen.getByRole('checkbox', { name: 'Transporte' }));
    await user.click(screen.getByRole('checkbox', { name: 'Conta principal' }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.objectContaining({ metrics: ['DESPESAS_PAGAS'], categoryIds: [2], accountIds: [1], grouping: 'MES', period: 'ULTIMOS_12_MESES' }), expect.objectContaining({ signal: expect.any(AbortSignal) })));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.type(screen.getByLabelText('Título do gráfico'), 'Meus gastos com combustível');
    await user.click(screen.getByRole('button', { name: 'Salvar gráfico' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts', expect.objectContaining({ title: 'Meus gastos com combustível', categoryIds: [2], accountIds: [1], type: 'BARRAS', grouping: 'MES', period: 'ULTIMOS_12_MESES' })));
  });
  it('compares received revenue and paid expenses and changes bars to lines', async () => {
    builder(); const user = userEvent.setup();
    await user.click(screen.getByRole('checkbox', { name: /^Receitas recebidas/ }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('radio', { name: 'Linhas' }));
    await user.selectOptions(screen.getByLabelText('Período'), 'ULTIMOS_6_MESES');
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.objectContaining({ metrics: ['DESPESAS_PAGAS', 'RECEITAS_RECEBIDAS'], type: 'LINHAS', period: 'ULTIMOS_6_MESES' }), expect.anything()));
    expect(screen.getByRole('radio', { name: 'Pizza' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.type(screen.getByLabelText('Título do gráfico'), 'Receitas x Despesas');
    await user.click(screen.getByRole('button', { name: 'Salvar gráfico' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts', expect.objectContaining({ title: 'Receitas x Despesas', metrics: ['DESPESAS_PAGAS', 'RECEITAS_RECEBIDAS'], type: 'LINHAS' })));
  });
  it('selects multiple categories, account and custom dates without creating categories', async () => {
    builder(); const user = userEvent.setup();
    await user.click(screen.getByRole('checkbox', { name: 'Transporte' })); await user.click(screen.getByRole('checkbox', { name: 'Materiais' }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.selectOptions(screen.getByLabelText('Período'), 'PERSONALIZADO');
    fireEvent.change(screen.getByLabelText('Data inicial'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByLabelText('Data final'), { target: { value: '2026-02-28' } });
    await user.selectOptions(screen.getByLabelText('Agrupamento'), 'SEMANA');
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.objectContaining({ categoryIds: [2, 3], from: '2026-01-01', to: '2026-02-28', grouping: 'SEMANA' }), expect.anything()));
    expect(api.post).not.toHaveBeenCalledWith('/finance/categories', expect.anything());
  });
  it('debounces rapid preview changes and fetches only the latest grouping', async () => {
    builder(); await userEvent.setup().click(screen.getByRole('button', { name: 'Continuar' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.anything(), expect.anything()));
    vi.mocked(api.post).mockClear();
    act(() => { fireEvent.change(screen.getByLabelText('Agrupamento'), { target: { value: 'DIA' } }); fireEvent.change(screen.getByLabelText('Agrupamento'), { target: { value: 'SEMANA' } }); fireEvent.change(screen.getByLabelText('Agrupamento'), { target: { value: 'ANO' } }); });
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    expect(api.post).toHaveBeenLastCalledWith('/finance/charts/preview', expect.objectContaining({ grouping: 'ANO' }), expect.anything());
  });
  it('prefills an existing configuration and updates it with its version', async () => {
    const chart = { ...makeChart(8, 'Gastos atuais'), categoryIds: [2], version: 4 }; builder(chart); const user = userEvent.setup();
    expect(screen.getByRole('checkbox', { name: 'Transporte' })).toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Continuar' })); await user.click(screen.getByRole('radio', { name: 'Linhas' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.objectContaining({ type: 'LINHAS' }), expect.anything()));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.clear(screen.getByLabelText('Título do gráfico')); await user.type(screen.getByLabelText('Título do gráfico'), 'Gastos revisados');
    await user.selectOptions(screen.getByLabelText('Tamanho no painel'), 'GRANDE');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/finance/charts/8', expect.objectContaining({ title: 'Gastos revisados', type: 'LINHAS', size: 'GRANDE', version: 4 })));
  });
  it('preserves entered information on save failure and exposes the error', async () => {
    const basePost = vi.mocked(api.post).getMockImplementation()!;
    vi.mocked(api.post).mockImplementation(async (...args) => { if (args[0] === '/finance/charts') throw new Error('Não foi possível salvar.'); return basePost(...args); });
    builder(); const user = userEvent.setup(); await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/preview', expect.anything(), expect.anything()));
    await user.click(screen.getByRole('button', { name: 'Continuar' })); await user.type(screen.getByLabelText('Título do gráfico'), 'Meu controle');
    await user.click(screen.getByRole('button', { name: 'Salvar gráfico' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Seu preenchimento foi preservado.');
    expect(screen.getByLabelText('Título do gráfico')).toHaveValue('Meu controle');
  });
  it('identifies unavailable clients, services and overdue data instead of sending unsupported filters', async () => {
    builder(); const user = userEvent.setup(); await user.click(screen.getByText('Informações ainda indisponíveis'));
    expect(screen.getByText(/Não há vínculo financeiro com clientes/)).toBeInTheDocument();
    expect(screen.getByText(/Não há módulo de serviços/)).toBeInTheDocument();
    expect(screen.getByText(/Vencimentos não implementados/)).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Cliente' })).not.toBeInTheDocument();
  });
});

describe('saved chart panel', () => {
  it('shows an empty state and preserves the exact financial tab order', async () => {
    setup(<Charts />); expect(await screen.findByText('Você ainda não criou nenhum gráfico.')).toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: 'Seções do financeiro' })).getAllByRole('link').map(link => link.textContent)).toEqual(['Movimentações', 'Gráficos', 'Contas e saldos']);
    expect(screen.getByRole('button', { name: 'Criar meu primeiro gráfico' })).toBeEnabled();
  });
  it('duplicates to a distinct saved chart and opens its editor', async () => {
    saved = [makeChart(1, 'Despesas com transporte')]; setup(<Charts />); const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Ações de Despesas com transporte' }));
    await user.click(screen.getByRole('button', { name: 'Duplicar gráfico' }));
    expect(await screen.findByRole('dialog', { name: 'Editar gráfico' })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/finance/charts/1/duplicate', { version: 0 });
    expect(saved.map(chart => chart.id)).toEqual([1, 30]);
  });
  it('deletes only after confirmation and refreshes the saved panel', async () => {
    saved = [makeChart(1, 'Controle de despesas')]; setup(<Charts />); const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Ações de Controle de despesas' })); await user.click(screen.getByRole('button', { name: 'Excluir gráfico' }));
    expect(api.delete).not.toHaveBeenCalled(); await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/finance/charts/1', { params: { version: 0 } }));
    expect(await screen.findByText('Você ainda não criou nenhum gráfico.')).toBeInTheDocument();
  });
  it('persists accessible reordering with the panel version', async () => {
    saved = [makeChart(1, 'Primeiro'), makeChart(2, 'Segundo')]; panelVersion = 7; setup(<Charts />); const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Personalizar painel' }));
    await user.click(screen.getByRole('button', { name: 'Mover Segundo para cima' }));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/finance/charts/order', { ids: [2, 1], version: 7 }));
    await waitFor(() => expect(screen.getAllByRole('article').map(card => card.getAttribute('aria-label'))).toEqual(['Segundo', 'Primeiro']));
  });
  it('confirms restoration and preserves financial movements', async () => {
    setup(<Charts />); const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Restaurar gráficos padrão' }));
    expect(screen.getByText(/Suas movimentações financeiras não serão alteradas/)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled(); await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/finance/charts/restore', { version: 0 }));
    expect(await screen.findByRole('article', { name: 'Padrão restaurado' })).toBeInTheDocument();
  });
  it('reloads saved configuration from the backend after a new mount', async () => {
    saved = [{ ...makeChart(1, 'Painel persistido'), type: 'LINHAS', categoryIds: [2], accountIds: [1], size: 'GRANDE' }];
    const first = setup(<Charts />); expect(await screen.findByRole('article', { name: 'Painel persistido' })).toHaveClass('chart-size-grande'); first.unmount();
    setup(<Charts />); const card = await screen.findByRole('article', { name: 'Painel persistido' });
    expect(within(card).getByText(/Linhas · Por mês/)).toBeInTheDocument(); expect(within(card).getByText('Transporte')).toBeInTheDocument();
    expect(vi.mocked(api.get).mock.calls.filter(([url]) => url === '/finance/charts')).toHaveLength(2);
  });
  it('updates values using the refresh action and shows query errors without invented data', async () => {
    saved = [makeChart(1, 'Dados atuais')]; setup(<Charts />); const user = userEvent.setup();
    await screen.findByRole('article', { name: 'Dados atuais' });
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/finance/charts/1/data', expect.anything()));
    const baseGet = vi.mocked(api.get).getMockImplementation()!;
    vi.mocked(api.get).mockImplementation(async (...args) => { if (args[0] === '/finance/charts/1/data') throw new Error('Consulta indisponível.'); return baseGet(...args); });
    await user.click(screen.getByRole('button', { name: 'Atualizar dados dos gráficos' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Consulta indisponível.');
  });
});

it('rejects incompatible financial combinations and excessive intervals', () => {
  const config = initialConfig();
  expect(configurationError({ ...config, metrics: ['SALDO_DISPONIVEL', 'DESPESAS_PAGAS'] })).toMatch(/sozinho/);
  expect(configurationError({ ...config, type: 'ROSCA', metrics: ['RESULTADO_CAIXA'], grouping: 'CONTA' })).toMatch(/única métrica/);
  expect(configurationError({ ...config, type: 'PIZZA', grouping: 'MES' })).toMatch(/categoria ou conta/);
  expect(configurationError({ ...config, metrics: ['RETIRADAS_PESSOAIS'], categoryIds: [2] })).toMatch(/categoria/);
  expect(configurationError({ ...config, grouping: 'CATEGORIA', metrics: ['DESPESAS_PAGAS', 'RECEITAS_RECEBIDAS'] })).toMatch(/somente/);
  expect(configurationError({ ...config, period: 'PERSONALIZADO', from: '2025-01-01', to: '2026-06-01', grouping: 'DIA' })).toMatch(/370 dias/);
  expect(configurationError({ ...config, period: 'PERSONALIZADO', from: '2020-01-01', to: '2026-01-01' })).toMatch(/cinco anos/);
  expect(configurationError({ ...config, title: 'Transporte', categoryIds: [2], grouping: 'MES' }, true)).toBeNull();
});
