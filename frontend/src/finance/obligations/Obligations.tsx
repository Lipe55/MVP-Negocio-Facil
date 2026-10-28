import { useState } from 'react';
import { useMutation,useQuery } from '@tanstack/react-query';
import { Plus,RefreshCw } from 'lucide-react';
import { api,errorMessage } from '../../api';
import type { Page } from '../../types';
import { money } from '../../money';
import { Empty,ErrorState,Loading,Modal,PageHeader,Pagination,useNotify } from '../../components/ui';
import { SettlementForm } from '../forms';
import { FinanceTabs,HistoryModal,useFinanceData,useRefreshFinance } from '../shared';
import type { PaymentStatus } from '../types';
import ObligationForm from './ObligationForm';
import InstallmentEditor from './InstallmentEditor';
import InstallmentTable from './InstallmentTable';
import type { InstallmentActions } from './InstallmentTable';
import ObligationDetails from './ObligationDetails';
import PaymentHistory from './PaymentHistory';
import RecurrenceEditor from './RecurrenceEditor';
import { asEntry,paymentLabels } from './types';
import type { Installment,ObligationKind } from './types';
import './obligations.css';

type Dialog={type:'PAY'|'EDIT'|'PAYMENTS'|'HISTORY'|'CANCEL'|'FUTURE'|'CANCEL_FUTURE';entry:Installment}|{type:'CREATE'}|{type:'DETAILS';id:number}|null;
export default function Obligations({kind}:{kind:ObligationKind}) {
  const reference=useFinanceData(),refresh=useRefreshFinance(),notify=useNotify();
  const [status,setStatus]=useState(''),[accountId,setAccountId]=useState(''),[categoryId,setCategoryId]=useState(''),[search,setSearch]=useState(''),[from,setFrom]=useState(''),[to,setTo]=useState(''),[page,setPage]=useState(0);
  const [dialog,setDialog]=useState<Dialog>(null);
  const query=useQuery({queryKey:['finance','installments',{kind,status,accountId,categoryId,search,from,to,page}],queryFn:async({signal})=>(await api.get<Page<Installment>>('/finance/installments',{params:{kind,status:status||undefined,accountId:accountId||undefined,categoryId:categoryId||undefined,search,from:from||undefined,to:to||undefined,page},signal})).data});
  const cancel=useMutation({mutationFn:async()=>{if(dialog?.type==='CANCEL')await api.post(`/finance/entries/${dialog.entry.id}/cancel`,{version:dialog.entry.version});},onSuccess:async()=>{await refresh();setDialog(null);notify('Parcela cancelada. Liquidações existentes foram estornadas e o histórico foi preservado.');},onError:error=>{notify(errorMessage(error),true);void refresh();}});
  const changeFilter=(setter:(value:string)=>void,value:string)=>{setter(value);setPage(0);};
  const actions:InstallmentActions={pay:entry=>setDialog({type:'PAY',entry}),edit:entry=>setDialog({type:'EDIT',entry}),payments:entry=>setDialog({type:'PAYMENTS',entry}),history:entry=>setDialog({type:'HISTORY',entry}),details:entry=>setDialog({type:'DETAILS',id:entry.obligationId}),cancel:entry=>{cancel.reset();setDialog({type:'CANCEL',entry});},future:(entry,cancellation)=>setDialog({type:cancellation?'CANCEL_FUTURE':'FUTURE',entry})};
  if(reference.isLoading)return <Loading/>;
  if(reference.error)return <ErrorState error={reference.error} retry={()=>void reference.refetch()}/>;
  const {accounts,categories}=reference.data!;
  const receiving=kind==='RECEITA';
  return <div className="finance-obligations">
    <PageHeader title={receiving?'Contas a receber':'Contas a pagar'} subtitle={receiving?'Acompanhe cobranças, vencimentos e recebimentos integrais ou parciais.':'Organize vencimentos, parcelas, pagamentos e despesas recorrentes.'} action={<button className="btn" onClick={()=>setDialog({type:'CREATE'})}><Plus size={17}/>{receiving?'Nova conta a receber':'Nova conta a pagar'}</button>}/>
    <FinanceTabs/>
    <div className="obligation-toolbar"><p className="finance-note">Somente valores efetivamente liquidados alteram o saldo. “Vencido” indica uma parcela com saldo pendente e vencimento anterior a hoje.</p><button className="btn secondary small" onClick={()=>void refresh()}><RefreshCw size={15}/>Atualizar</button></div>
    <section className="panel">
      <div className="obligation-filters">
        <label className="field"><span>Buscar descrição</span><input maxLength={160} value={search} onChange={event=>changeFilter(setSearch,event.target.value)} placeholder="Busque uma conta…"/></label>
        <label className="field"><span>Situação</span><select value={status} onChange={event=>changeFilter(setStatus,event.target.value)}><option value="">Todas</option>{Object.entries(paymentLabels).map(([key,label])=><option key={key} value={key as PaymentStatus}>{label}</option>)}</select></label>
        <label className="field"><span>Conta financeira</span><select value={accountId} onChange={event=>changeFilter(setAccountId,event.target.value)}><option value="">Todas as contas</option>{accounts.map(item=><option key={item.id} value={item.id}>{item.name}{!item.active?' (inativa)':''}</option>)}</select></label>
        <label className="field"><span>Categoria</span><select value={categoryId} onChange={event=>changeFilter(setCategoryId,event.target.value)}><option value="">Todas as categorias</option>{categories.filter(item=>item.kind===kind).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label className="field"><span>Vencimento de</span><input type="date" value={from} onChange={event=>changeFilter(setFrom,event.target.value)}/></label>
        <label className="field"><span>Vencimento até</span><input type="date" value={to} onChange={event=>changeFilter(setTo,event.target.value)}/></label>
      </div>
      {(status==='PENDENTE'||status==='PARCIAL')&&<p className="finance-note">O filtro {paymentLabels[status]} também inclui parcelas em atraso {status==='PARCIAL'?'com pagamentos parciais':'ainda sem pagamentos'}. A situação da linha indica quando o vencimento já passou.</p>}
      {query.isLoading?<Loading/>:query.error?<ErrorState error={query.error} retry={()=>void query.refetch()}/>:!query.data?.content.length?<Empty title={receiving?'Nenhuma conta a receber encontrada':'Nenhuma conta a pagar encontrada'} action={<button className="btn secondary" onClick={()=>setDialog({type:'CREATE'})}>{receiving?'Cadastrar conta a receber':'Cadastrar conta a pagar'}</button>}>Cadastre uma conta ou ajuste os filtros. Pendências não representam dinheiro já disponível.</Empty>:<>
        <InstallmentTable entries={query.data.content} actions={actions}/>
        <Pagination page={page} totalPages={query.data.totalPages} total={query.data.totalElements} onChange={setPage}/>
      </>}
    </section>
    {dialog?.type==='CREATE'&&<ObligationForm kind={kind} accounts={accounts} categories={categories} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='EDIT'&&<InstallmentEditor entry={dialog.entry} accounts={accounts} categories={categories} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='PAY'&&<SettlementForm entry={asEntry(dialog.entry)} account={accounts.find(item=>item.id===dialog.entry.accountId)!} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='PAYMENTS'&&<PaymentHistory entry={dialog.entry} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='HISTORY'&&<HistoryModal resource="LANCAMENTO" id={dialog.entry.id} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='DETAILS'&&<ObligationDetails id={dialog.id} actions={actions} onClose={()=>setDialog(null)}/>}
    {(dialog?.type==='FUTURE'||dialog?.type==='CANCEL_FUTURE')&&<RecurrenceEditor key={`${dialog.type}-${dialog.entry.id}`} entry={dialog.entry} accounts={accounts} categories={categories} cancel={dialog.type==='CANCEL_FUTURE'} onClose={()=>setDialog(null)}/>}
    {dialog?.type==='CANCEL'&&<Modal title="Cancelar esta parcela?" busy={cancel.isPending} onClose={()=>setDialog(null)}><p className="muted">“{dialog.entry.description}” ({dialog.entry.installmentNumber}/{dialog.entry.installmentCount}) permanecerá no histórico. {money(dialog.entry.paidAmount)} em liquidações, se existentes, serão estornados na conta. As outras parcelas serão preservadas. Esta ação não faz reembolso bancário.</p>{cancel.error&&<p className="form-error" role="alert">{errorMessage(cancel.error)}</p>}<div className="actions mt-6"><button className="btn secondary" disabled={cancel.isPending} onClick={()=>setDialog(null)}>Voltar</button><button className="btn danger" disabled={cancel.isPending} onClick={()=>cancel.mutate()}>{cancel.isPending?'Cancelando…':'Confirmar cancelamento'}</button></div></Modal>}
  </div>;
}
