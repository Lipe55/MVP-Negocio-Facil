import { useQuery, useQueryClient } from '@tanstack/react-query';
import { NavLink } from 'react-router-dom';
import { api } from '../api';
import { date, dateTime, money } from '../money';
import { ErrorState, Loading, Modal, Empty } from '../components/ui';
import type { FinanceAccount, FinanceCategory, FinanceAudit, FinanceKind, FinanceStatus } from './types';
export const kindLabels: Record<FinanceKind,string> = {RECEITA:'Receita', DESPESA:'Despesa operacional', RETIRADA:'Retirada pessoal'};
export const statusLabels: Record<FinanceStatus,string> = {PENDENTE:'Pendente', PARCIAL:'Parcial', LIQUIDADO:'Liquidado', CANCELADO:'Cancelado'};
export function useFinanceData() {
  return useQuery({queryKey:['finance','reference'],queryFn: async()=> {
    const [accounts,categories] = await Promise.all([api.get<FinanceAccount[]>('/finance/accounts'),api.get<FinanceCategory[]>('/finance/categories')]);
    return {accounts:accounts.data,categories:categories.data};
  }});
}
export function useRefreshFinance() {
  const client=useQueryClient();
  return async()=>{ await client.invalidateQueries({queryKey:['finance']}); };
}
export function FinanceTabs() {
  return <nav className="finance-tabs" aria-label="Seções do financeiro">
    <NavLink to="/financeiro/movimentacoes">Movimentações</NavLink>
    <NavLink to="/financeiro/graficos">Gráficos</NavLink>
    <NavLink to="/financeiro/contas">Contas e saldos</NavLink>
    <NavLink to="/financeiro/receber">Contas a receber</NavLink>
    <NavLink to="/financeiro/pagar">Contas a pagar</NavLink>
  </nav>;
}
export function StatusTag({status}:{status:FinanceStatus}) {
  return <span className={`badge ${status==='PENDENTE'||status==='PARCIAL'?'badge-aguardando':status==='LIQUIDADO'?'badge-aceito':'badge-rascunho'}`}><span aria-hidden="true"/>{statusLabels[status]}</span>;
}
const snapshotFields: Record<string,string> = {name:'Conta', description:'Descrição', amount:'Valor', totalAmount:'Valor total', paidAmount:'Liquidado', remainingAmount:'Saldo pendente', dueDate:'Vencimento', installmentNumber:'Parcela', installmentCount:'Quantidade de parcelas', occurrenceNumber:'Ocorrência', occurrences:'Quantidade de ocorrências', firstDueDate:'Primeiro vencimento', status:'Situação', accountName:'Conta', categoryName:'Categoria', settlementDate:'Liquidação', openingBalance:'Saldo inicial', openingDate:'Referência', balance:'Saldo',active:'Ativo',entryDate:'Competência',notes:'Observações',kind:'Natureza',sourceAccountId:'Conta de origem',targetAccountId:'Conta de destino',transferDate:'Data da transferência',canceledAt:'Estornada em',reason:'Motivo',settlementId:'Identificador da liquidação',updatedCount:'Ocorrências alteradas',skippedCount:'Ocorrências preservadas'};
function Snapshot({value}:{value:string}) {
  let data: Record<string,unknown>;
  try {
    const parsed:unknown=JSON.parse(value);
    const record=(item:unknown):item is Record<string,unknown>=>item!==null&&typeof item==='object'&&!Array.isArray(item);
    if(!record(parsed))return <p>Detalhes indisponíveis.</p>;
    data={...(record(parsed.entry)?parsed.entry:{}),...(record(parsed.recurrence)?parsed.recurrence:{}),...(record(parsed.result)?parsed.result:{}),...parsed};
  } catch { return <p>Detalhes indisponíveis.</p>; }
  return <dl className="finance-history-data">{Object.entries(snapshotFields).filter(([key])=>data[key]!=null).map(([key,label])=>{
    const raw=data[key]; let text=String(raw);
    if (['amount','totalAmount','paidAmount','remainingAmount','openingBalance','balance'].includes(key)) text=money(text);
    else if (['settlementDate','openingDate','entryDate','transferDate','dueDate','firstDueDate'].includes(key)) text=date(text);
    else if (key==='canceledAt') text=dateTime(text);
    else if (key==='kind') text=kindLabels[text as FinanceKind]??text;
    else if (key==='status') text=statusLabels[text as FinanceStatus]??text;
    else if (typeof raw==='boolean') text=raw?'Sim':'Não';
    return <div key={key}><dt>{label}</dt><dd>{text}</dd></div>;
  })}</dl>;
}
export function HistoryModal({resource,id,onClose}:{resource:'CONTA'|'LANCAMENTO'|'TRANSFERENCIA'|'OBRIGACAO'|'RECORRENCIA';id:number;onClose:()=>void}) {
  const query=useQuery({queryKey:['finance','history',resource,id],queryFn:async()=> (await api.get<FinanceAudit[]>(`/finance/history/${resource}/${id}`)).data});
  const labels:Record<string,string>={CRIACAO:'Criação',EDICAO:'Edição',EDICAO_PARCELA:'Edição de parcela',CANCELAMENTO:'Cancelamento',CANCELAMENTO_PARCELA:'Cancelamento de parcela',LIQUIDACAO:'Liquidação',ESTORNO:'Estorno',ESTORNO_LIQUIDACAO:'Estorno de liquidação',EDICAO_FUTURAS:'Edição de ocorrências futuras',CANCELAMENTO_FUTURAS:'Cancelamento de ocorrências futuras'};
  return <Modal title="Histórico financeiro" onClose={onClose}>
    {query.isLoading?<Loading/>:query.error?<ErrorState error={query.error} retry={()=>void query.refetch()}/>:!query.data?.length?<Empty title="Sem alterações registradas">Ainda não há alterações disponíveis para este registro.</Empty>:
      <ol className="finance-history">{query.data.map(item=><li key={item.id}>
        <strong>{labels[item.action]??item.action}</strong><p className="muted">{dateTime(item.createdAt)} · {item.actorId?`Usuário #${item.actorId}`:'Sistema'}</p>
        {item.beforeData&&<details><summary>Antes da alteração</summary><Snapshot value={item.beforeData}/></details>}
        <details open><summary>Dados registrados</summary><Snapshot value={item.afterData}/></details>
      </li>)}</ol>}
  </Modal>;
}
