import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ArrowLeftRight, History, Landmark, Pencil, Plus, Wallet } from 'lucide-react';
import { api, errorMessage } from '../api';
import { date, money, scaled } from '../money';
import { Confirm, Empty, ErrorState, Loading, PageHeader, Pagination, useNotify } from '../components/ui';
import { AccountForm, TransferForm } from './forms';
import { FinanceTabs, HistoryModal, useFinanceData, useRefreshFinance } from './shared';
import type { FinanceAccount, FinanceStatement } from './types';
function cents(value:string) {return value.startsWith('-')?-scaled(value.slice(1),2):scaled(value,2);}
const movementLabels:Record<string,string>={RECEBIMENTO:'Recebimento',PAGAMENTO:'Despesa paga',RETIRADA:'Retirada pessoal',TRANSFERENCIA_ENTRADA:'Transferência recebida',TRANSFERENCIA_SAIDA:'Transferência enviada',ESTORNO:'Estorno'};
function Statement({account}:{account:FinanceAccount}) {
  const [from,setFrom]=useState(account.openingDate),[to,setTo]=useState(''),[page,setPage]=useState(0);
  const [history,setHistory]=useState<{resource:'LANCAMENTO'|'TRANSFERENCIA';id:number}|null>(null);
  const [cancel,setCancel]=useState<{id:number;version:number}|null>(null);
  const refresh=useRefreshFinance(),notify=useNotify();
  const query=useQuery({queryKey:['finance','statement',account.id,from,to,page],queryFn:async()=>
    (await api.get<FinanceStatement>(`/finance/accounts/${account.id}/statement`,{params:{from:from||undefined,to:to||undefined,page}})).data});
  const reversal=useMutation({mutationFn:async()=>{if(cancel)await api.post(`/finance/transfers/${cancel.id}/cancel`,{version:cancel.version});},onSuccess:async()=>{await refresh();setCancel(null);notify('Transferência estornada nas duas contas.');},onError:e=>notify(errorMessage(e),true)});
  return <section className="panel" aria-label={`Extrato de ${account.name}`}>
    <div className="panel-head"><div><h2>Extrato · {account.name}</h2><p>Saldo inicial de {money(account.openingBalance)} em {date(account.openingDate)}. Somente movimentos efetivos.</p></div></div>
    <div className="finance-statement-filters">
      <label className="field"><span>Extrato de</span><input type="date" min={account.openingDate} value={from} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></label>
      <label className="field"><span>Até</span><input type="date" value={to} onChange={e=>{setTo(e.target.value);setPage(0);}}/></label>
    </div>
    {query.isLoading?<Loading/>:query.error?<ErrorState error={query.error} retry={()=>void query.refetch()}/>:query.data&&<>
      <div className="finance-statement-summary"><span>Saldo anterior <strong>{money(query.data.balanceBeforePeriod)}</strong></span><span>Variação no período <strong>{money(query.data.periodChange)}</strong></span><span>Saldo ao fim do período <strong>{money(query.data.balanceAtEnd)}</strong></span></div>
      {!query.data.movements.content.length?<Empty title="Nenhum movimento efetivo no período">Lançamentos pendentes não aparecem no extrato.</Empty>:<div className="table-scroll"><table><thead><tr><th>Data efetiva</th><th>Descrição</th><th>Movimento</th><th>Valor</th><th>Saldo após movimento</th><th>Histórico</th></tr></thead><tbody>
        {query.data.movements.content.map(m=><tr key={m.id}>
          <td className="nowrap">{date(m.effectiveDate)}</td><td>{m.description}{m.reversed&&<small className="finance-secondary">Estornado — registro preservado</small>}</td><td>{movementLabels[m.kind]}</td>
          <td className={`table-money ${m.amount.startsWith('-')?'finance-outflow':'finance-income'}`}>{money(m.amount)}</td><td className="table-money">{money(m.runningBalance)}</td>
          <td><div className="row-actions">{m.entryId!=null&&<button className="icon-btn" aria-label={`Histórico de ${m.description}`} onClick={()=>setHistory({resource:'LANCAMENTO',id:m.entryId!})}><History size={17}/></button>}
            {m.transferId!=null&&<button className="icon-btn" aria-label="Histórico da transferência" onClick={()=>setHistory({resource:'TRANSFERENCIA',id:m.transferId!})}><History size={17}/></button>}
            {m.transferId!=null&&!m.reversed&&m.kind!=='ESTORNO'&&m.transferVersion!=null&&<button className="btn secondary small" onClick={()=>setCancel({id:m.transferId!,version:m.transferVersion!})}>Estornar</button>}
          </div></td>
        </tr>)}
      </tbody></table></div>}
      <Pagination page={page} totalPages={query.data.movements.totalPages} total={query.data.movements.totalElements} onChange={setPage}/>
    </>}
    {history&&<HistoryModal {...history} onClose={()=>setHistory(null)}/>}
    {cancel&&<Confirm title="Estornar transferência?" description="Serão registrados estornos nas duas contas. O saldo consolidado é preservado e o histórico permanece disponível." busy={reversal.isPending} danger onClose={()=>setCancel(null)} onConfirm={()=>reversal.mutate()}/>}
  </section>;
}
export default function FinanceAccounts() {
  const query=useFinanceData();const [editor,setEditor]=useState<FinanceAccount|null|undefined>();
  const [transfer,setTransfer]=useState(false),[selected,setSelected]=useState<number|null>(null),[history,setHistory]=useState<number|null>(null);
  if(query.isLoading)return <Loading/>;
  if(query.error)return <ErrorState error={query.error} retry={()=>void query.refetch()}/>;
  const {accounts}=query.data!;
  const total=accounts.reduce((sum,a)=>sum+cents(a.balance),0n);
  const selectedAccount=accounts.find(a=>a.id===selected);
  return <>
    <PageHeader title="Contas e saldos" subtitle="O dinheiro disponível em cada conta, sem incluir pendências." action={<button className="btn" onClick={()=>setEditor(null)}><Plus size={17}/>Nova conta</button>}/>
    <FinanceTabs/>
    <section className="panel finance-balance"><div><span className="eyebrow">SALDO DISPONÍVEL CONSOLIDADO</span><strong>{money(total)}</strong><p>Saldo inicial mais entradas e saídas efetivas. Retiradas reduzem o saldo; transferências internas não alteram o total.</p></div>
      <button className="btn secondary" disabled={accounts.filter(a=>a.active).length<2} onClick={()=>setTransfer(true)}><ArrowLeftRight size={17}/>Transferir entre contas</button>
    </section>
    <div className="finance-account-grid">{accounts.map(a=><section className={`panel finance-account ${!a.active?'finance-account-inactive':''}`} key={a.id}>
      <div className="finance-account-title">{a.type==='BANCO'?<Landmark size={21}/>:<Wallet size={21}/>}<h2>{a.name}</h2>{a.primaryAccount&&<span className="subtle-label">Principal</span>}{!a.active&&<span className="subtle-label">Inativa</span>}</div>
      <strong className={a.balance.startsWith('-')?'finance-outflow':''}>{money(a.balance)}</strong>
      <p className="muted">{a.type==='BANCO'?'Conta bancária':'Dinheiro em espécie'} · Referência {date(a.openingDate)}</p>
      <div className="actions"><button className="btn secondary small" onClick={()=>setSelected(a.id)}>Ver extrato</button><button className="icon-btn" aria-label={`Editar conta ${a.name}`} onClick={()=>setEditor(a)}><Pencil size={17}/></button><button className="icon-btn" aria-label={`Histórico da conta ${a.name}`} onClick={()=>setHistory(a.id)}><History size={17}/></button></div>
    </section>)}</div>
    {selectedAccount&&<Statement key={`${selectedAccount.id}-${selectedAccount.openingDate}`} account={selectedAccount}/>}
    {editor!==undefined&&<AccountForm account={editor??undefined} onClose={()=>setEditor(undefined)}/>}
    {transfer&&<TransferForm accounts={accounts} onClose={()=>setTransfer(false)}/>}
    {history!=null&&<HistoryModal resource="CONTA" id={history} onClose={()=>setHistory(null)}/>}
  </>;
}
