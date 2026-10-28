import { useForm } from 'react-hook-form';
import { api,errorMessage } from '../../api';
import { money,scaled } from '../../money';
import { Field,Modal,useNotify } from '../../components/ui';
import { moneyInput,validAmount } from '../schemas';
import { useRefreshFinance } from '../shared';
import type { FinanceAccount,FinanceCategory } from '../types';
import { validDate } from './rules';
import type { Installment } from './types';

type Values={description:string;amount:string;accountId:string;categoryId:string;entryDate:string;dueDate:string;notes:string};
export default function InstallmentEditor({entry,accounts,categories,onClose}:{entry:Installment;accounts:FinanceAccount[];categories:FinanceCategory[];onClose:()=>void}) {
  const refresh=useRefreshFinance(),notify=useNotify(),hasPayments=entry.hasSettlementHistory===true||scaled(entry.paidAmount,2)>0n;
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<Values>({defaultValues:{description:entry.description,amount:entry.amount,accountId:String(entry.accountId),categoryId:String(entry.categoryId),entryDate:entry.entryDate,dueDate:entry.dueDate??'',notes:entry.notes??''}});
  return <Modal title={entry.recurrenceId?'Editar esta ocorrência':'Editar parcela'} onClose={onClose} busy={isSubmitting}>
    <p className="finance-note">Parcela {entry.installmentNumber} de {entry.installmentCount}. Esta edição afeta somente este registro.{entry.recurrenceId?' As demais ocorrências da recorrência serão preservadas.':''}</p>
    {hasPayments&&<p className="finance-note">Valor liquidado atual: {money(entry.paidAmount)}. Já existe histórico de liquidações, mesmo que estornadas. Valor, conta e categoria permanecem preservados. Para corrigir esses campos, cancele o registro e cadastre uma nova obrigação; o histórico anterior será mantido.</p>}
    <form noValidate onSubmit={handleSubmit(async values=>{
      try{
        await api.put(`/finance/installments/${entry.id}`,{version:entry.version,description:values.description.trim(),amount:hasPayments?entry.amount:moneyInput(values.amount),accountId:hasPayments?entry.accountId:Number(values.accountId),categoryId:hasPayments?entry.categoryId:Number(values.categoryId),entryDate:values.entryDate,dueDate:values.dueDate||null,notes:values.notes});
        await refresh();notify('Parcela atualizada. As demais parcelas foram preservadas.');onClose();
      }catch(error){setError('root',{message:errorMessage(error)});void refresh();}
    })}>
      <Field label="Descrição" error={errors.description?.message}><input maxLength={500} {...register('description',{validate:value=>!!value.trim()||'Informe a descrição.'})}/></Field>
      <Field label="Valor da parcela (R$)" error={errors.amount?.message}><input inputMode="decimal" readOnly={hasPayments} {...register('amount',{validate:value=>hasPayments||validAmount(value)||'Informe um valor maior que zero.'})}/></Field>
      <div className="form-grid"><Field label="Conta financeira" error={errors.accountId?.message}><select disabled={hasPayments} {...register('accountId',{required:!hasPayments&&'Selecione a conta.'})}>{accounts.filter(item=>item.active||item.id===entry.accountId).map(item=><option key={item.id} value={item.id}>{item.name}{!item.active?' (inativa)':''}</option>)}</select></Field><Field label="Categoria" error={errors.categoryId?.message}><select disabled={hasPayments} {...register('categoryId',{required:!hasPayments&&'Selecione a categoria.'})}>{categories.filter(item=>item.kind===entry.kind).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></div>
      <div className="form-grid"><Field label="Vencimento" error={errors.dueDate?.message}><input type="date" {...register('dueDate',{validate:value=>!value||validDate(value)||'Informe um vencimento válido.'})}/></Field><Field label="Data de competência" error={errors.entryDate?.message}><input type="date" {...register('entryDate',{validate:value=>validDate(value)||'Informe uma competência válida.'})}/></Field></div>
      <Field label="Observações"><textarea rows={2} maxLength={3000} {...register('notes')}/></Field>
      {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
      <div className="actions"><button className="btn secondary" type="button" disabled={isSubmitting} onClick={onClose}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Salvando…':'Salvar parcela'}</button></div>
    </form>
  </Modal>;
}
