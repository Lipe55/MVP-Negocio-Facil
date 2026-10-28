import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { api,errorMessage } from '../../api';
import { money,today } from '../../money';
import { Field,Modal,useNotify } from '../../components/ui';
import type { FinanceAccount,FinanceCategory } from '../types';
import { moneyInput,validAmount } from '../schemas';
import { useRefreshFinance } from '../shared';
import { installmentAmounts,validDate } from './rules';
import type { ObligationKind } from './types';

type Values={description:string;amount:string;categoryId:string;accountId:string;entryDate:string;dueDate:string;installments:number;occurrences:number;notes:string;settled:boolean;settlementDate:string};
export default function ObligationForm({kind,accounts,categories,onClose}:{kind:ObligationKind;accounts:FinanceAccount[];categories:FinanceCategory[];onClose:()=>void}) {
  const refresh=useRefreshFinance(),notify=useNotify();
  const [requestId]=useState(()=>crypto.randomUUID()),[mode,setMode]=useState<'ONCE'|'MONTHLY'>('ONCE');
  const {register,handleSubmit,watch,setError,formState:{errors,isSubmitting}}=useForm<Values>({shouldUnregister:true,defaultValues:{description:'',amount:'',categoryId:'',accountId:String(accounts.find(item=>item.primaryAccount)?.id??''),entryDate:today(),dueDate:today(),installments:1,occurrences:12,notes:'',settled:false,settlementDate:today()}});
  const recurring=mode==='MONTHLY',count=watch('installments'),settled=!!watch('settled')&&!recurring&&count===1;
  let preview:string[]=[];
  try{preview=installmentAmounts(watch('amount'),count);}catch{/* Display only a valid preview. */}
  const selectedAccount=accounts.find(item=>String(item.id)===watch('accountId'));
  return <Modal title={kind==='RECEITA'?'Nova conta a receber':'Nova conta a pagar'} onClose={onClose} busy={isSubmitting}>
    <form noValidate className="obligation-form" onSubmit={handleSubmit(async values=>{
      try {
        if(!recurring)installmentAmounts(values.amount,values.installments);
        const common={requestId,description:values.description.trim(),amount:moneyInput(values.amount),categoryId:Number(values.categoryId),accountId:Number(values.accountId),notes:values.notes};
        if(recurring)await api.post('/finance/recurrences',{...common,firstDueDate:values.dueDate,occurrences:values.occurrences});
        else await api.post('/finance/obligations',{...common,kind,entryDate:values.entryDate,dueDate:values.dueDate,installments:values.installments,settled,settlementDate:settled?values.settlementDate:null});
        await refresh();notify(recurring?'Recorrência mensal criada com quantidade limitada de ocorrências.':values.installments>1?`${values.installments} parcelas criadas, preservando o valor total.`:'Conta registrada.');onClose();
      }catch(error){setError('root',{message:errorMessage(error)});void refresh();}
    })}>
      {kind==='DESPESA'&&<Field label="Como deseja cadastrar?"><select value={mode} onChange={event=>setMode(event.target.value as typeof mode)} disabled={isSubmitting}><option value="ONCE">Despesa única ou parcelada</option><option value="MONTHLY">Despesa recorrente mensal</option></select></Field>}
      <Field label="Descrição" error={errors.description?.message}><input autoFocus maxLength={500} {...register('description',{validate:value=>!!value.trim()||'Informe a descrição.',maxLength:{value:500,message:'Use até 500 caracteres.'}})}/></Field>
      <div className="form-grid">
        <Field label={recurring?'Valor de cada ocorrência (R$)':'Valor total (R$)'} error={errors.amount?.message}><input inputMode="decimal" placeholder="Ex.: 1.200,00" {...register('amount',{validate:value=>validAmount(value)||'Informe um valor maior que zero, com até duas casas decimais.'})}/></Field>
        <Field label="Categoria" error={errors.categoryId?.message}><select {...register('categoryId',{required:'Selecione a categoria.'})}><option value="">Selecione</option>{categories.filter(item=>item.kind===kind).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
      </div>
      <Field label="Conta financeira" error={errors.accountId?.message}><select {...register('accountId',{required:'Selecione a conta.'})}><option value="">Selecione</option>{accounts.filter(item=>item.active).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
      <div className="form-grid">
        <Field label={recurring?'Primeiro vencimento':'Vencimento da primeira parcela'} error={errors.dueDate?.message}><input type="date" {...register('dueDate',{validate:value=>validDate(value)||'Informe um vencimento válido.'})}/></Field>
        {recurring?<Field label="Quantidade de ocorrências mensais" error={errors.occurrences?.message}><input type="number" min={1} max={60} step={1} {...register('occurrences',{valueAsNumber:true,validate:value=>Number.isInteger(value)&&value>=1&&value<=60||'Use de 1 a 60 ocorrências.'})}/></Field>:<Field label="Quantidade de parcelas" error={errors.installments?.message}><input type="number" min={1} max={120} step={1} {...register('installments',{valueAsNumber:true,validate:value=>Number.isInteger(value)&&value>=1&&value<=120||'Use de 1 a 120 parcelas.'})}/></Field>}
      </div>
      {recurring?<p className="finance-note">Cada mês terá uma obrigação independente, pelo valor informado. A geração é limitada às ocorrências escolhidas; não cria registros indefinidamente. Em meses curtos, utiliza o último dia, preservando o dia original nos meses seguintes.</p>:<>
        {preview.length>1&&<p className="obligation-preview" role="status">{preview.every(value=>value===preview[0])?`${preview.length} parcelas de ${money(preview[0])}`:`${preview.length} parcelas entre ${money(preview[preview.length-1])} e ${money(preview[0])}`}. Soma exata: <strong>{money(moneyInput(watch('amount')))}</strong>.</p>}
        <p className="finance-note">As parcelas vencem mensalmente e preservam todos os centavos do valor total. Valores pendentes não alteram o saldo disponível.</p>
        <Field label="Data de competência" error={errors.entryDate?.message}><input type="date" {...register('entryDate',{validate:value=>validDate(value)||'Informe uma competência válida.'})}/></Field>
        {count===1&&<label className="obligation-checkbox"><input type="checkbox" {...register('settled')}/>{kind==='RECEITA'?'Já recebi o valor integral':'Já paguei o valor integral'}</label>}
        {settled&&<Field label="Data efetiva" error={errors.settlementDate?.message}><input type="date" min={selectedAccount?.openingDate} max={today()} {...register('settlementDate',{validate:value=>!settled||validDate(value)&&value<=today()&&(!selectedAccount||value>=selectedAccount.openingDate)||'Informe uma data entre a abertura da conta e hoje.'})}/></Field>}
      </>}
      <Field label="Observações" error={errors.notes?.message}><textarea rows={2} maxLength={3000} {...register('notes',{maxLength:{value:3000,message:'Use até 3.000 caracteres.'}})}/></Field>
      {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
      <div className="actions"><button className="btn secondary" type="button" disabled={isSubmitting} onClick={onClose}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Registrando…':recurring?'Criar recorrência':'Registrar conta'}</button></div>
    </form>
  </Modal>;
}
