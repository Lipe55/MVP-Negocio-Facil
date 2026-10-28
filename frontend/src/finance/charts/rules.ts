import { today } from '../../money';
import type { ChartConfig, Metric } from './types';

export function allowsCategories(metrics: Metric[]) {
  return metrics.length > 0 && metrics.every(metric => metric === 'RECEITAS_RECEBIDAS' || metric === 'DESPESAS_PAGAS');
}
export function configurationError(config: ChartConfig, requireTitle = false): string | null {
  if (!config.metrics.length) return 'Escolha pelo menos uma informação para acompanhar.';
  if (config.metrics.includes('SALDO_DISPONIVEL') && config.metrics.length > 1) return 'Analise o saldo disponível sozinho: ele representa o dinheiro existente, enquanto as outras métricas representam movimentos no período.';
  const stock = config.metrics.includes('SALDO_DISPONIVEL');
  if (stock && config.grouping === 'CATEGORIA') return 'O saldo pode ser organizado por data ou conta, sem divisão por categoria.';
  if (config.categoryIds.length && !allowsCategories(config.metrics)) return 'Filtros de categoria estão disponíveis para receitas recebidas e despesas pagas.';
  if (config.grouping === 'CATEGORIA' && (config.metrics.length !== 1 || !allowsCategories(config.metrics))) return 'Para organizar por categoria, escolha somente receitas recebidas ou somente despesas pagas.';
  if (config.type === 'PIZZA' || config.type === 'ROSCA') {
    if (config.metrics.length !== 1 || stock || config.metrics.includes('RESULTADO_CAIXA')) return 'Pizza e rosca mostram a composição de uma única métrica de entradas, despesas ou retiradas.';
    if (!['CATEGORIA', 'CONTA'].includes(config.grouping)) return 'Para pizza ou rosca, organize os valores por categoria ou conta financeira.';
  }
  if (config.period === 'PERSONALIZADO') {
    if (!config.from || !config.to) return 'Informe o início e o fim do período.';
    if (config.from > config.to) return 'O início do período deve ser anterior ou igual ao fim.';
    if (config.to > today()) return 'Escolha um período até hoje; os gráficos representam movimentos já realizados.';
    const days = (Date.parse(config.to) - Date.parse(config.from)) / 86400000 + 1;
    if (days > 1830) return 'Escolha um período de até cinco anos.';
    if (config.grouping === 'DIA' && days > 370) return 'O agrupamento diário aceita até 370 dias. Escolha mês, semana ou um período menor.';
  }
  if (requireTitle && (!config.title.trim() || config.title.trim().length > 120)) return 'Dê ao gráfico um nome de até 120 caracteres.';
  return null;
}
