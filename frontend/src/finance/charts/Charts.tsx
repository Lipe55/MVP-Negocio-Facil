import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, BarChart3, Copy, MoreHorizontal, Pencil, Plus, RefreshCw, RotateCcw, Settings2, Trash2 } from 'lucide-react';
import { api, errorMessage } from '../../api';
import { Confirm, Empty, ErrorState, Loading, PageHeader, useNotify } from '../../components/ui';
import { FinanceTabs, useFinanceData } from '../shared';
import type { FinanceAccount, FinanceCategory } from '../types';
import ChartBuilder from './ChartBuilder';
import ChartFilters, { PeriodLabel } from './ChartFilters';
import ChartVisual from './ChartVisual';
import { groupingLabels, metricLabels, typeLabels } from './types';
import type { ChartCatalog, ChartData, ChartPanel, ChartView } from './types';
import './charts.css';

function ChartCard({ chart, accounts, categories, personalizing, index, total, busy, onEdit, onDuplicate, onDelete, onMove }: {
  chart: ChartView; accounts: FinanceAccount[]; categories: FinanceCategory[]; personalizing: boolean; index: number; total: number; busy: boolean;
  onEdit: () => void; onDuplicate: () => void; onDelete: () => void; onMove: (direction: number) => void;
}) {
  const [menu, setMenu] = useState(false), menuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menu) return;
    const close = (event: PointerEvent) => { if (!menuRef.current?.contains(event.target as Node)) setMenu(false); };
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [menu]);
  const query = useQuery({
    queryKey: ['finance', 'charts', 'data', chart.id, chart.version],
    queryFn: async ({ signal }) => (await api.get<ChartData>(`/finance/charts/${chart.id}/data`, { signal })).data,
    staleTime: 10000, refetchInterval: 60000,
  });
  return <article className={`panel chart-card chart-size-${chart.size.toLowerCase()}`} aria-label={chart.title}>
    <header className="chart-card-heading"><div><h2>{chart.title}</h2><p>{typeLabels[chart.type]} · Por {groupingLabels[chart.grouping].toLowerCase()} · <PeriodLabel config={chart} /></p></div>
      <div className="chart-card-menu" ref={menuRef} onKeyDown={event => { if (event.key === 'Escape') setMenu(false); }}>
        <button className="icon-btn" aria-label={`Ações de ${chart.title}`} aria-expanded={menu} aria-haspopup="true" disabled={busy} onClick={() => setMenu(!menu)}><MoreHorizontal size={21} /></button>
        {menu && <div className="chart-action-menu" role="group" aria-label={`Opções de ${chart.title}`}>
          <button onClick={() => { setMenu(false); onEdit(); }}><Pencil size={15} />Editar gráfico</button>
          <button onClick={() => { setMenu(false); onDuplicate(); }}><Copy size={15} />Duplicar gráfico</button>
          <button className="chart-delete-action" onClick={() => { setMenu(false); onDelete(); }}><Trash2 size={15} />Excluir gráfico</button>
        </div>}
      </div>
    </header>
    <div className="chart-metric-chips">{chart.metrics.map(metric => <span key={metric}>{metricLabels[metric]}</span>)}</div>
    <ChartFilters config={chart} accounts={accounts} categories={categories} />
    <div className="chart-card-body">{query.isLoading ? <Loading /> : query.error ? <ErrorState error={query.error} retry={() => void query.refetch()} /> : query.data && <ChartVisual config={chart} data={query.data} title={chart.title} />}</div>
    {personalizing && <footer className="chart-card-order"><span>Posição {index + 1} de {total}</span><div className="actions">
      <button className="btn secondary small" aria-label={`Mover ${chart.title} para cima`} disabled={busy || index === 0} onClick={() => onMove(-1)}><ArrowUp size={15} />Subir</button>
      <button className="btn secondary small" aria-label={`Mover ${chart.title} para baixo`} disabled={busy || index === total - 1} onClick={() => onMove(1)}><ArrowDown size={15} />Descer</button>
      <button className="icon-btn" aria-label={`Editar tamanho de ${chart.title}`} disabled={busy} onClick={onEdit}><Settings2 size={17} /></button>
    </div></footer>}
  </article>;
}

export default function FinanceCharts() {
  const reference = useFinanceData(), client = useQueryClient(), notify = useNotify();
  const panel = useQuery({ queryKey: ['finance', 'charts', 'panel'], queryFn: async ({ signal }) => (await api.get<ChartPanel>('/finance/charts', { signal })).data });
  const catalog = useQuery({ queryKey: ['finance', 'charts', 'catalog'], queryFn: async ({ signal }) => (await api.get<ChartCatalog>('/finance/charts/catalog', { signal })).data, staleTime: 60000 });
  const [editor, setEditor] = useState<ChartView | null | undefined>();
  const [personalizing, setPersonalizing] = useState(false), [deleting, setDeleting] = useState<ChartView | null>(null), [restoring, setRestoring] = useState(false);
  const refresh = () => client.invalidateQueries({ queryKey: ['finance', 'charts'] });
  const failure = (error: unknown) => { notify(errorMessage(error), true); void refresh(); };
  const remove = useMutation({ mutationFn: async () => { if (deleting) await api.delete(`/finance/charts/${deleting.id}`, { params: { version: deleting.version } }); }, onSuccess: async () => { await refresh(); setDeleting(null); notify('Gráfico excluído. Suas movimentações foram preservadas.'); }, onError: failure });
  const duplicate = useMutation({ mutationFn: async (chart: ChartView) => (await api.post<ChartView>(`/finance/charts/${chart.id}/duplicate`, { version: chart.version })).data, onSuccess: async chart => { await refresh(); setEditor(chart); notify('Cópia independente criada. Você pode personalizá-la agora.'); }, onError: failure });
  const restore = useMutation({ mutationFn: async () => (await api.post<ChartPanel>('/finance/charts/restore', { version: panel.data!.version })).data, onSuccess: async () => { await refresh(); setRestoring(false); notify('Gráficos padrão restaurados. Suas movimentações foram preservadas.'); }, onError: failure });
  const reorder = useMutation({ mutationFn: async (ids: number[]) => (await api.put<ChartPanel>('/finance/charts/order', { ids, version: panel.data!.version })).data, onSuccess: async result => { client.setQueryData(['finance', 'charts', 'panel'], result); notify('Ordem do painel salva.'); }, onError: failure });
  const busy = remove.isPending || duplicate.isPending || restore.isPending || reorder.isPending;
  const charts = panel.data?.charts ?? [];
  const move = (index: number, direction: number) => {
    const ids = charts.map(chart => chart.id), target = index + direction;
    if (target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    reorder.mutate(ids);
  };
  if (reference.isLoading || panel.isLoading || catalog.isLoading) return <Loading />;
  if (reference.error || panel.error || catalog.error) return <ErrorState error={reference.error || panel.error || catalog.error} retry={() => { void reference.refetch(); void panel.refetch(); void catalog.refetch(); }} />;
  const { accounts, categories } = reference.data!;
  const maxCharts = catalog.data!.maxCharts;
  return <div className="financial-charts">
    <PageHeader title="Meus gráficos" subtitle="Personalize seus gráficos e acompanhe os números mais importantes do seu negócio." action={<button className="btn" disabled={busy || charts.length >= maxCharts} onClick={() => setEditor(null)}><Plus size={17} />Adicionar gráfico</button>} />
    <FinanceTabs />
    <div className="chart-panel-toolbar"><div className="chart-panel-count"><BarChart3 size={19} /><span>{charts.length} gráfico{charts.length !== 1 ? 's' : ''} no seu painel</span><small>Seus dados, do seu jeito.</small></div><div className="actions">
      <button className="btn secondary small" aria-label="Atualizar dados dos gráficos" disabled={busy} onClick={() => void refresh()}><RefreshCw size={15} />Atualizar</button>
      <button className={`btn ${personalizing ? '' : 'secondary'} small`} aria-pressed={personalizing} disabled={busy || !charts.length} onClick={() => setPersonalizing(!personalizing)}><Settings2 size={15} />{personalizing ? 'Concluir personalização' : 'Personalizar painel'}</button>
      <button className="btn secondary small" disabled={busy} onClick={() => setRestoring(true)}><RotateCcw size={15} />Restaurar gráficos padrão</button>
    </div></div>
    {personalizing && <div className="chart-panel-notice" role="status"><Settings2 size={18} /><p>Use Subir e Descer para organizar seus gráficos. A ordem é salva automaticamente. Para mudar o tamanho, abra a edição do gráfico.</p></div>}
    {charts.length >= maxCharts && <p className="chart-help">Seu painel atingiu o limite de {maxCharts} gráficos. Exclua um gráfico para adicionar outro.</p>}
    {!charts.length ? <section className="panel"><Empty title="Você ainda não criou nenhum gráfico." action={<button className="btn" onClick={() => setEditor(null)}><Plus size={17} />Criar meu primeiro gráfico</button>}>Crie seu primeiro gráfico para acompanhar as informações financeiras mais importantes do seu negócio.</Empty></section> : <div className="chart-grid">{charts.map((chart, index) => <ChartCard key={chart.id} chart={chart} accounts={accounts} categories={categories} personalizing={personalizing} index={index} total={charts.length} busy={busy} onEdit={() => setEditor(chart)} onDuplicate={() => { if (charts.length >= maxCharts) notify(`Seu painel aceita até ${maxCharts} gráficos.`, true); else duplicate.mutate(chart); }} onDelete={() => setDeleting(chart)} onMove={direction => move(index, direction)} />)}</div>}
    <p className="chart-footnote">Valores efetivos, atualizados a partir das movimentações financeiras. Transferências não contam como receitas ou despesas; retiradas ficam separadas. Atualização automática a cada minuto enquanto esta aba estiver aberta.</p>
    <details className="chart-unavailable"><summary>Por que algumas análises ainda não aparecem?</summary><ul>{catalog.data!.metrics.filter(metric => !metric.available).map(metric => <li key={metric.id}><strong>{metric.label}:</strong> {metric.reason ?? metric.description}</li>)}{catalog.data!.unavailableFilters.map(filter => <li key={filter.id}><strong>{filter.label}:</strong> {filter.reason}</li>)}</ul></details>
    {editor !== undefined && <ChartBuilder key={editor?.id ?? 'new'} chart={editor ?? undefined} catalog={catalog.data!} accounts={accounts} categories={categories} onClose={() => setEditor(undefined)} />}
    {deleting && <Confirm title="Excluir gráfico?" description={`“${deleting.title}” será removido do seu painel. Nenhuma movimentação financeira será apagada.`} busy={remove.isPending} danger onConfirm={() => remove.mutate()} onClose={() => setDeleting(null)} />}
    {restoring && <Confirm title="Restaurar gráficos padrão?" description="As personalizações e os gráficos atuais deste painel serão substituídos pelos gráficos padrão disponíveis. Suas movimentações financeiras não serão alteradas." busy={restore.isPending} onConfirm={() => restore.mutate()} onClose={() => setRestoring(false)} />}
  </div>;
}
