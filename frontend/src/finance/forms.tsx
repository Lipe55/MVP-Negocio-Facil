import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { api, errorMessage } from '../api';
import { today } from '../money';
import { Field, Modal, useNotify } from '../components/ui';
import { accountSchema, entrySchema, transferSchema, moneyInput, type EntryFormInput, type AccountFormInput, type TransferFormInput } from './schemas';
import type { FinanceAccount, FinanceCategory, FinanceEntry, FinanceKind } from './types';
import { kindLabels, useRefreshFinance } from './shared';

export function EntryForm({kind,entry,accounts,categories,onClose}:{kind:FinanceKind;entry?:FinanceEntry;accounts:FinanceAccount[];categories:FinanceCategory[];onClose:()=>void}) {
  const refresh=useRefreshFinance(); const notify=useNotify(); const [requestId]=useState(()=>crypto.randomUUID());
  const form=useForm<EntryFormInput>({resolver:zodResolver(entrySchema),defaultValues:{
    kind:entry?.kind??kind, description:entry?.description??'', amount:entry?.amount??'',
    accountId:String(entry?.accountId??accounts.find(a=>a.primaryAccount)?.id??''), categoryId:entry?.categoryId?String(entry.categoryId):'',
    state:entry?.status==='PENDENTE'?'PENDENTE':'LIQUIDADO', entryDate:entry?.entryDate??today(),
    settlementDate:entry?.settlementDate??today(),notes:entry?.notes??'',
  }});
  const {register,handleSubmit,watch,setError,formState:{errors,isSubmitting}}=form;
  const selectedKind=watch('kind'), settled=watch('state')==='LIQUIDADO';
  const selectedAccount=accounts.find(a=>String(a.id)===watch('accountId'));
  return <Modal title={entry?'Editar lançamento':`Nova ${kindLabels[kind].toLowerCase()}`} onClose={onClose} busy={isSubmitting}>
    <form noValidate onSubmit={handleSubmit(async values=>{
      try {
        const body={requestId,kind:values.kind,description:values.description,amount:moneyInput(values.amount),
          accountId:Number(values.accountId),categoryId:values.kind==='RETIRADA'?null:Number(values.categoryId),
          settled:values.state==='LIQUIDADO',entryDate:values.entryDate,
          settlementDate:values.state==='LIQUIDADO'?values.settlementDate:null,notes:values.notes,version:entry?.version};
        if(entry) await api.put(`/finance/entries/${entry.id}`,body); else await api.post('/finance/entries',body);
        await refresh();notify(entry?'Lançamento atualizado.':'Lançamento registrado.');onClose();
      } catch(e) { setError('root',{message:errorMessage(e)}); void refresh(); }
    })}>
      <Field label="Valor (R$)" error={errors.amount?.message}><input autoFocus inputMode="decimal" placeholder="Ex.: 150,00" {...register('amount')}/></Field>
      <Field label="Descrição" error={errors.description?.message}><input {...register('description')}/></Field>
      {selectedKind!=='RETIRADA'&&<Field label="Categoria" error={errors.categoryId?.message}>
        <select {...register('categoryId')}><option value="">Selecione</option>{categories.filter(c=>c.kind===selectedKind).map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select>
      </Field>}
      {selectedKind==='RETIRADA'&&<p className="finance-note">Retirada para uso pessoal. Reduz o saldo e permanece separada das despesas operacionais.</p>}
      <Field label="Situação"><select {...register('state')}><option value="LIQUIDADO">{selectedKind==='RECEITA'?'Recebido':'Pago / retirado'}</option><option value="PENDENTE">Pendente — sem efeito no saldo</option></select></Field>
      <details className="finance-additional" open={!!entry}>
        <summary>Conta, datas e observações</summary>
        <Field label="Conta financeira" error={errors.accountId?.message}><select {...register('accountId')}><option value="">Selecione</option>{accounts.filter(a=>a.active||a.id===entry?.accountId).map(a=><option key={a.id} value={a.id} disabled={!a.active}>{a.name}{!a.active?' (inativa)':''}</option>)}</select></Field>
        <Field label="Data de competência" error={errors.entryDate?.message}><input type="date" {...register('entryDate')}/></Field>
        <Field label="Observações" error={errors.notes?.message}><textarea rows={3} {...register('notes')}/></Field>
      </details>
      {settled&&<Field label="Data efetiva do recebimento ou pagamento" error={errors.settlementDate?.message}><input type="date" min={selectedAccount?.openingDate} max={today()} {...register('settlementDate')}/></Field>}
      {entry?.status==='LIQUIDADO'&&<p className="finance-note">A edição estorna o movimento anterior e registra os novos valores, preservando o histórico.</p>}
      {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
      <div className="actions"><button type="button" className="btn secondary" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Salvando…':entry?'Salvar alterações':'Registrar lançamento'}</button></div>
    </form>
  </Modal>;
}

export function AccountForm({account,onClose}:{account?:FinanceAccount;onClose:()=>void}) {
  const refresh=useRefreshFinance(); const notify=useNotify();
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<AccountFormInput>({resolver:zodResolver(accountSchema),defaultValues:{name:account?.name??'',type:account?.type??'BANCO',openingBalance:account?.openingBalance??'0,00',openingDate:account?.openingDate??today(),active:account?.active??true}});
  return <Modal title={account?'Editar conta':'Nova conta financeira'} onClose={onClose} busy={isSubmitting}><form noValidate onSubmit={handleSubmit(async values=>{
    try {
      const body={...values,openingBalance:moneyInput(values.openingBalance,true),version:account?.version};
      if(account) await api.put(`/finance/accounts/${account.id}`,body); else await api.post('/finance/accounts',body);
      await refresh();notify('Conta salva.');onClose();
    } catch(e) {setError('root',{message:errorMessage(e)});void refresh();}
  })}>
    <Field label="Nome da conta" error={errors.name?.message}><input autoFocus {...register('name')}/></Field>
    <Field label="Tipo"><select {...register('type')}><option value="BANCO">Conta bancária</option><option value="DINHEIRO">Dinheiro em espécie</option></select></Field>
    <div className="form-grid">
      <Field label="Saldo inicial (R$)" error={errors.openingBalance?.message}><input inputMode="decimal" {...register('openingBalance')}/></Field>
      <Field label="Data de referência" error={errors.openingDate?.message}><input type="date" max={today()} {...register('openingDate')}/></Field>
    </div>
    <p className="finance-note">O saldo inicial não é receita. Saldo e referência só podem ser alterados antes de existirem movimentos efetivos.</p>
    {account&&!account.primaryAccount&&<label className="checkbox finance-checkbox"><input type="checkbox" {...register('active')}/>Conta ativa</label>}
    {account&&!account.primaryAccount&&<p className="input-hint">Para desativar, a conta deve ter saldo zero e nenhum lançamento pendente. Reative-a para corrigir operações históricas.</p>}
    {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
    <div className="actions"><button type="button" className="btn secondary" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Salvando…':'Salvar conta'}</button></div>
  </form></Modal>;
}

export function TransferForm({accounts,onClose}:{accounts:FinanceAccount[];onClose:()=>void}) {
  const refresh=useRefreshFinance();const notify=useNotify();const [requestId]=useState(()=>crypto.randomUUID());
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<TransferFormInput>({resolver:zodResolver(transferSchema),defaultValues:{sourceAccountId:String(accounts.find(a=>a.primaryAccount)?.id??''),targetAccountId:'',amount:'',transferDate:today(),description:''}});
  return <Modal title="Transferir entre contas" onClose={onClose} busy={isSubmitting}><form noValidate onSubmit={handleSubmit(async values=>{
    try {await api.post('/finance/transfers',{...values,requestId,sourceAccountId:Number(values.sourceAccountId),targetAccountId:Number(values.targetAccountId),amount:moneyInput(values.amount)});await refresh();notify('Transferência registrada nas duas contas.');onClose();}
    catch(e){setError('root',{message:errorMessage(e)});}
  })}>
    <Field label="Conta de origem" error={errors.sourceAccountId?.message}><select {...register('sourceAccountId')}><option value="">Selecione</option>{accounts.filter(a=>a.active).map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select></Field>
    <Field label="Conta de destino" error={errors.targetAccountId?.message}><select {...register('targetAccountId')}><option value="">Selecione</option>{accounts.filter(a=>a.active).map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select></Field>
    <Field label="Valor da transferência (R$)" error={errors.amount?.message}><input inputMode="decimal" {...register('amount')}/></Field>
    <Field label="Data da transferência" error={errors.transferDate?.message}><input type="date" max={today()} {...register('transferDate')}/></Field>
    <Field label="Descrição (opcional)" error={errors.description?.message}><input {...register('description')}/></Field>
    <p className="finance-note">Transferências não são receitas ou despesas e não alteram o saldo consolidado. Não há tarifa automática.</p>
    {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
    <div className="actions"><button type="button" className="btn secondary" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Transferindo…':'Confirmar transferência'}</button></div>
  </form></Modal>;
}

export function CategoryForm({onClose}:{onClose:()=>void}) {
  const refresh=useRefreshFinance();const notify=useNotify();
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<{name:string;kind:'RECEITA'|'DESPESA'}>({defaultValues:{name:'',kind:'DESPESA'}});
  return <Modal title="Nova categoria" onClose={onClose} busy={isSubmitting}><form noValidate onSubmit={handleSubmit(async values=>{
    try{await api.post('/finance/categories',{name:values.name.trim(),kind:values.kind});await refresh();notify('Categoria criada.');onClose();}catch(e){setError('root',{message:errorMessage(e)});}
  })}>
    <Field label="Nome da categoria" error={errors.name?.message}><input autoFocus {...register('name',{validate:v=>!!v.trim()||'Informe o nome.',maxLength:{value:100,message:'Máximo de 100 caracteres.'}})}/></Field>
    <Field label="Natureza"><select {...register('kind')}><option value="RECEITA">Receita</option><option value="DESPESA">Despesa operacional</option></select></Field>
    {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
    <div className="actions"><button type="button" className="btn secondary" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className="btn" disabled={isSubmitting}>Salvar categoria</button></div>
  </form></Modal>;
}

export function SettlementForm({entry,account,onClose}:{entry:FinanceEntry;account:FinanceAccount;onClose:()=>void}) {
  const refresh=useRefreshFinance();const notify=useNotify();const [requestId]=useState(()=>crypto.randomUUID());
  const {register,handleSubmit,setError,formState:{errors,isSubmitting}}=useForm<{settlementDate:string}>({defaultValues:{settlementDate:today()}});
  return <Modal title={entry.kind==='RECEITA'?'Registrar recebimento':'Registrar pagamento ou retirada'} onClose={onClose} busy={isSubmitting}>
    <p className="finance-note">A liquidação integral de “{entry.description}” movimentará a conta {entry.accountName}.</p>
    <form onSubmit={handleSubmit(async values=>{
      try{await api.post(`/finance/entries/${entry.id}/settle`,{...values,requestId,version:entry.version});await refresh();notify('Liquidação registrada.');onClose();}
      catch(e){setError('root',{message:errorMessage(e)});void refresh();}
    })}>
      <Field label="Data efetiva" error={errors.settlementDate?.message}><input type="date" min={account.openingDate} max={today()} {...register('settlementDate',{required:'Informe a data.'})}/></Field>
      {errors.root&&<p className="form-error" role="alert">{errors.root.message}</p>}
      <div className="actions"><button className="btn secondary" type="button" onClick={onClose} disabled={isSubmitting}>Voltar</button><button className="btn" disabled={isSubmitting}>{isSubmitting?'Registrando…':'Confirmar liquidação'}</button></div>
    </form>
  </Modal>;
}
