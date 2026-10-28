import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AreaChart, BarChart3, Check, ChevronLeft, ChevronRight, Donut, LineChart, PieChart } from 'lucide-react';
import { api, errorMessage } from '../../api';
import { today } from '../../money';
import { ErrorState, Loading, Modal, useNotify } from '../../components/ui';
import type { FinanceAccount, FinanceCategory } from '../types';
import ChartVisual from './ChartVisual';
import ChartFilters, { PeriodLabel } from './ChartFilters';
import { allowsCategories, configurationError } from './rules';
import { configOf, groupingLabels, initialConfig, metricLabels, periodLabels, typeLabels } from './types';
import type { ChartCatalog, ChartConfig, ChartData, ChartType, ChartView, Metric, Grouping, Period } from './types';

const icons = { BARRAS: BarChart3, LINHAS: LineChart, AREA: AreaChart, PIZZA: PieChart, ROSCA: Donut };
const steps = ['O que acompanhar', 'Como visualizar', 'Salvar no painel'];
function useDebounced<T>(value: T, delay: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => { const timer = window.setTimeout(() => setDebounced(value), delay); return () => window.clearTimeout(timer); }, [value, delay]);
  return debounced;
}
function selected(ids: number[], id: number, checked: boolean) { return checked ? [...ids, id] : ids.filter(item => item !== id); }

export default function ChartBuilder({ chart, catalog, accounts, categories, onClose }: {
  chart?: ChartView; catalog: ChartCatalog; accounts: FinanceAccount[]; categories: FinanceCategory[]; onClose: () => void;
}) {
  const [config, setConfig] = useState<ChartConfig>(() => chart ? configOf(chart) : initialConfig());
  const [step, setStep] = useState(0);
  const notify = useNotify(), client = useQueryClient();
  const update = <K extends keyof ChartConfig>(key: K, value: ChartConfig[K]) => setConfig(current => ({ ...current, [key]: value }));
  const error = configurationError(config);
  // Visual-only options and the title never trigger a new aggregate query.
  const aggregateKey = JSON.stringify({ ...config, title: 'Prévia do gráfico', size: 'MEDIO', palette: 'PADRAO', showValues: false });
  const debouncedKey = useDebounced(aggregateKey, 350);
  const waitingForPreview = aggregateKey !== debouncedKey;
  const previewConfig: ChartConfig = JSON.parse(debouncedKey);
  const preview = useQuery({
    queryKey: ['finance', 'charts', 'preview', debouncedKey],
    queryFn: async ({ signal }) => (await api.post<ChartData>('/finance/charts/preview', previewConfig, { signal })).data,
    enabled: step > 0 && !error && !waitingForPreview,
    retry: false,
    staleTime: 15000,
  });
  const save = useMutation({
    mutationFn: async () => {
      const payload = { ...configOf(config), title: config.title.trim() };
      if (chart) return (await api.put<ChartView>(`/finance/charts/${chart.id}`, { ...payload, version: chart.version })).data;
      return (await api.post<ChartView>('/finance/charts', payload)).data;
    },
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['finance', 'charts'] }); notify(chart ? 'Gráfico atualizado.' : 'Gráfico adicionado ao seu painel.'); onClose(); },
  });
  const titleError = configurationError(config, true);
  const metricToggle = (metric: Metric, checked: boolean) => {
    setConfig(current => {
      let metrics = checked ? [...current.metrics, metric] : current.metrics.filter(value => value !== metric);
      if (checked && metric === 'SALDO_DISPONIVEL') metrics = ['SALDO_DISPONIVEL'];
      else if (checked) metrics = metrics.filter(value => value !== 'SALDO_DISPONIVEL');
      const categoryIds = allowsCategories(metrics) ? current.categoryIds.filter(id => {
        const kind = categories.find(item => item.id === id)?.kind;
        return (kind === 'RECEITA' && metrics.includes('RECEITAS_RECEBIDAS')) || (kind === 'DESPESA' && metrics.includes('DESPESAS_PAGAS'));
      }) : [];
      return { ...current, metrics, categoryIds };
    });
  };
  const categoryOptions = categories.filter(item => item.kind === 'RECEITA' ? config.metrics.includes('RECEITAS_RECEBIDAS') : config.metrics.includes('DESPESAS_PAGAS'));
  const typeDisabled = (type: ChartType) => (type === 'PIZZA' || type === 'ROSCA') && (config.metrics.length !== 1 || config.metrics.some(metric => metric === 'SALDO_DISPONIVEL' || metric === 'RESULTADO_CAIXA'));
  const changeType = (type: ChartType) => setConfig(current => ({ ...current, type,
    grouping: (type === 'PIZZA' || type === 'ROSCA') && !['CATEGORIA', 'CONTA'].includes(current.grouping)
      ? current.metrics.includes('RETIRADAS_PESSOAIS') ? 'CONTA' : 'CATEGORIA' : current.grouping,
  }));
  return <Modal title={chart ? 'Editar gráfico' : 'Adicionar gráfico'} onClose={onClose} busy={save.isPending}>
    <div className="chart-builder">
      <ol className="chart-steps" aria-label="Etapas do construtor">{steps.map((label, index) => <li key={label} aria-current={index === step ? 'step' : undefined} className={index < step ? 'complete' : ''}><span>{index < step ? <Check size={15} aria-hidden="true" /> : index + 1}</span>{label}</li>)}</ol>
      <div className={`chart-builder-content ${step > 0 ? 'with-preview' : ''}`}>
        <div className="chart-builder-fields">
          {step === 0 && <>
            <header className="chart-section-heading"><h3>O que você quer acompanhar?</h3><p>Escolha informações do dinheiro que já entrou ou saiu do seu negócio.</p></header>
            <fieldset className="chart-metric-options"><legend className="sr-only">Métricas financeiras</legend>{catalog.metrics.filter(metric => metric.available).map(metric => <label className={`chart-choice ${config.metrics.includes(metric.id as Metric) ? 'selected' : ''}`} key={metric.id}>
              <input type="checkbox" checked={config.metrics.includes(metric.id as Metric)} onChange={event => metricToggle(metric.id as Metric, event.target.checked)} />
              <span><strong>{metric.label}</strong><small>{metric.description}</small></span>
            </label>)}</fieldset>
            <p className="chart-help">O resultado operacional de caixa é receitas recebidas menos despesas operacionais pagas. Ele não representa lucro contábil. Retiradas pessoais são analisadas separadamente.</p>
            <section className="chart-filter-section"><h3>Quais informações deseja filtrar?</h3><p className="muted">Deixe sem seleção para considerar todas as opções compatíveis.</p>
              <fieldset className="chart-checklist"><legend>Contas financeiras</legend>{accounts.map(account => <label key={account.id}><input type="checkbox" checked={config.accountIds.includes(account.id)} onChange={event => update('accountIds', selected(config.accountIds, account.id, event.target.checked))} /><span>{account.name}{!account.active ? ' (inativa)' : ''}</span></label>)}</fieldset>
              {allowsCategories(config.metrics) ? <fieldset className="chart-checklist"><legend>Categorias</legend>{categoryOptions.length ? categoryOptions.map(category => <label key={category.id}><input type="checkbox" checked={config.categoryIds.includes(category.id)} onChange={event => update('categoryIds', selected(config.categoryIds, category.id, event.target.checked))} /><span>{category.name}{config.metrics.length > 1 ? ` · ${category.kind === 'RECEITA' ? 'Receita' : 'Despesa'}` : ''}</span></label>) : <p className="muted">Nenhuma categoria compatível. Cadastre categorias em Movimentações.</p>}</fieldset> : <p className="chart-help">Categorias podem filtrar receitas recebidas e despesas pagas. Saldo, resultado e retiradas usam suas regras próprias.</p>}
              <p className="chart-help">Situação: somente movimentos efetivamente liquidados. Lançamentos pendentes não entram nestes gráficos.</p>
            </section>
            <details className="chart-unavailable"><summary>Informações ainda indisponíveis</summary>
              <p className="muted">O construtor utiliza os vínculos e regras existentes no financeiro.</p>
              <ul>{catalog.metrics.filter(metric => !metric.available).map(metric => <li key={metric.id}><strong>{metric.label}:</strong> {metric.reason ?? metric.description}</li>)}{catalog.unavailableFilters.map(filter => <li key={filter.id}><strong>{filter.label}:</strong> {filter.reason}</li>)}</ul>
            </details>
          </>}
          {step === 1 && <>
            <header className="chart-section-heading"><h3>Como deseja visualizar?</h3><p>Compare valores ou acompanhe sua evolução ao longo do tempo.</p></header>
            <fieldset className="chart-type-options"><legend>Tipo de gráfico</legend>{Object.entries(typeLabels).map(([value, label]) => {
              const type = value as ChartType, Icon = icons[type];
              return <label key={type} className={`chart-type-choice ${config.type === type ? 'selected' : ''} ${typeDisabled(type) ? 'disabled' : ''}`}>
                <input type="radio" name="chart-type" value={type} checked={config.type === type} disabled={typeDisabled(type)} onChange={() => changeType(type)} /><Icon size={23} aria-hidden="true" /><span>{label}</span>
              </label>;
            })}</fieldset>
            {(config.metrics.length > 1 || config.metrics.includes('SALDO_DISPONIVEL') || config.metrics.includes('RESULTADO_CAIXA')) && <p className="chart-help">Pizza e rosca precisam de uma única métrica de receitas, despesas ou retiradas, dividida por categoria ou conta.</p>}
            <label className="field"><span>Como deseja organizar os dados?</span><select aria-label="Agrupamento" value={config.grouping} onChange={event => update('grouping', event.target.value as Grouping)}>{Object.entries(groupingLabels).map(([value, label]) => <option key={value} value={value} disabled={value === 'CATEGORIA' && (config.metrics.length !== 1 || !allowsCategories(config.metrics))}>{label}</option>)}</select></label>
            <label className="field"><span>Qual período deseja analisar?</span><select aria-label="Período" value={config.period} onChange={event => update('period', event.target.value as Period)}>{Object.entries(periodLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            {config.period === 'PERSONALIZADO' && <div className="form-grid"><label className="field"><span>Data inicial</span><input type="date" max={config.to || today()} value={config.from ?? ''} onChange={event => update('from', event.target.value || null)} /></label><label className="field"><span>Data final</span><input type="date" min={config.from ?? undefined} max={today()} value={config.to ?? ''} onChange={event => update('to', event.target.value || null)} /></label></div>}
            <p className="chart-help">Valores por data efetiva, no horário de São Paulo. O saldo mostra o valor ao final de cada período; não é uma soma de saldos diários.</p>
          </>}
          {step === 2 && <>
            <header className="chart-section-heading"><h3>Salvar no meu painel</h3><p>Dê um nome fácil de reconhecer e confira suas escolhas.</p></header>
            <label className="field"><span>Nome do gráfico</span><input aria-label="Título do gráfico" autoFocus maxLength={120} value={config.title} onChange={event => update('title', event.target.value)} placeholder="Ex.: Meus gastos com combustível" /></label>
            <div className="form-grid"><label className="field"><span>Tamanho no painel</span><select value={config.size} onChange={event => update('size', event.target.value as ChartConfig['size'])}><option value="PEQUENO">Pequeno</option><option value="MEDIO">Médio</option><option value="GRANDE">Grande</option></select></label><label className="field"><span>Paleta de cores</span><select value={config.palette} onChange={event => update('palette', event.target.value as ChartConfig['palette'])}><option value="PADRAO">Cores do sistema</option><option value="ACESSIVEL">Alto contraste</option></select></label></div>
            <label className="chart-inline-check"><input type="checkbox" checked={config.showValues} onChange={event => update('showValues', event.target.checked)} />Mostrar valores no gráfico</label>
            <dl className="chart-review"><div><dt>Visualização</dt><dd>{typeLabels[config.type]} · {groupingLabels[config.grouping]}</dd></div><div><dt>Informações</dt><dd>{config.metrics.map(metric => metricLabels[metric]).join(' + ')}</dd></div><div><dt>Período</dt><dd><PeriodLabel config={config} /></dd></div></dl>
            <ChartFilters config={config} accounts={accounts} categories={categories} />
          </>}
          {error && <p className="chart-inline-error" role="alert">{error}</p>}
          {step === 2 && titleError && !error && <p className="chart-help">{titleError}</p>}
          {save.error && <div className="chart-inline-error" role="alert">{errorMessage(save.error)} Seu preenchimento foi preservado.</div>}
        </div>
        {step > 0 && <aside className="chart-preview" aria-label="Prévia do gráfico"><div className="chart-preview-heading"><span className="eyebrow">PRÉVIA COM DADOS REAIS</span><h3>{config.title || 'Seu próximo gráfico'}</h3><p><PeriodLabel config={config} /></p></div>
          {error ? <p className="chart-help">Ajuste as opções para visualizar os dados.</p> : waitingForPreview || preview.isLoading ? <Loading /> : preview.error ? <ErrorState error={preview.error} retry={() => void preview.refetch()} /> : preview.data ? <ChartVisual config={config} data={preview.data} title="Prévia do gráfico" /> : <p role="status">Preparando prévia…</p>}
        </aside>}
      </div>
      <footer className="chart-builder-footer"><span>Etapa {step + 1} de 3</span><div className="actions">
        <button className="btn secondary" disabled={save.isPending} onClick={() => step > 0 ? setStep(step - 1) : onClose()}>{step > 0 && <ChevronLeft size={16} />}{step > 0 ? 'Voltar' : 'Cancelar'}</button>
        {step < 2 ? <button className="btn" disabled={step === 0 ? !config.metrics.length : !!error} onClick={() => setStep(step + 1)}>Continuar<ChevronRight size={16} /></button> : <button className="btn" disabled={!!titleError || save.isPending || preview.isFetching || waitingForPreview || !!preview.error || !preview.data} onClick={() => save.mutate()}>{save.isPending ? 'Salvando…' : chart ? 'Salvar alterações' : 'Salvar gráfico'}<Check size={16} /></button>}
      </div></footer>
    </div>
  </Modal>;
}
