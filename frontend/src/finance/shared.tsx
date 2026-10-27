import { useQuery, useQueryClient } from '@tanstack/react-query';
import { NavLink } from 'react-router-dom';
import { api } from '../api';
import { date, dateTime, money } from '../money';
import { ErrorState, Loading, Modal, Empty } from '../components/ui';
import type { FinanceAccount, FinanceCategory, FinanceAudit, FinanceKind, FinanceStatus } from './types';
export const kindLabels: Record<FinanceKind,string> = {RECEITA:'Receita', DESPESA:'Despesa operacional', RETIRADA:'Retirada pessoal'};
export const statusLabels: Record<FinanceStatus,string> = {PENDENTE:'Pendente', LIQUIDADO:'Liquidado', CANCELADO:'Cancelado'};
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
    <NavLink to="/financeiro/contas">Contas e saldos</NavLink>
  </nav>;
}
export function StatusTag({status}:{status:FinanceStatus}) {
  return <span className={`badge ${status==='PENDENTE'?'badge-aguardando':status==='LIQUIDADO'?'badge-aceito':'badge-rascunho'}`}><span aria-hidden="true"/>{statusLabels[status]}</span>;
}
const snapshotFields: Record<string,string> = {name:'Conta', description:'Descrição', amount:'Valor', status:'Situação', accountName:'Conta', categoryName:'Categoria', settlementDate:'Liquidação', openingBalance:'Saldo inicial', openingDate:'Referência', balance:'Saldo',active:'Conta ativa',entryDate:'Competência',notes:'Observações',kind:'Natureza',sourceAccountId:'Conta de origem',targetAccountId:'Conta de destino',transferDate:'Data da transferência',canceledAt:'Estornada em'};
function Snapshot({value}:{value:string}) {
  let data: Record<string,unknown>;
  try { data=JSON.parse(value); } catch { return <p>Detalhes indisponíveis.</p>; }
  return <dl className="finance-history-data">{Object.entries(snapshotFields).filter(([key])=>data[key]!=null).map(([key,label])=>{
    const raw=data[key]; let text=String(raw);
    if (['amount','openingBalance','balance'].includes(key)) text=money(text);
    else if (['settlementDate','openingDate','entryDate','transferDate'].includes(key)) text=date(text);
    else if (key==='canceledAt') text=dateTime(text);
    else if (key==='kind') text=kindLabels[text as FinanceKind]??text;
    else if (key==='status') text=statusLabels[text as FinanceStatus]??text;
    else if (typeof raw==='boolean') text=raw?'Sim':'Não';
    return <div key={key}><dt>{label}</dt><dd>{text}</dd></div>;
  })}</dl>;
}
export function HistoryModal({resource,id,onClose}:{resource:'CONTA'|'LANCAMENTO'|'TRANSFERENCIA';id:number;onClose:()=>void}) {
  const query=useQuery({queryKey:['finance','history',resource,id],queryFn:async()=> (await api.get<FinanceAudit[]>(`/finance/history/${resource}/${id}`)).data});
  const labels:Record<string,string>={CRIACAO:'Criação',EDICAO:'Edição',CANCELAMENTO:'Cancelamento',LIQUIDACAO:'Liquidação'};
  return <Modal title="Histórico financeiro" onClose={onClose}>
    {query.isLoading?<Loading/>:query.error?<ErrorState error={query.error} retry={()=>void query.refetch()}/>:!query.data?.length?<Empty title="Sem alterações registradas">A conta foi criada automaticamente antes do primeiro uso.</Empty>:
      <ol className="finance-history">{query.data.map(item=><li key={item.id}>
        <strong>{labels[item.action]??item.action}</strong><p className="muted">{dateTime(item.createdAt)} · {item.actorId?`Usuário #${item.actorId}`:'Sistema'}</p>
        {item.beforeData&&<details><summary>Antes da alteração</summary><Snapshot value={item.beforeData}/></details>}
        <details open><summary>Dados registrados</summary><Snapshot value={item.afterData}/></details>
      </li>)}</ol>}
  </Modal>;
}
