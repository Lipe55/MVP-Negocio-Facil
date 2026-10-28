import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ChartVisual from './ChartVisual';
import type { ChartConfig, ChartData } from './types';

const config: ChartConfig = {
  title: 'Receitas x Despesas', type: 'BARRAS',
  metrics: ['RECEITAS_RECEBIDAS', 'DESPESAS_PAGAS'], grouping: 'MES',
  period: 'ULTIMOS_6_MESES', from: null, to: null, categoryIds: [], accountIds: [],
  size: 'MEDIO', palette: 'PADRAO', showValues: false,
};
const data: ChartData = {
  from: '2026-01-01', to: '2026-02-28', grouping: 'MES',
  series: [{ metric: 'RECEITAS_RECEBIDAS', label: 'Receitas recebidas' }, { metric: 'DESPESAS_PAGAS', label: 'Despesas pagas' }],
  points: [
    { key: '2026-01', label: 'jan. de 2026', values: { RECEITAS_RECEBIDAS: '1234.56', DESPESAS_PAGAS: '234.55' } },
    { key: '2026-02', label: 'fev. de 2026', values: { RECEITAS_RECEBIDAS: '65.45', DESPESAS_PAGAS: '12.34' } },
  ],
  openingBalance: null, hasData: true, warnings: [],
};
afterEach(() => { vi.unstubAllGlobals(); });
function resizeObserver() {
  const notifications: ((width: number) => void)[] = [];
  const disconnect = vi.fn();
  class Observer {
    constructor(callback: ResizeObserverCallback) { notifications.push(width => callback([{ contentRect: { width } } as ResizeObserverEntry], this as unknown as ResizeObserver)); }
    observe() {}
    disconnect = disconnect;
  }
  vi.stubGlobal('ResizeObserver', Observer);
  return { resize: (width: number) => act(() => notifications.forEach(notify => notify(width))), disconnect };
}

describe('ChartVisual', () => {
  it('draws grouped bars and preserves cents in legend and the accessible table', () => {
    render(<ChartVisual config={config} data={data}/>);
    expect(screen.getByRole('group', { name: /Receitas x Despesas/ })).toBeInTheDocument();
    expect(screen.getAllByRole('button')).toHaveLength(4);
    expect(screen.getByText('R$ 1.300,01')).toBeInTheDocument();
    expect(screen.getByText('R$ 246,89')).toBeInTheDocument();
    const table = screen.getByRole('table', { hidden: true });
    expect(within(table).getByText('R$ 1.234,56')).toBeInTheDocument();
    expect(within(table).getAllByRole('columnheader', { hidden: true })).toHaveLength(3);
  });

  it('exposes the real value on pointer, focus, click and keyboard selection', async () => {
    render(<ChartVisual config={config} data={data}/>);
    const first = screen.getByRole('button', { name: 'jan. de 2026 · Receitas recebidas: R$ 1.234,56' });
    fireEvent.pointerEnter(first);
    expect(screen.getByRole('status')).toHaveTextContent('Receitas recebidas: R$ 1.234,56');
    const second = screen.getByRole('button', { name: 'fev. de 2026 · Despesas pagas: R$ 12,34' });
    fireEvent.focus(second);
    expect(screen.getByRole('status')).toHaveTextContent('Despesas pagas: R$ 12,34');
    await userEvent.setup().click(first);
    expect(first).toHaveAttribute('aria-pressed', 'true');
    fireEvent.keyDown(second, { key: 'Enter' });
    expect(second).toHaveAttribute('aria-pressed', 'true');
    fireEvent.keyDown(first, { key: ' ' });
    expect(first).toHaveAttribute('aria-pressed', 'true');
  });

  it.each(['LINHAS', 'AREA'] as const)('renders %s with distinct series patterns and exact values', type => {
    const { container } = render(<ChartVisual config={{ ...config, type, showValues: true }} data={data}/>);
    expect(container.querySelectorAll('[data-series]')).toHaveLength(2);
    expect(container.querySelector('[stroke-dasharray]')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /jan. de 2026 · Receitas recebidas/ })).toBeInTheDocument();
    if (type === 'AREA') expect(container.querySelector('path[fill-opacity="0.14"]')).toBeInTheDocument();
  });

  it.each(['PIZZA', 'ROSCA'] as const)('renders %s with amounts, percent and numbered slices', async type => {
    const pieConfig: ChartConfig = { ...config, type, metrics: ['DESPESAS_PAGAS'], grouping: 'CATEGORIA' };
    const pieData: ChartData = { ...data, grouping: 'CATEGORIA', series: [data.series[1]], points: [
      { key: '1', label: 'Transporte', values: { DESPESAS_PAGAS: '30.00' } },
      { key: '2', label: 'Materiais', values: { DESPESAS_PAGAS: '70.00' } },
    ] };
    const { container } = render(<ChartVisual config={pieConfig} data={pieData}/>);
    const slice = screen.getByRole('button', { name: 'Transporte · Despesas pagas: R$ 30,00 · 30,00%' });
    await userEvent.setup().click(slice);
    expect(screen.getByRole('status')).toHaveTextContent('R$ 30,00 · 30,00%');
    expect(screen.getByRole('list', { name: 'Legenda do gráfico' })).toHaveTextContent('70,00%');
    expect(container.querySelectorAll('.chart-slice-number')).toHaveLength(2);
    if (type === 'ROSCA') expect(container.querySelector('circle[r="52"]')).toBeInTheDocument();
  });

  it('uses the final balance, rather than summing daily balances, and preserves negative amounts', () => {
    const stockConfig: ChartConfig = { ...config, type: 'LINHAS', metrics: ['SALDO_DISPONIVEL'] };
    const stockData: ChartData = { ...data, series: [{ metric: 'SALDO_DISPONIVEL', label: 'Saldo disponível' }], openingBalance: '100.00', points: [
      { key: '1', label: 'jan. de 2026', values: { SALDO_DISPONIVEL: '200.00' } },
      { key: '2', label: 'fev. de 2026', values: { SALDO_DISPONIVEL: '-10.01' } },
    ] };
    render(<ChartVisual config={stockConfig} data={stockData}/>);
    expect(screen.getByText(/Saldo de abertura/)).toHaveTextContent('R$ 100,00');
    expect(screen.getByRole('list', { name: 'Legenda do gráfico' })).toHaveTextContent('-R$ 10,01');
    expect(screen.queryByText('R$ 189,99')).not.toBeInTheDocument();
  });

  it('sums the final account balances only when grouped by account', () => {
    const stockConfig: ChartConfig = { ...config, type: 'BARRAS', metrics: ['SALDO_DISPONIVEL'], grouping: 'CONTA' };
    const stockData: ChartData = { ...data, grouping: 'CONTA', series: [{ metric: 'SALDO_DISPONIVEL', label: 'Saldo disponível' }], points: [
      { key: '1', label: 'Principal', values: { SALDO_DISPONIVEL: '200.00' } },
      { key: '2', label: 'Dinheiro', values: { SALDO_DISPONIVEL: '-10.01' } },
    ] };
    render(<ChartVisual config={stockConfig} data={stockData}/>);
    expect(screen.getByRole('list', { name: 'Legenda do gráfico' })).toHaveTextContent('R$ 189,99');
  });

  it('does not manufacture marks when the backend reports no data', () => {
    const { container } = render(<ChartVisual config={config} data={{ ...data, hasData: false }}/>);
    expect(screen.getByRole('status')).toHaveTextContent('Ainda não existem movimentações suficientes');
    expect(container.querySelector('svg')).toBeNull();
    expect(screen.queryByText('R$ 1.234,56')).not.toBeInTheDocument();
  });

  it('handles a real zero distribution without drawing fake pie slices', () => {
    const { container } = render(<ChartVisual config={{ ...config, type: 'ROSCA', metrics: ['DESPESAS_PAGAS'], grouping: 'CATEGORIA' }} data={{ ...data, grouping: 'CATEGORIA', series: [data.series[1]], points: [{ key: '1', label: 'Materiais', values: { DESPESAS_PAGAS: '0.00' } }] }}/>);
    expect(screen.getByText('Não há valores positivos para distribuir neste gráfico.')).toBeInTheDocument();
    expect(container.querySelector('svg')).toBeNull();
    expect(screen.getByRole('table', { hidden: true })).toHaveTextContent('R$ 0,00');
  });

  it('adapts the viewBox to mobile width, preserving legible units and larger touch marks', () => {
    const observer = resizeObserver();
    const { container, unmount } = render(<ChartVisual config={{ ...config, type: 'LINHAS' }} data={data}/>);
    observer.resize(360);
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 360 290');
    expect(container.querySelector('circle[role="button"]')).toHaveAttribute('r', '12');
    expect(screen.getByRole('button', { name: 'jan. de 2026 · Receitas recebidas: R$ 1.234,56' })).toHaveAttribute('cx', '167.5');
    observer.resize(0);
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 360 290');
    observer.resize(280);
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 320 290');
    observer.resize(1400);
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 720 290');
    unmount(); expect(observer.disconnect).toHaveBeenCalled();
  });

  it('centers a readable donut in a narrow card instead of shrinking a desktop diagram', () => {
    const observer = resizeObserver();
    const { container } = render(<ChartVisual config={{ ...config, type: 'ROSCA', metrics: ['DESPESAS_PAGAS'], grouping: 'CATEGORIA' }} data={{ ...data, grouping: 'CATEGORIA', series: [data.series[1]], points: [{ key: '1', label: 'Materiais', values: { DESPESAS_PAGAS: '30.00' } }] }}/>);
    observer.resize(340);
    expect(container.querySelector('svg')).toHaveAttribute('viewBox', '0 0 340 290');
    expect(container.querySelector('circle[r="52"]')).toHaveAttribute('cx', '170');
    expect(screen.getByRole('button', { name: 'Materiais · Despesas pagas: R$ 30,00 · 100,00%' })).toBeInTheDocument();
  });

  it('shortens mobile weekly tick labels while retaining full labels for tooltips and the data table', () => {
    const observer = resizeObserver();
    const weekly: ChartData = { ...data, grouping: 'SEMANA', points: [{ key: '2026-10-06', label: 'Semana de 06/10/2026', values: { RECEITAS_RECEBIDAS: '1234.56', DESPESAS_PAGAS: '234.55' } }] };
    const { container } = render(<ChartVisual config={{ ...config, grouping: 'SEMANA' }} data={weekly}/>);
    observer.resize(360);
    expect([...container.querySelectorAll('.chart-axis-label')].some(label => label.lastChild?.textContent === '06/10/2026')).toBe(true);
    expect(screen.getByRole('button', { name: 'Semana de 06/10/2026 · Receitas recebidas: R$ 1.234,56' })).toBeInTheDocument();
    expect(screen.getByRole('table', { hidden: true })).toHaveTextContent('Semana de 06/10/2026');
  });
});
