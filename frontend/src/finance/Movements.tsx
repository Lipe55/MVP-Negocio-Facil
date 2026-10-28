import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Plus, History, Pencil, Ban, Check, RefreshCw } from 'lucide-react';
import { api, errorMessage } from '../api';
import { date, money } from '../money';
import type { Page } from '../types';
import type { FinanceEntry, FinanceKind, FinanceStatus } from './types';
import { Confirm, Empty, ErrorState, Loading, PageHeader, Pagination, useNotify } from '../components/ui';
import { EntryForm, CategoryForm, SettlementForm } from './forms';
import { FinanceTabs, HistoryModal, kindLabels, StatusTag, useFinanceData, useRefreshFinance } from './shared';

export default function FinanceMovements() {
  const reference=useFinanceData(), refresh=useRefreshFinance(), notify=useNotify();
  const [kind,setKind]=useState(''),[status,setStatus]=useState(''),[accountId,setAccountId]=useState('');
  const [search,setSearch]=useState(''),[from,setFrom]=useState(''),[to,setTo]=useState(''),[page,setPage]=useState(0);
  const [editor,setEditor]=useState<{kind:FinanceKind;entry?:FinanceEntry}|null>(null);
  const [category,setCategory]=useState(false),[history,setHistory]=useState<number|null>(null);
  const [cancel,setCancel]=useState<FinanceEntry|null>(null),[settle,setSettle]=useState<FinanceEntry|null>(null);
  const entries=useQuery({queryKey:['finance','entries',{kind,status,accountId,search,from,to,page}],queryFn:async()=>
    (await api.get<Page<FinanceEntry>>('/finance/entries',{params:{kind:kind||undefined,status:status||undefined,accountId:accountId||undefined,search,from:from||undefined,to:to||undefined,page}})).data});
  const cancellation=useMutation({mutationFn:async()=>{if(cancel)await api.post(`/finance/entries/${cancel.id}/cancel`,{version:cancel.version});},onSuccess:async()=>{await refresh();setCancel(null);notify('Lançamento cancelado. Movimentos efetivos foram estornados.');},onError:e=>{notify(errorMessage(e),true);void refresh();}});
  if(reference.isLoading)return <Loading/>;
  if(reference.error)return <ErrorState error={reference.error} retry={()=>void reference.refetch()}/>;
  const data=reference.data!;
  return <>
    <PageHeader title="Financeiro" subtitle="Entradas, saídas e retiradas com histórico e saldo real." action={<button className="btn secondary" onClick={()=>void refresh()}><RefreshCw size={16}/>Atualizar</button>}/>
    <FinanceTabs/>
    <div className="finance-toolbar">
      <div className="actions"><button className="btn" onClick={()=>setEditor({kind:'RECEITA'})}><Plus size={16}/>Nova receita</button><button className="btn secondary" onClick={()=>setEditor({kind:'DESPESA'})}><Plus size={16}/>Nova despesa</button><button className="btn secondary" onClick={()=>setEditor({kind:'RETIRADA'})}>Retirada pessoal</button></div>
      <button className="text-link" onClick={()=>setCategory(true)}>Nova categoria</button>
    </div>
    <p className="finance-note">Pendências não alteram o saldo. Retiradas pessoais e transferências são separadas das despesas operacionais.</p>
    <section className="panel">
      <div className="finance-filters">
        <label className="field"><span>Buscar descrição</span><input value={search} maxLength={160} onChange={e=>{setSearch(e.target.value);setPage(0);}} placeholder="Busque uma movimentação…"/></label>
        <label className="field"><span>Natureza</span><select value={kind} onChange={e=>{setKind(e.target.value);setPage(0);}}><option value="">Todas</option>{Object.entries(kindLabels).map(([k,label])=><option key={k} value={k}>{label}</option>)}</select></label>
        <label className="field"><span>Situação</span><select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">Todas</option><option value="PENDENTE">Pendente</option><option value="PARCIAL">Parcial</option><option value="LIQUIDADO">Liquidado</option><option value="CANCELADO">Cancelado</option></select></label>
        <label className="field"><span>Conta</span><select value={accountId} onChange={e=>{setAccountId(e.target.value);setPage(0);}}><option value="">Todas as contas</option>{data.accounts.map(a=><option key={a.id} value={a.id}>{a.name}{!a.active?' (inativa)':''}</option>)}</select></label>
        <label className="field"><span>Competência de</span><input type="date" value={from} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></label>
        <label className="field"><span>Até</span><input type="date" value={to} onChange={e=>{setTo(e.target.value);setPage(0);}}/></label>
      </div>
      {entries.isLoading?<Loading/>:entries.error?<ErrorState error={entries.error} retry={()=>void entries.refetch()}/>:!entries.data?.content.length?<Empty title="Nenhum lançamento encontrado">Registre uma receita, uma despesa ou uma retirada pessoal para começar.</Empty>:<>
        <div className="table-scroll"><table><thead><tr><th>Competência</th><th>Descrição</th><th>Natureza / categoria</th><th>Conta</th><th>Valor</th><th>Situação</th><th>Ações</th></tr></thead><tbody>
          {entries.data.content.map(e=><tr key={e.id}>
            <td className="nowrap">{date(e.entryDate)}</td><td><strong>{e.description}</strong>{e.settlementDate&&<small className="finance-secondary">Efetivado em {date(e.settlementDate)}</small>}{e.dueDate&&<small className="finance-secondary">Vencimento {date(e.dueDate)}{(e.installmentCount??1)>1?` · Parcela ${e.installmentNumber}/${e.installmentCount}`:''}</small>}</td>
            <td>{kindLabels[e.kind]}<small className="finance-secondary">{e.categoryName??'Uso pessoal'}</small></td><td>{e.accountName}</td>
            <td className={`table-money ${e.status==='CANCELADO'?'finance-canceled':e.kind==='RECEITA'?'finance-income':'finance-outflow'}`}>{money(e.amount)}{e.status==='PARCIAL'&&e.paidAmount&&e.remainingAmount&&<small className="finance-secondary">Liquidado {money(e.paidAmount)} · Pendente {money(e.remainingAmount)}</small>}</td>
            <td><StatusTag status={e.status}/></td><td><div className="row-actions">
              <button className="icon-btn" aria-label={`Histórico de ${e.description}`} onClick={()=>setHistory(e.id)}><History size={17}/></button>
              {e.status!=='CANCELADO'&&<>{e.managed||(e.installmentCount??1)>1||e.recurrenceId!=null||e.status==='PARCIAL'?<Link className="btn secondary small" to={e.kind==='RECEITA'?'/financeiro/receber':'/financeiro/pagar'}>Gerenciar parcela</Link>:<button className="icon-btn" aria-label={`Editar ${e.description}`} onClick={()=>setEditor({kind:e.kind,entry:e})}><Pencil size={17}/></button>}
                {(e.status==='PENDENTE'||e.status==='PARCIAL')&&<button className="icon-btn" aria-label={`Liquidar ${e.description}`} onClick={()=>setSettle(e)}><Check size={17}/></button>}
                <button className="icon-btn" aria-label={`Cancelar ${e.description}`} onClick={()=>setCancel(e)}><Ban size={17}/></button></>}
            </div></td>
          </tr>)}
        </tbody></table></div>
        <Pagination page={page} totalPages={entries.data.totalPages} total={entries.data.totalElements} onChange={setPage}/>
      </>}
    </section>
    {editor&&<EntryForm key={editor.entry?.id??editor.kind} {...editor} accounts={data.accounts} categories={data.categories} onClose={()=>setEditor(null)}/>}
    {category&&<CategoryForm onClose={()=>setCategory(false)}/>}
    {history!=null&&<HistoryModal resource="LANCAMENTO" id={history} onClose={()=>setHistory(null)}/>}
    {settle&&<SettlementForm entry={settle} account={data.accounts.find(a=>a.id===settle.accountId)!} onClose={()=>setSettle(null)}/>}
    {cancel&&<Confirm title="Cancelar lançamento?" description={`“${cancel.description}” permanecerá no histórico. Todas as liquidações existentes desta parcela serão estornadas na conta. Outras parcelas serão preservadas. Esta ação não executa reembolso bancário.`} busy={cancellation.isPending} danger onClose={()=>setCancel(null)} onConfirm={()=>cancellation.mutate()}/>}
  </>;
}
