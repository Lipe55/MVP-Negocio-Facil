import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery } from '@tanstack/react-query';
import { api,errorMessage } from '../../api';
import { date,money,today } from '../../money';
import { ErrorState,Field,Loading,Modal,useNotify } from '../../components/ui';
import { moneyInput,validAmount } from '../schemas';
import { useRefreshFinance } from '../shared';
import type { FinanceAccount,FinanceCategory } from '../types';
import type { FutureResult,Installment,Recurrence } from './types';

type Values={description:string;amount:string;categoryId:string;accountId:string;dueDay:number;notes:string};
function FutureForm({entry,recurrence,accounts,categories,cancel,onClose,onBusy}:{entry:Installment;recurrence:Recurrence;accounts:FinanceAccount[];categories:FinanceCategory[];cancel:boolean;onClose:()=>void;onBusy:(value:boolean)=>void}) {
  const refresh=useRefreshFinance(),notify=useNotify();
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<Values>({defaultValues:{description:entry.description,amount:entry.amount,categoryId:String(entry.categoryId),accountId:String(entry.accountId),dueDay:Number(entry.dueDate?.slice(8,10)??today().slice(8,10)),notes:entry.notes??''}});
  return <form noValidate onSubmit={handleSubmit(async values=>{
    onBusy(true);
    try{
      const base={version:recurrence.version,fromObligationId:entry.obligationId};
      const result=cancel?(await api.post<FutureResult>(`/finance/recurrences/${recurrence.id}/cancel-future`,base)).data:(await api.put<FutureResult>(`/finance/recurrences/${recurrence.id}/future`,{...base,description:values.description.trim(),amount:moneyInput(values.amount),categoryId:Number(values.categoryId),accountId:Number(values.accountId),dueDay:values.dueDay,notes:values.notes})).data;
      await refresh();notify(`${result.updatedCount} ocorrência(s) ${cancel?'cancelada(s)':'atualizada(s)'}. ${result.skippedCount} preservada(s).`);onClose();
    }catch(error){setError('root',{message:errorMessage(error)});void refresh();}finally{onBusy(false);}
  })}>
    <p className="finance-note">A partir de “{entry.description}”, vencimento {entry.dueDate?date(entry.dueDate):'não definido'}. Somente ocorrências futuras sem histórico de liquidações serão {cancel?'canceladas':'alteradas'}. Registros passados, parciais, quitados, cancelados ou com liquidações estornadas serão preservados.</p>
    {cancel?<p className="obligation-reversal-note">A recorrência de {money(recurrence.amount)} possui {recurrence.occurrences} ocorrência(s) previstas. O cancelamento é lógico e não apaga o histórico. Nenhum pagamento já registrado será estornado por esta ação.</p>:<>
      <Field label="Descrição das próximas ocorrências" error={errors.description?.message}><input maxLength={500} {...register('description',{validate:value=>!!value.trim()||'Informe a descrição.'})}/></Field>
      <Field label="Valor de cada ocorrência (R$)" error={errors.amount?.message}><input inputMode="decimal" {...register('amount',{validate:value=>validAmount(value)||'Informe um valor maior que zero.'})}/></Field>
      <div className="form-grid"><Field label="Conta financeira" error={errors.accountId?.message}><select {...register('accountId',{required:'Selecione a conta.'})}>{accounts.filter(item=>item.active).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field><Field label="Categoria" error={errors.categoryId?.message}><select {...register('categoryId',{required:'Selecione a categoria.'})}>{categories.filter(item=>item.kind==='DESPESA').map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></div>
      <Field label="Dia do vencimento nas próximas ocorrências" error={errors.dueDay?.message}><input type="number" min={1} max={31} step={1} {...register('dueDay',{valueAsNumber:true,validate:value=>Number.isInteger(value)&&value>=1&&value<=31||'Use um dia de 1 a 31.'})}/></Field>
      <p className="finance-note">Em meses com menos dias, será usado o último dia do mês. Esta alteração não cria ocorrências adicionais.</p>
      <Field label="Observações"><textarea rows={2} maxLength={3000} {...register('notes')}/></Field>
    </>}
    {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
    <div className="actions"><button type="button" className="btn secondary" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className={`btn ${cancel?'danger':''}`} disabled={isSubmitting}>{isSubmitting?'Processando…':cancel?'Confirmar cancelamento das próximas':'Salvar próximas ocorrências'}</button></div>
  </form>;
}
export default function RecurrenceEditor({entry,accounts,categories,cancel=false,onClose}:{entry:Installment;accounts:FinanceAccount[];categories:FinanceCategory[];cancel?:boolean;onClose:()=>void}) {
  const [busy,setBusy]=useState(false);
  const query=useQuery({queryKey:['finance','recurrence',entry.recurrenceId],queryFn:async({signal})=>(await api.get<Recurrence>(`/finance/recurrences/${entry.recurrenceId}`,{signal})).data});
  return <Modal title={cancel?'Cancelar próximas ocorrências?':'Editar próximas ocorrências'} onClose={onClose} busy={busy}>
    {query.isLoading?<Loading/>:query.error?<ErrorState error={query.error} retry={()=>void query.refetch()}/>:query.data&&<FutureForm entry={entry} recurrence={query.data} accounts={accounts} categories={categories} cancel={cancel} onClose={onClose} onBusy={setBusy}/>}
  </Modal>;
}
