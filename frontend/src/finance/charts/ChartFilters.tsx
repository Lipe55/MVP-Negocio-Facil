import { date } from '../../money';
import type { FinanceAccount, FinanceCategory } from '../types';
import { periodLabels, type ChartConfig } from './types';

export function PeriodLabel({ config }: { config: ChartConfig }) {
  return <>{config.period === 'PERSONALIZADO' && config.from && config.to ? `${date(config.from)} a ${date(config.to)}` : periodLabels[config.period]}</>;
}
export default function ChartFilters({ config, accounts, categories }: { config: ChartConfig; accounts: FinanceAccount[]; categories: FinanceCategory[] }) {
  const names = [
    ...(config.accountIds.length ? config.accountIds.map(id => accounts.find(item => item.id === id)?.name ?? `Conta #${id}`) : ['Todas as contas']),
    ...(config.categoryIds.length ? config.categoryIds.map(id => categories.find(item => item.id === id)?.name ?? `Categoria #${id}`) : []),
  ];
  return <div className="chart-filters" aria-label="Filtros ativos">{names.map((name, index) => <span key={`${index}-${name}`}>{name}</span>)}<span>{config.metrics.includes('SALDO_DISPONIVEL') ? 'Saldo efetivo' : 'Somente liquidados'}</span></div>;
}
