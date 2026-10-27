import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../api';
import { money, decimal, today } from '../money';
import { Notifications } from '../components/ui';
import { moneyInput, entrySchema, transferSchema } from './schemas';
import { EntryForm } from './forms';
import FinanceAccounts from './Accounts';
import FinanceMovements from './Movements';
import type { FinanceAccount, FinanceCategory } from './types';
vi.mock('../api',()=>({api:{get:vi.fn(),post:vi.fn(),put:vi.fn()},errorMessage:()=> 'Falha ao salvar no servidor.'}));
const accounts:FinanceAccount[]=[{id:1,name:'Conta principal',type:'BANCO',primaryAccount:true,active:true,openingBalance:'0.00',openingDate:today(),balance:'100.00',version:0}];
const categories:FinanceCategory[]=[{id:2,name:'Serviços',kind:'RECEITA'},{id:3,name:'Materiais',kind:'DESPESA'}];
function setup(element:React.ReactNode){
  return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><Notifications>{element}</Notifications></MemoryRouter></QueryClientProvider>);
}
beforeEach(()=>{
  vi.resetAllMocks();
  Object.defineProperty(HTMLDialogElement.prototype,'showModal',{configurable:true,value:function(this:HTMLDialogElement){this.open=true;}});
  Object.defineProperty(HTMLDialogElement.prototype,'close',{configurable:true,value:function(this:HTMLDialogElement){this.open=false;}});
  vi.mocked(api.get).mockImplementation(async url=>({data:url==='/finance/accounts'?accounts:url==='/finance/categories'?categories:{content:[],totalElements:0,totalPages:0,page:0,size:20}}));
  vi.mocked(api.post).mockResolvedValue({data:{}});
});
it('normalizes Brazilian money and preserves cents without floating point',()=>{
  expect(moneyInput('1.234,56')).toBe('1234.56');expect(moneyInput('1234.56')).toBe('1234.56');expect(moneyInput('1.000')).toBe('1000.00');
  expect(moneyInput('-0,99',true)).toBe('-0.99');expect(money('-0.99')).toBe('-R$ 0,99');expect(decimal(-1n)).toBe('-0.01');
  expect(()=>moneyInput('1,234')).toThrow();expect(()=>moneyInput('1e3')).toThrow();expect(()=>moneyInput('-1')).toThrow();expect(()=>moneyInput('100000000000000.00')).toThrow();
});
it('rejects missing category, zero values and same-account transfer',()=>{
  const base={kind:'RECEITA',description:'Serviço',amount:'10',categoryId:'',accountId:'1',state:'PENDENTE',entryDate:today(),settlementDate:'',notes:''};
  expect(entrySchema.safeParse(base).success).toBe(false);expect(entrySchema.safeParse({...base,categoryId:'2',amount:'0'}).success).toBe(false);
  expect(transferSchema.safeParse({sourceAccountId:'1',targetAccountId:'1',amount:'10',transferDate:today(),description:''}).success).toBe(false);
});
it('records received revenue with decimal strings',async()=>{
  const close=vi.fn();setup(<EntryForm kind="RECEITA" accounts={accounts} categories={categories} onClose={close}/>);
  const user=userEvent.setup();await user.type(screen.getByLabelText('Valor (R$)'),'1.234,56');await user.type(screen.getByLabelText('Descrição'),'Consultoria');await user.selectOptions(screen.getByLabelText('Categoria'),'2');
  await user.click(screen.getByRole('button',{name:'Registrar lançamento'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries',expect.objectContaining({requestId:expect.any(String),kind:'RECEITA',amount:'1234.56',settled:true,accountId:1,categoryId:2,settlementDate:today()})));
  expect(close).toHaveBeenCalled();
});
it('sends pending expense without settlement date or cash movement',async()=>{
  setup(<EntryForm kind="DESPESA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.type(screen.getByLabelText('Valor (R$)'),'37,89');await user.type(screen.getByLabelText('Descrição'),'Material');await user.selectOptions(screen.getByLabelText('Categoria'),'3');await user.selectOptions(screen.getByLabelText('Situação'),'PENDENTE');
  expect(screen.queryByLabelText('Data efetiva do recebimento ou pagamento')).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Registrar lançamento'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries',expect.objectContaining({kind:'DESPESA',amount:'37.89',settled:false,settlementDate:null})));
});
it('keeps withdrawal separate from operational categories',async()=>{
  setup(<EntryForm kind="RETIRADA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  expect(screen.queryByLabelText('Categoria')).not.toBeInTheDocument();await user.type(screen.getByLabelText('Valor (R$)'),'30');await user.type(screen.getByLabelText('Descrição'),'Uso pessoal');await user.click(screen.getByRole('button',{name:'Registrar lançamento'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries',expect.objectContaining({kind:'RETIRADA',categoryId:null,amount:'30.00'})));
});
it('preserves entered values and exposes server failure',async()=>{
  vi.mocked(api.post).mockRejectedValue(new Error('network'));setup(<EntryForm kind="RETIRADA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);
  const user=userEvent.setup();await user.type(screen.getByLabelText('Valor (R$)'),'30');await user.type(screen.getByLabelText('Descrição'),'Uso pessoal');await user.click(screen.getByRole('button',{name:'Registrar lançamento'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Falha ao salvar no servidor.');expect(screen.getByLabelText('Descrição')).toHaveValue('Uso pessoal');expect(screen.getByRole('button',{name:'Registrar lançamento'})).toBeEnabled();
});
it('shows consolidated balances including an overdrawn account',async()=>{
  vi.mocked(api.get).mockImplementation(async url=>({data:url==='/finance/accounts'?[...accounts,{...accounts[0],id:2,name:'Conta secundária',primaryAccount:false,balance:'-10.01'}]:categories}));
  setup(<FinanceAccounts/>);expect(await screen.findByText('R$ 89,99')).toBeInTheDocument();expect(screen.getByText('-R$ 10,01')).toBeInTheDocument();
});
it('shows an empty movement state and exposes filters',async()=>{
  setup(<FinanceMovements/>);expect(await screen.findByText('Nenhum lançamento encontrado')).toBeInTheDocument();
  await act(async()=>{await userEvent.setup().selectOptions(screen.getByLabelText('Situação'),'PENDENTE');});
  await waitFor(()=>expect(api.get).toHaveBeenCalledWith('/finance/entries',expect.objectContaining({params:expect.objectContaining({status:'PENDENTE'})})));
});

it('prevents closing a financial form while a write is in flight',async()=>{
  let finish: (value:unknown)=>void = ()=>{};
  vi.mocked(api.post).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  const close=vi.fn();setup(<EntryForm kind="RETIRADA" accounts={accounts} categories={categories} onClose={close}/>);
  const user=userEvent.setup();await user.type(screen.getByLabelText('Valor (R$)'),'10');await user.type(screen.getByLabelText('Descrição'),'Retirada');
  await user.click(screen.getByRole('button',{name:'Registrar lançamento'}));
  expect(screen.getByRole('button',{name:'Fechar'})).toBeDisabled();
  expect(screen.getByRole('button',{name:'Voltar'})).toBeDisabled();
  await act(async()=>{finish({data:{}});});
  await waitFor(()=>expect(close).toHaveBeenCalledTimes(1));
});
