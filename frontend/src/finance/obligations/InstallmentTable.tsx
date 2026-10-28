import { Ban,Check,Coins,History,List,Pencil,Repeat2 } from 'lucide-react';
import { date,money,scaled,today } from '../../money';
import type { Installment } from './types';
import { paymentLabels } from './types';

export function PaymentTag({entry}:{entry:Installment}) {
  return <span className={`badge ${entry.paymentStatus==='VENCIDO'?'badge-recusado':entry.paymentStatus==='QUITADO'?'badge-aceito':entry.paymentStatus==='CANCELADO'?'badge-rascunho':'badge-aguardando'}`}><span aria-hidden="true"/>{paymentLabels[entry.paymentStatus]}{entry.overdue&&entry.paymentStatus!=='VENCIDO'?' · Em atraso':''}</span>;
}
export interface InstallmentActions {
  pay:(entry:Installment)=>void; edit:(entry:Installment)=>void; cancel:(entry:Installment)=>void;
  payments:(entry:Installment)=>void; history:(entry:Installment)=>void; details:(entry:Installment)=>void;
  future:(entry:Installment,cancel:boolean)=>void;
}
export default function InstallmentTable({entries,actions,showDetails=true}:{entries:Installment[];actions:InstallmentActions;showDetails?:boolean}) {
  return <div className="table-scroll"><table className="obligation-table"><thead><tr><th>Vencimento</th><th>Descrição</th><th>Conta / categoria</th><th>Valor</th><th>Liquidado</th><th>Saldo pendente</th><th>Situação</th><th>Ações</th></tr></thead><tbody>{entries.map(entry=>{
    const hasPending=entry.status!=='CANCELADO'&&scaled(entry.remainingAmount,2)>0n;
    const future=entry.recurrenceId!=null&&entry.status==='PENDENTE'&&!entry.hasSettlementHistory&&scaled(entry.paidAmount,2)===0n&&entry.dueDate!=null&&entry.dueDate>=today();
    const identity=`${entry.description}, parcela ${entry.installmentNumber}, vencimento ${entry.dueDate?date(entry.dueDate):'não definido'}`;
    return <tr key={entry.id}>
      <td className="nowrap"><span className={entry.overdue?'obligation-overdue':''}>{entry.dueDate?date(entry.dueDate):'Sem vencimento'}</span></td>
      <td><strong>{entry.description}</strong><small className="finance-secondary">Parcela {entry.installmentNumber}/{entry.installmentCount}{entry.recurrenceId?` · Recorrência, ocorrência ${entry.occurrenceNumber}`:''}</small></td>
      <td>{entry.accountName}<small className="finance-secondary">{entry.categoryName}</small></td>
      <td className="table-money">{money(entry.amount)}</td><td className="table-money">{money(entry.paidAmount)}</td><td className="table-money">{money(entry.remainingAmount)}</td>
      <td><PaymentTag entry={entry}/>{entry.overdue&&scaled(entry.paidAmount,2)>0n&&<small className="finance-secondary">Pagamento parcial em atraso</small>}</td>
      <td><div className="row-actions">
        {hasPending&&<button className="icon-btn" aria-label={`Registrar ${entry.kind==='RECEITA'?'recebimento':'pagamento'} de ${identity}`} onClick={()=>actions.pay(entry)}><Check size={17}/></button>}
        {entry.status!=='CANCELADO'&&<button className="icon-btn" aria-label={`Editar ${identity}`} onClick={()=>actions.edit(entry)}><Pencil size={17}/></button>}
        <button className="icon-btn" aria-label={`Histórico de liquidações de ${identity}`} onClick={()=>actions.payments(entry)}><Coins size={17}/></button>
        <button className="icon-btn" aria-label={`Histórico de alterações de ${identity}`} onClick={()=>actions.history(entry)}><History size={17}/></button>
        {showDetails&&<button className="icon-btn" aria-label={`Ver parcelas de ${identity}`} onClick={()=>actions.details(entry)}><List size={17}/></button>}
        {entry.status!=='CANCELADO'&&<button className="icon-btn" aria-label={`Cancelar ${identity}`} onClick={()=>actions.cancel(entry)}><Ban size={17}/></button>}
      </div>{entry.recurrenceId!=null&&<details className="obligation-row-recurrence"><summary><Repeat2 size={13} aria-hidden="true"/>Recorrência</summary><div>
        {future?<><button className="text-link" onClick={()=>actions.future(entry,false)}>Editar próximas ocorrências</button><button className="text-link obligation-danger-link" onClick={()=>actions.future(entry,true)}>Cancelar próximas ocorrências</button></>:<span className="muted">As ações futuras ficam disponíveis em uma ocorrência futura sem histórico de liquidações.</span>}
      </div></details>}</td>
    </tr>;
  })}</tbody></table></div>;
}
