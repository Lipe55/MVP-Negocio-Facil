import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { money, scaled } from '../../money';
import type { ChartConfig, ChartData, Metric } from './types';

type Selection = { index: number; metric: Metric };
const groupingLabels: Record<string, string> = {
  DIA: 'Dia', SEMANA: 'Semana', MES: 'Mês', ANO: 'Ano', CATEGORIA: 'Categoria', CONTA: 'Conta financeira',
};
const color = (index: number) => `var(--chart-${index % 5 + 1})`;
function cents(value: string): bigint {
  return value.startsWith('-') ? -scaled(value.slice(1), 2) : scaled(value, 2);
}
function amount(data: ChartData, index: number, metric: Metric): bigint {
  return cents(data.points[index]?.values[metric] ?? '0.00');
}
function percent(part: bigint, total: bigint): string {
  if (total === 0n) return '0,00%';
  const value = (part * 10000n + total / 2n) / total;
  return `${value / 100n},${(value % 100n).toString().padStart(2, '0')}%`;
}
function pointOnCircle(cx: number, cy: number, radius: number, angle: number) {
  return { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) };
}

/** Values stay in integer cents; Number is used only to position the SVG marks. */
export default function ChartVisual({ config, data, title = config.title }: {
  config: ChartConfig; data: ChartData; title?: string;
}) {
  const id = useId().replace(/:/g, '');
  const [selection, setSelection] = useState<Selection | null>(null);
  const wrapper = useRef<HTMLDivElement>(null);
  const [viewportWidth, setViewportWidth] = useState(720);
  useEffect(() => {
    const element = wrapper.current;
    if (!element) return;
    const resize = (width: number) => {
      if (width > 0) setViewportWidth(Math.max(320, Math.min(720, Math.round(width))));
    };
    resize(element.getBoundingClientRect().width);
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(entries => { for (const entry of entries) resize(entry.contentRect.width); });
    observer.observe(element);
    return () => observer.disconnect();
  }, [data.hasData, data.points.length]);
  useEffect(() => { setSelection(null); }, [data]);
  const circular = config.type === 'PIZZA' || config.type === 'ROSCA';
  const stock = config.metrics.includes('SALDO_DISPONIVEL');
  const firstMetric = data.series[0]?.metric;
  const sliceValues = firstMetric ? data.points.map((_, i) => amount(data, i, firstMetric)) : [];
  const sliceTotal = sliceValues.reduce((sum, value) => sum + (value > 0n ? value : 0n), 0n);

  if (!data.hasData || !data.points.length || !data.series.length) return <div className="chart-no-data" role="status">
    <p>Ainda não existem movimentações suficientes para este gráfico.</p>
    <small>Confira o período e os filtros selecionados. Nenhum valor fictício é exibido.</small>
  </div>;

  const values = data.points.flatMap((_, i) => data.series.map(series => amount(data, i, series.metric)));
  const minimum = values.reduce((min, value) => value < min ? value : min, 0n);
  let maximum = values.reduce((max, value) => value > max ? value : max, 0n);
  if (minimum === maximum) { maximum += 100n; }
  const span = maximum - minimum;
  const left = Math.max(money(minimum).length, money(maximum).length) > 18 ? 165 : 110;
  const right = viewportWidth - 20, top = 25, bottom = 235;
  const plotWidth = right - left, plotHeight = bottom - top;
  const y = (value: bigint) => bottom - Number(value - minimum) / Number(span) * plotHeight;
  const band = plotWidth / data.points.length;
  const x = (index: number) => left + band * (index + 0.5);
  const baseline = y(0n);
  const ticks = Array.from({ length: 5 }, (_, i) => minimum + span * BigInt(i) / 4n);
  const labelEvery = Math.max(1, Math.ceil(data.points.length / Math.max(2, Math.floor(plotWidth / 110))));
  const showValueLabels = config.showValues && data.points.length * data.series.length <= Math.min(18, Math.max(2, Math.floor(plotWidth / 80)));
  const shortLabel = (label: string) => {
    const value = viewportWidth < 520 && data.grouping === 'SEMANA' ? label.replace(/^Semana de /, '') : label;
    const limit = viewportWidth < 520 ? 12 : 20;
    return value.length > limit ? `${value.slice(0, limit - 1)}…` : value;
  };
  const active = selection && data.points[selection.index] ? selection : null;
  const target = (index: number, metric: Metric, seriesLabel: string, suffix = '') => {
    const name = `${data.points[index].label} · ${seriesLabel}: ${money(amount(data, index, metric))}${suffix}`;
    const selected = active?.index === index && active.metric === metric;
    return {
      role: 'button' as const, tabIndex: 0, 'aria-label': name, 'aria-pressed': selected,
      onPointerEnter: () => setSelection({ index, metric }),
      onFocus: () => setSelection({ index, metric }),
      onClick: () => setSelection({ index, metric }),
      onKeyDown: (event: KeyboardEvent<SVGElement>) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault(); setSelection({ index, metric });
        }
      },
    };
  };
  let accumulated = -Math.PI / 2;
  const circularInvalid = circular && (sliceValues.some(value => value < 0n) || data.series.length !== 1);

  return <div className={`chart-visual chart-palette-${config.palette.toLowerCase()}`} ref={wrapper}>
    {stock && data.openingBalance != null && <p className="chart-opening">Saldo de abertura do período: <strong>{money(data.openingBalance)}</strong></p>}
    {circular && (circularInvalid || sliceTotal === 0n) ? <div className="chart-no-data" role="status"><p>{circularInvalid ? 'Esta visualização exige uma única série de valores não negativos.' : 'Não há valores positivos para distribuir neste gráfico.'}</p><small>Os valores reais continuam disponíveis na tabela.</small></div> : <svg className="chart-svg" viewBox={`0 0 ${viewportWidth} 290`} role="group" aria-labelledby={`${id}-title ${id}-desc`}>
      <title id={`${id}-title`}>{title}</title>
      <desc id={`${id}-desc`}>Valores em reais, agrupados por {groupingLabels[data.grouping]?.toLowerCase()}. Selecione uma marca para consultar seu valor ou abra a tabela de dados.</desc>
      <defs>{Array.from({ length: 5 }, (_, i) => <pattern key={i} id={`${id}-pattern-${i}`} width="8" height="8" patternUnits="userSpaceOnUse" patternTransform={`rotate(${i % 2 ? 45 : -45})`}>
        <rect width="8" height="8" fill={color(i)}/>
        <path d={i % 2 ? 'M 0 0 L 0 8' : 'M 0 0 L 8 0'} stroke="var(--surface)" strokeWidth={i ? 2 : 0} opacity="0.65"/>
      </pattern>)}</defs>
      {circular && firstMetric ? <>
        {data.points.map((point, index) => {
          const value = sliceValues[index];
          if (value <= 0n) return null;
          const fraction = Number(value) / Number(sliceTotal);
          const angle = fraction * Math.PI * 2;
          const startAngle = accumulated;
          const endAngle = accumulated + angle;
          accumulated = endAngle;
          const cx = viewportWidth / 2, cy = 130, radius = 103;
          const start = pointOnCircle(cx, cy, radius, startAngle);
          const end = pointOnCircle(cx, cy, radius, endAngle);
          const labelPoint = pointOnCircle(cx, cy, config.type === 'ROSCA' ? 78 : 68, startAngle + angle / 2);
          const props = target(index, firstMetric, data.series[0].label, ` · ${percent(value, sliceTotal)}`);
          // Two arcs support a full circle without a degenerate start/end path.
          const path = fraction >= 0.999999999
            ? `M ${cx} ${cy} L ${cx} ${cy - radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy + radius} A ${radius} ${radius} 0 1 1 ${cx} ${cy - radius} Z`
            : `M ${cx} ${cy} L ${start.x} ${start.y} A ${radius} ${radius} 0 ${angle > Math.PI ? 1 : 0} 1 ${end.x} ${end.y} Z`;
          return <g key={point.key}>
            <path {...props} d={path} fill={`url(#${id}-pattern-${index % 5})`} stroke="var(--surface)" strokeWidth="2"><title>{props['aria-label']}</title></path>
            {fraction >= 0.035 && <text x={labelPoint.x} y={labelPoint.y} textAnchor="middle" dominantBaseline="central" className="chart-slice-number" pointerEvents="none">{index + 1}</text>}
          </g>;
        })}
        {config.type === 'ROSCA' && <circle cx={viewportWidth / 2} cy="130" r="52" fill="var(--surface)" pointerEvents="none"/>}
        <text x={viewportWidth / 2} y="268" textAnchor="middle" className="chart-axis-title">{data.series[0].label} · valores em R$</text>
      </> : <>
        {ticks.map((tick, i) => <g key={i}>
          <line x1={left} x2={right} y1={y(tick)} y2={y(tick)} className="chart-gridline"/>
          <text x={left - 10} y={y(tick) + 4} textAnchor="end" className="chart-axis-label">{money(tick)}</text>
        </g>)}
        <line x1={left} x2={right} y1={baseline} y2={baseline} className="chart-baseline"/>
        <text x={left} y="13" className="chart-axis-title">Valor (R$)</text>
        {data.points.map((point, i) => (i % labelEvery === 0 || i === data.points.length - 1) && <text key={point.key} x={x(i)} y="255" textAnchor="middle" className="chart-axis-label"><title>{point.label}</title>{shortLabel(point.label)}</text>)}
        <text x={(left + right) / 2} y="282" textAnchor="middle" className="chart-axis-title">{groupingLabels[data.grouping] ?? data.grouping}</text>
        {data.series.map((series, seriesIndex) => {
          const coordinates = data.points.map((_, i) => `${x(i)},${y(amount(data, i, series.metric))}`);
          const line = `M ${coordinates.join(' L ')}`;
          if (config.type === 'BARRAS') {
            const width = Math.min(44, band * 0.74 / data.series.length);
            return <g key={series.metric} data-series={series.metric}>{data.points.map((point, i) => {
              const value = amount(data, i, series.metric);
              const px = x(i) - width * data.series.length / 2 + seriesIndex * width;
              const py = y(value);
              const props = target(i, series.metric, series.label);
              return <g key={point.key}>
                <rect {...props} x={px} y={Math.min(py, baseline)} width={Math.max(0.5, width - 1)} height={Math.max(value === 0n ? 1 : 0.75, Math.abs(py - baseline))} rx={Math.min(3, width / 4)} fill={`url(#${id}-pattern-${seriesIndex})`}><title>{props['aria-label']}</title></rect>
                {showValueLabels && <text x={px + width / 2} y={value < 0n ? py + 14 : py - 6} textAnchor="middle" className="chart-value-label">{money(value)}</text>}
              </g>;
            })}</g>;
          }
          return <g key={series.metric} data-series={series.metric}>
            {config.type === 'AREA' && <path d={`${line} L ${x(data.points.length - 1)},${baseline} L ${x(0)},${baseline} Z`} fill={color(seriesIndex)} fillOpacity="0.14"/>}
            <path d={line} fill="none" stroke={color(seriesIndex)} strokeWidth="2.5" strokeDasharray={seriesIndex ? `${9 - seriesIndex} ${3 + seriesIndex}` : undefined}/>
            {data.points.map((point, i) => {
              const value = amount(data, i, series.metric), py = y(value);
              const props = target(i, series.metric, series.label);
              return <g key={point.key}>
                {seriesIndex % 2 ? <rect x={x(i) - 3} y={py - 3} width="6" height="6" fill={color(seriesIndex)} pointerEvents="none"/> : <circle cx={x(i)} cy={py} r={data.points.length > 60 ? 1.5 : 3} fill={color(seriesIndex)} pointerEvents="none"/>}
                <circle {...props} cx={x(i)} cy={py} r="12" fill="transparent" stroke={active?.index === i && active.metric === series.metric ? color(seriesIndex) : 'transparent'} strokeWidth="2"><title>{props['aria-label']}</title></circle>
                {showValueLabels && <text x={x(i)} y={py - 9 - seriesIndex * 3} textAnchor="middle" className="chart-value-label">{money(value)}</text>}
              </g>;
            })}
          </g>;
        })}
      </>}
    </svg>}
    <div className="chart-tooltip" role="status" aria-live="polite" aria-atomic="true">
      {active ? <><strong>{data.points[active.index].label}</strong><span>{data.series.find(series => series.metric === active.metric)?.label}: <b>{money(amount(data, active.index, active.metric))}</b>{circular && ` · ${percent(amount(data, active.index, active.metric), sliceTotal)}`}</span></> : <span>Passe o cursor, toque ou use o teclado para consultar os valores.</span>}
    </div>
    <ul className="chart-legend" aria-label="Legenda do gráfico">
      {circular && firstMetric ? data.points.map((point, index) => <li key={point.key}>
        <span className="chart-legend-mark" style={{ background: color(index) }} aria-hidden="true">{index + 1}</span><span>{point.label}</span><strong>{money(sliceValues[index])} · {percent(sliceValues[index], sliceTotal)}</strong>
      </li>) : data.series.map((series, index) => {
        const total = stock && config.grouping !== 'CONTA' ? amount(data, data.points.length - 1, series.metric) : data.points.reduce((sum, _, i) => sum + amount(data, i, series.metric), 0n);
        return <li key={series.metric}><span className="chart-legend-mark" style={{ background: color(index) }} aria-hidden="true">{index + 1}</span><span>{series.label}{stock ? ' · ao fim do período' : ' · total no período'}</span><strong>{money(total)}</strong></li>;
      })}
    </ul>
    {data.warnings.map((warning, index) => <p className="chart-data-warning" key={index}>{warning}</p>)}
    <details className="chart-data-table"><summary>Ver dados em tabela</summary><div className="table-scroll"><table>
      <caption>{title} · valores em reais{stock ? ' · saldo ao fim de cada intervalo' : ''}</caption>
      <thead><tr><th scope="col">{groupingLabels[data.grouping] ?? 'Agrupamento'}</th>{data.series.map(series => <th key={series.metric} scope="col">{series.label}</th>)}{circular && <th scope="col">Percentual</th>}</tr></thead>
      <tbody>{data.points.map((point, index) => <tr key={point.key}><th scope="row">{point.label}</th>{data.series.map(series => <td className="table-money" key={series.metric}>{money(amount(data, index, series.metric))}</td>)}{circular && <td>{percent(sliceValues[index] ?? 0n, sliceTotal)}</td>}</tr>)}</tbody>
    </table></div></details>
  </div>;
}
