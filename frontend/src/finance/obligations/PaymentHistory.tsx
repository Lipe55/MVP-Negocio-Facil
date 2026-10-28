import { useState } from 'react';
import { useMutation,useQuery } from '@tanstack/react-query';
import { api,errorMessage } from '../../api';
import { date,dateTime,money } from '../../money';
import { Empty,ErrorState,Field,Loading,Modal,useNotify } from '../../components/ui';
import { useRefreshFinance } from '../shared';
import type { Installment,Settlement } from './types';

export default function PaymentHistory({entry,onClose}:{entry:Installment;onClose:()=>void}) {
  const refresh=useRefreshFinance(),notify=useNotify();
  const [reversing,setReversing]=useState<Settlement|null>(null),[reason,setReason]=useState('Correção de registro');
  const query=useQuery({queryKey:['finance','settlements',entry.id],queryFn:async({signal})=>(await api.get<Settlement[]>(`/finance/entries/${entry.id}/settlements`,{signal})).data});
  const current=useQuery({queryKey:['finance','installment',entry.id],queryFn:async({signal})=>(await api.get<Installment>(`/finance/installments/${entry.id}`,{signal})).data});
  const reverse=useMutation({mutationFn:async()=>{
    if(!reversing)return;
    await api.post(`/finance/entries/${entry.id}/settlements/${reversing.id}/reverse`,{version:current.data?.version??entry.version,reason:reason.trim()});
  },onSuccess:async()=>{await refresh();setReversing(null);notify('Liquidação estornada. O histórico e os movimentos foram preservados.');},onError:()=>{void refresh();}});
  const active=current.data??entry;
  return <Modal title="Histórico de pagamentos e recebimentos" onClose={onClose} busy={reverse.isPending}>
    <div className="obligation-payment-history">
      <h3>{entry.description}</h3>
      <p className="muted">Parcela {entry.installmentNumber} de {entry.installmentCount} · {entry.accountName}</p>
      <div className="obligation-totals"><span>Valor da parcela<strong>{money(active.amount)}</strong></span><span>Liquidado<strong>{money(active.paidAmount)}</strong></span><span>Saldo pendente<strong>{money(active.remainingAmount)}</strong></span></div>
      {query.isLoading||current.isLoading?<Loading/>:query.error||current.error?<ErrorState error={query.error||current.error} retry={()=>{void query.refetch();void current.refetch();}}/>:!query.data?.length?<Empty title="Nenhuma liquidação registrada">O valor pendente ainda não alterou o saldo da conta.</Empty>:<ol className="obligation-payment-list">{query.data.map(item=><li key={item.id}>
        <div><strong>{money(item.amount)}</strong><span>{date(item.settlementDate)}</span></div>
        <p className="muted">Registrado em {dateTime(item.createdAt)}</p>
        {item.reversedAt?<p className="obligation-reversed">Estornado em {dateTime(item.reversedAt)} · registro preservado</p>:active.status!=='CANCELADO'&&<button className="btn secondary small" disabled={reverse.isPending} onClick={()=>{setReversing(item);setReason('Correção de registro');reverse.reset();}}>Estornar liquidação #{item.id}</button>}
      </li>)}</ol>}
      {reversing&&<section className="obligation-reversal" aria-label="Confirmar estorno da liquidação">
        <h3>Estornar {money(reversing.amount)}?</h3>
        <p>O saldo da conta será corrigido e a parcela poderá voltar a ter valor pendente. Esta ação corrige o controle financeiro; não executa reembolso ou transferência bancária.</p>
        <Field label="Motivo do estorno"><textarea maxLength={300} rows={2} value={reason} onChange={event=>setReason(event.target.value)} disabled={reverse.isPending}/></Field>
        <div className="actions"><button className="btn secondary small" disabled={reverse.isPending} onClick={()=>setReversing(null)}>Manter liquidação</button><button className="btn danger small" disabled={reverse.isPending||!reason.trim()||!!current.error||current.isFetching} onClick={()=>reverse.mutate()}>{reverse.isPending?'Estornando…':'Confirmar estorno'}</button></div>
      </section>}
      {reverse.error&&<p className="form-error" role="alert">{errorMessage(reverse.error)}</p>}
      <div className="actions"><button className="btn secondary" disabled={reverse.isPending} onClick={onClose}>Fechar histórico</button></div>
    </div>
  </Modal>;
}
