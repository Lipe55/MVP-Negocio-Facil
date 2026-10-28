export type Metric = 'RECEITAS_RECEBIDAS' | 'DESPESAS_PAGAS' | 'RESULTADO_CAIXA' | 'SALDO_DISPONIVEL' | 'RETIRADAS_PESSOAIS';
export type ChartType = 'BARRAS' | 'LINHAS' | 'AREA' | 'PIZZA' | 'ROSCA';
export type Grouping = 'DIA' | 'SEMANA' | 'MES' | 'ANO' | 'CATEGORIA' | 'CONTA';
export type Period = 'ULTIMOS_7_DIAS' | 'ULTIMOS_30_DIAS' | 'MES_ATUAL' | 'ULTIMOS_3_MESES' | 'ULTIMOS_6_MESES' | 'ULTIMOS_12_MESES' | 'ANO_ATUAL' | 'PERSONALIZADO';
export interface ChartConfig {
  title: string;
  type: ChartType;
  metrics: Metric[];
  grouping: Grouping;
  period: Period;
  from: string | null;
  to: string | null;
  categoryIds: number[];
  accountIds: number[];
  size: 'PEQUENO' | 'MEDIO' | 'GRANDE';
  palette: 'PADRAO' | 'ACESSIVEL';
  showValues: boolean;
}
export interface ChartView extends ChartConfig {
  id: number;
  position: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface ChartPanel { charts: ChartView[]; version: number; }
export interface ChartMetric { id: string; label: string; description: string; available: boolean; reason: string | null; stock: boolean; }
export interface ChartCatalog {
  metrics: ChartMetric[];
  groupings: { id: Grouping; label: string }[];
  unavailableFilters: { id: string; label: string; reason: string }[];
  maxCharts: number;
  maxPoints: number;
}
export interface ChartData {
  from: string;
  to: string;
  grouping: Grouping;
  series: { metric: Metric; label: string }[];
  points: { key: string; label: string; values: Partial<Record<Metric, string>> }[];
  openingBalance: string | null;
  hasData: boolean;
  warnings: string[];
}
export const metricLabels: Record<Metric, string> = {
  RECEITAS_RECEBIDAS: 'Receitas recebidas', DESPESAS_PAGAS: 'Despesas pagas',
  RESULTADO_CAIXA: 'Resultado operacional de caixa', SALDO_DISPONIVEL: 'Saldo disponível', RETIRADAS_PESSOAIS: 'Retiradas pessoais',
};
export const typeLabels: Record<ChartType, string> = { BARRAS: 'Barras', LINHAS: 'Linhas', AREA: 'Área', PIZZA: 'Pizza', ROSCA: 'Rosca' };
export const groupingLabels: Record<Grouping, string> = { DIA: 'Dia', SEMANA: 'Semana', MES: 'Mês', ANO: 'Ano', CATEGORIA: 'Categoria', CONTA: 'Conta financeira' };
export const periodLabels: Record<Period, string> = {
  ULTIMOS_7_DIAS: 'Últimos 7 dias', ULTIMOS_30_DIAS: 'Últimos 30 dias', MES_ATUAL: 'Mês atual',
  ULTIMOS_3_MESES: 'Últimos 3 meses', ULTIMOS_6_MESES: 'Últimos 6 meses', ULTIMOS_12_MESES: 'Últimos 12 meses', ANO_ATUAL: 'Ano atual', PERSONALIZADO: 'Período personalizado',
};
export const initialConfig = (): ChartConfig => ({
  title: '', type: 'BARRAS', metrics: ['DESPESAS_PAGAS'], grouping: 'MES', period: 'ULTIMOS_12_MESES',
  from: null, to: null, categoryIds: [], accountIds: [], size: 'MEDIO', palette: 'PADRAO', showValues: false,
});
export function configOf(chart: ChartConfig): ChartConfig {
  return { title: chart.title, type: chart.type, metrics: [...chart.metrics], grouping: chart.grouping, period: chart.period,
    from: chart.from, to: chart.to, categoryIds: [...chart.categoryIds], accountIds: [...chart.accountIds],
    size: chart.size, palette: chart.palette, showValues: chart.showValues };
}
