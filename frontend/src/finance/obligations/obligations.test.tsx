import { act,fireEvent,render,screen,waitFor,within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach,expect,it,vi } from 'vitest';
import { api } from '../../api';
import { scaled,today } from '../../money';
import { Notifications } from '../../components/ui';
import { EntryForm,SettlementForm } from '../forms';
import FinanceMovements from '../Movements';
import type { FinanceAccount,FinanceCategory } from '../types';
import ObligationForm from './ObligationForm';
import InstallmentEditor from './InstallmentEditor';
import Obligations from './Obligations';
import ObligationDetails from './ObligationDetails';
import PaymentHistory from './PaymentHistory';
import RecurrenceEditor from './RecurrenceEditor';
import { installmentAmounts,validDate } from './rules';
import { asEntry } from './types';
import { HistoryModal } from '../shared';
import type { Installment,ObligationDetail,Recurrence,Settlement } from './types';
import type { InstallmentActions } from './InstallmentTable';

vi.mock('../../api',()=>({api:{get:vi.fn(),post:vi.fn(),put:vi.fn()},errorMessage:()=> 'Não foi possível concluir no servidor.'}));
const accounts:FinanceAccount[]=[{id:1,name:'Conta principal',type:'BANCO',primaryAccount:true,active:true,openingBalance:'0.00',openingDate:'2026-01-01',balance:'0.00',version:0}];
const categories:FinanceCategory[]=[{id:2,name:'Serviços',kind:'RECEITA'},{id:3,name:'Materiais',kind:'DESPESA'}];
const installment:Installment={id:10,obligationId:20,kind:'RECEITA',description:'Cobrança de teste',amount:'1500.00',paidAmount:'0.00',remainingAmount:'1500.00',dueDate:today(),entryDate:today(),accountId:1,accountName:'Conta principal',categoryId:2,categoryName:'Serviços',paymentStatus:'PENDENTE',overdue:false,status:'PENDENTE',version:3,installmentNumber:1,installmentCount:1,recurrenceId:null,occurrenceNumber:null,notes:'Observação original'};
const recurrence:Recurrence={id:30,description:'Aluguel',amount:'100.00',categoryId:3,accountId:1,firstDueDate:today(),occurrences:12,active:true,version:5};
const page=(rows:Installment[])=>({content:rows,totalElements:rows.length,totalPages:rows.length?1:0,page:0,size:20});
function setup(element:React.ReactNode){return render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><MemoryRouter><Notifications>{element}</Notifications></MemoryRouter></QueryClientProvider>);}
function actions():InstallmentActions{return {pay:vi.fn(),edit:vi.fn(),payments:vi.fn(),history:vi.fn(),details:vi.fn(),cancel:vi.fn(),future:vi.fn()};}
beforeEach(()=>{
  vi.resetAllMocks();
  Object.defineProperty(HTMLDialogElement.prototype,'showModal',{configurable:true,value:function(this:HTMLDialogElement){this.open=true;}});
  Object.defineProperty(HTMLDialogElement.prototype,'close',{configurable:true,value:function(this:HTMLDialogElement){this.open=false;}});
  vi.mocked(api.get).mockImplementation(async url=>({data:url==='/finance/accounts'?accounts:url==='/finance/categories'?categories:url==='/finance/installments/10'?installment:url==='/finance/recurrences/30'?recurrence:url.endsWith('/settlements')?[]:page([])}));
  vi.mocked(api.post).mockResolvedValue({data:{updatedCount:4,skippedCount:2}});vi.mocked(api.put).mockResolvedValue({data:{updatedCount:4,skippedCount:2}});
});

it('previews installments preserving every cent and validates dates and count',()=>{
  expect(installmentAmounts('1.200,00',6)).toEqual(Array(6).fill('200.00'));
  expect(installmentAmounts('10,00',3)).toEqual(['3.34','3.33','3.33']);
  expect(installmentAmounts('999,99',120).reduce((sum,value)=>sum+scaled(value,2),0n)).toBe(99999n);
  expect(()=>installmentAmounts('0,01',2)).toThrow(/centavo/);expect(()=>installmentAmounts('10',0)).toThrow(/120/);
  expect(validDate('2026-02-30')).toBe(false);expect(validDate('2028-02-29')).toBe(true);
});
it('creates a pending expense of 1200 divided into six installments with one stable request id',async()=>{
  setup(<ObligationForm kind="DESPESA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.type(screen.getByLabelText('Descrição'),'Materiais parcelados');await user.type(screen.getByLabelText('Valor total (R$)'),'1.200,00');await user.selectOptions(screen.getByLabelText('Categoria'),'3');
  fireEvent.change(screen.getByLabelText('Quantidade de parcelas'),{target:{value:'6'}});
  expect(screen.getByRole('status')).toHaveTextContent('6 parcelas de R$ 200,00');
  await user.click(screen.getByRole('button',{name:'Registrar conta'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/obligations',expect.objectContaining({requestId:expect.any(String),kind:'DESPESA',amount:'1200.00',installments:6,dueDate:today(),settled:false,categoryId:3,accountId:1})));
});
it('creates a finite monthly recurrence instead of mixing recurrence and installments',async()=>{
  setup(<ObligationForm kind="DESPESA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText('Como deseja cadastrar?'),'MONTHLY');
  expect(screen.queryByLabelText('Quantidade de parcelas')).not.toBeInTheDocument();
  await user.type(screen.getByLabelText('Descrição'),'Aluguel');await user.type(screen.getByLabelText('Valor de cada ocorrência (R$)'),'850,25');await user.selectOptions(screen.getByLabelText('Categoria'),'3');
  await user.click(screen.getByRole('button',{name:'Criar recorrência'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/recurrences',expect.objectContaining({requestId:expect.any(String),description:'Aluguel',amount:'850.25',occurrences:12,firstDueDate:today(),categoryId:3})));
  expect(api.post).not.toHaveBeenCalledWith('/finance/obligations',expect.anything());
});
it('ignores hidden installment validation when switching to a recurring expense',async()=>{
  setup(<ObligationForm kind="DESPESA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  fireEvent.change(screen.getByLabelText('Quantidade de parcelas'),{target:{value:'0'}});
  await user.selectOptions(screen.getByLabelText('Como deseja cadastrar?'),'MONTHLY');await user.type(screen.getByLabelText('Descrição'),'Aluguel');await user.type(screen.getByLabelText('Valor de cada ocorrência (R$)'),'100');await user.selectOptions(screen.getByLabelText('Categoria'),'3');
  await user.click(screen.getByRole('button',{name:'Criar recorrência'}));await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/recurrences',expect.objectContaining({occurrences:12,amount:'100.00'})));
});
it('rejects an excessive recurrence and never offers recurrence for receivables',async()=>{
  const view=setup(<ObligationForm kind="DESPESA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText('Como deseja cadastrar?'),'MONTHLY');await user.type(screen.getByLabelText('Descrição'),'Aluguel');await user.type(screen.getByLabelText('Valor de cada ocorrência (R$)'),'100');await user.selectOptions(screen.getByLabelText('Categoria'),'3');fireEvent.change(screen.getByLabelText('Quantidade de ocorrências mensais'),{target:{value:'61'}});
  await user.click(screen.getByRole('button',{name:'Criar recorrência'}));expect(await screen.findByRole('alert')).toHaveTextContent('1 a 60');expect(api.post).not.toHaveBeenCalled();
  view.unmount();setup(<ObligationForm kind="RECEITA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);expect(screen.queryByLabelText('Como deseja cadastrar?')).not.toBeInTheDocument();
});
it('preserves creation inputs and operation key on a failed request and retry',async()=>{
  vi.mocked(api.post).mockRejectedValue(new Error('network'));
  setup(<ObligationForm kind="RECEITA" accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.type(screen.getByLabelText('Descrição'),'Serviço parcelado');await user.type(screen.getByLabelText('Valor total (R$)'),'1500');await user.selectOptions(screen.getByLabelText('Categoria'),'2');await user.click(screen.getByRole('button',{name:'Registrar conta'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível concluir');expect(screen.getByLabelText('Descrição')).toHaveValue('Serviço parcelado');
  await user.click(screen.getByRole('button',{name:'Registrar conta'}));await waitFor(()=>expect(api.post).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api.post).mock.calls[0][1]).toEqual(vi.mocked(api.post).mock.calls[1][1]);
});
it('registers 500 of 1500 and clearly keeps 1000 pending',async()=>{
  setup(<SettlementForm entry={asEntry(installment)} account={accounts[0]} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.clear(screen.getByLabelText('Valor da liquidação (R$)'));await user.type(screen.getByLabelText('Valor da liquidação (R$)'),'500,00');
  expect(screen.getByText(/Saldo pendente após este registro/)).toHaveTextContent('R$ 1.000,00');
  await user.click(screen.getByRole('button',{name:'Confirmar liquidação'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries/10/settle',expect.objectContaining({amount:'500.00',version:3,requestId:expect.any(String),settlementDate:today()})));
});
it('supports the second partial receipt and blocks amounts above the remainder',async()=>{
  setup(<SettlementForm entry={asEntry({...installment,status:'PARCIAL',paidAmount:'500.00',remainingAmount:'1000.00'})} account={accounts[0]} onClose={vi.fn()}/>);const user=userEvent.setup();
  expect(screen.getByLabelText('Valor da liquidação (R$)')).toHaveValue('1000.00');
  await user.clear(screen.getByLabelText('Valor da liquidação (R$)'));await user.type(screen.getByLabelText('Valor da liquidação (R$)'),'1000,01');await user.click(screen.getByRole('button',{name:'Confirmar liquidação'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('não pode superar');expect(api.post).not.toHaveBeenCalled();
  await user.clear(screen.getByLabelText('Valor da liquidação (R$)'));await user.type(screen.getByLabelText('Valor da liquidação (R$)'),'1000');await user.click(screen.getByRole('button',{name:'Confirmar liquidação'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries/10/settle',expect.objectContaining({amount:'1000.00'})));
});
it('prevents closing a partial settlement while the write is in flight',async()=>{
  let complete:(value:unknown)=>void=()=>{};vi.mocked(api.post).mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));
  setup(<SettlementForm entry={asEntry(installment)} account={accounts[0]} onClose={vi.fn()}/>);await userEvent.setup().click(screen.getByRole('button',{name:'Confirmar liquidação'}));
  expect(screen.getByRole('button',{name:'Fechar'})).toBeDisabled();expect(screen.getByRole('button',{name:'Voltar'})).toBeDisabled();
  await act(async()=>complete({data:{}}));
});
it('edits only one installment and preserves financial fields after partial payment',async()=>{
  const entry={...installment,status:'PARCIAL' as const,paidAmount:'500.00',remainingAmount:'1000.00',installmentCount:3};
  setup(<InstallmentEditor entry={entry} accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  expect(screen.getByLabelText('Valor da parcela (R$)')).toHaveAttribute('readonly');expect(screen.getByLabelText('Conta financeira')).toBeDisabled();
  fireEvent.change(screen.getByLabelText('Vencimento'),{target:{value:'2027-01-31'}});await user.clear(screen.getByLabelText('Observações'));await user.type(screen.getByLabelText('Observações'),'Prazo combinado');await user.click(screen.getByRole('button',{name:'Salvar parcela'}));
  await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/finance/installments/10',expect.objectContaining({version:3,amount:'1500.00',accountId:1,categoryId:2,dueDate:'2027-01-31',notes:'Prazo combinado'})));
});
it('keeps financial fields immutable after every settlement was reversed',()=>{
  setup(<InstallmentEditor entry={{...installment,hasSettlementHistory:true,paidAmount:'0.00'}} accounts={accounts} categories={categories} onClose={vi.fn()}/>);
  expect(screen.getByLabelText('Valor da parcela (R$)')).toHaveAttribute('readonly');expect(screen.getByLabelText('Conta financeira')).toBeDisabled();expect(screen.getByText(/cancele o registro e cadastre uma nova obrigação/)).toBeInTheDocument();
});
it('updates only future unpaid recurrence occurrences using the recurrence version',async()=>{
  setup(<RecurrenceEditor entry={{...installment,kind:'DESPESA',categoryId:3,recurrenceId:30,occurrenceNumber:2}} accounts={accounts} categories={categories} onClose={vi.fn()}/>);const user=userEvent.setup();
  expect(await screen.findByLabelText('Valor de cada ocorrência (R$)')).toBeInTheDocument();
  expect(screen.getByText(/cancelados ou com liquidações estornadas serão preservados/)).toBeInTheDocument();
  await user.clear(screen.getByLabelText('Valor de cada ocorrência (R$)'));await user.type(screen.getByLabelText('Valor de cada ocorrência (R$)'),'250');fireEvent.change(screen.getByLabelText('Dia do vencimento nas próximas ocorrências'),{target:{value:'31'}});
  await user.click(screen.getByRole('button',{name:'Salvar próximas ocorrências'}));
  await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/finance/recurrences/30/future',expect.objectContaining({version:5,fromObligationId:20,amount:'250.00',dueDay:31})));
});
it('requires explicit confirmation to cancel future occurrences without reversing paid history',async()=>{
  setup(<RecurrenceEditor entry={{...installment,kind:'DESPESA',categoryId:3,recurrenceId:30,occurrenceNumber:2}} accounts={accounts} categories={categories} cancel onClose={vi.fn()}/>);
  expect(await screen.findByText(/Nenhum pagamento já registrado será estornado/)).toBeInTheDocument();expect(api.post).not.toHaveBeenCalled();
  await userEvent.setup().click(screen.getByRole('button',{name:'Confirmar cancelamento das próximas'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/recurrences/30/cancel-future',{version:5,fromObligationId:20}));
});
it('shows payment history and reverses one payment with the latest entry version',async()=>{
  const settlements:Settlement[]=[{id:7,amount:'500.00',settlementDate:today(),reversedAt:null,createdAt:`${today()}T12:00:00Z`},{id:8,amount:'10.00',settlementDate:today(),reversedAt:`${today()}T13:00:00Z`,createdAt:`${today()}T12:00:00Z`}];
  const get=vi.mocked(api.get).getMockImplementation()!;vi.mocked(api.get).mockImplementation(async(...args)=>args[0].endsWith('/settlements')?{data:settlements}:args[0]==='/finance/installments/10'?{data:{...installment,status:'PARCIAL',paidAmount:'500.00',remainingAmount:'1000.00',version:9}}:get(...args));
  setup(<PaymentHistory entry={installment} onClose={vi.fn()}/>);const user=userEvent.setup();
  await user.click(await screen.findByRole('button',{name:'Estornar liquidação #7'}));expect(screen.queryByRole('button',{name:'Estornar liquidação #8'})).not.toBeInTheDocument();
  expect(screen.getByLabelText('Motivo do estorno')).toHaveAttribute('maxlength','300');
  expect(screen.getByText(/não executa reembolso ou transferência bancária/)).toBeInTheDocument();
  await user.click(screen.getByRole('button',{name:'Confirmar estorno'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries/10/settlements/7/reverse',{version:9,reason:'Correção de registro'}));
});
it('filters installments by kind, due dates and partial state without sending company identifiers',async()=>{
  setup(<Obligations kind="RECEITA"/>);expect(await screen.findByText('Nenhuma conta a receber encontrada')).toBeInTheDocument();const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText('Situação'),'PARCIAL');fireEvent.change(screen.getByLabelText('Vencimento de'),{target:{value:'2026-01-01'}});fireEvent.change(screen.getByLabelText('Vencimento até'),{target:{value:'2026-12-31'}});
  await waitFor(()=>expect(api.get).toHaveBeenCalledWith('/finance/installments',expect.objectContaining({params:expect.objectContaining({kind:'RECEITA',status:'PARCIAL',from:'2026-01-01',to:'2026-12-31'})})));
  for(const [url,options] of vi.mocked(api.get).mock.calls)if(url==='/finance/installments')expect(options?.params).not.toHaveProperty('companyId');
});
it('shows partial overdue balances and opens payment and cancellation actions',async()=>{
  const entry={...installment,paidAmount:'500.00',remainingAmount:'1000.00',paymentStatus:'VENCIDO' as const,status:'PARCIAL' as const,overdue:true};
  const get=vi.mocked(api.get).getMockImplementation()!;vi.mocked(api.get).mockImplementation(async(...args)=>args[0]==='/finance/installments'?{data:page([entry])}:get(...args));
  setup(<Obligations kind="RECEITA"/>);expect(await screen.findByText('Pagamento parcial em atraso')).toBeInTheDocument();const user=userEvent.setup();
  const row=screen.getByRole('row',{name:/Cobrança de teste/});expect(within(row).getByText('R$ 1.000,00')).toBeInTheDocument();
  await user.click(screen.getByRole('button',{name:/^Cancelar Cobrança de teste/}));expect(api.post).not.toHaveBeenCalled();
  expect(screen.getByRole('dialog',{name:'Cancelar esta parcela?'})).toHaveTextContent('As outras parcelas serão preservadas');
  await user.click(screen.getByRole('button',{name:'Confirmar cancelamento'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith('/finance/entries/10/cancel',{version:3}));
});
it('opens all installments of one obligation without changing other installments',async()=>{
  const detail:ObligationDetail={id:20,kind:'DESPESA',description:'Material parcelado',totalAmount:'1200.00',installmentCount:6,recurrenceId:null,occurrenceNumber:null,version:0,installments:Array.from({length:6},(_,index)=>({...installment,id:10+index,amount:'200.00',remainingAmount:'200.00',kind:'DESPESA',description:'Material parcelado',installmentNumber:index+1,installmentCount:6}))};
  vi.mocked(api.get).mockResolvedValue({data:detail});const callbacks=actions();setup(<ObligationDetails id={20} actions={callbacks} onClose={vi.fn()}/>);
  expect(await screen.findByText(/Valor total:/)).toHaveTextContent('R$ 1.200,00');expect(screen.getAllByRole('row')).toHaveLength(7);
  const edit=screen.getByRole('button',{name:/^Editar Material parcelado, parcela 3,/});await userEvent.setup().click(edit);expect(callbacks.edit).toHaveBeenCalledWith(expect.objectContaining({id:12,installmentNumber:3}));expect(api.put).not.toHaveBeenCalled();
});
it('integrates partial movements with installment management while preserving old quick forms',async()=>{
  const get=vi.mocked(api.get).getMockImplementation()!;vi.mocked(api.get).mockImplementation(async(...args)=>args[0]==='/finance/entries'?{data:page([asEntry({...installment,status:'PARCIAL',paidAmount:'500.00',remainingAmount:'1000.00'}) as unknown as Installment])}:get(...args));
  setup(<FinanceMovements/>);expect(await screen.findByText('Gerenciar parcela')).toHaveAttribute('href','/financeiro/receber');
  expect(screen.getByText('Liquidado R$ 500,00 · Pendente R$ 1.000,00')).toBeInTheDocument();expect(screen.getByRole('button',{name:'Liquidar Cobrança de teste'})).toBeEnabled();expect(screen.queryByRole('button',{name:'Editar Cobrança de teste'})).not.toBeInTheDocument();
});
it('preserves an existing due date when editing a quick entry from movements',async()=>{
  setup(<EntryForm kind="RECEITA" entry={asEntry({...installment,dueDate:'2027-01-31',status:'LIQUIDADO',paidAmount:'1500.00',remainingAmount:'0.00'})} accounts={accounts} categories={categories} onClose={vi.fn()}/>);
  expect(screen.getByText(/estorna as liquidações anteriores/)).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button',{name:'Salvar alterações'}));
  await waitFor(()=>expect(api.put).toHaveBeenCalledWith('/finance/entries/10',expect.objectContaining({dueDate:'2027-01-31',amount:'1500.00',version:3})));
});
it('renders human readable audit actions and balances nested in reversal snapshots',async()=>{
  vi.mocked(api.get).mockResolvedValue({data:[{id:1,actorId:1,action:'ESTORNO_LIQUIDACAO',beforeData:null,afterData:JSON.stringify({reason:'Valor incorreto',settlementId:7,entry:{description:'Cobrança de teste',paidAmount:'500.00',remainingAmount:'1000.00'}}),createdAt:`${today()}T12:00:00Z`}]});
  setup(<HistoryModal resource="LANCAMENTO" id={10} onClose={vi.fn()}/>);
  expect(await screen.findByText('Estorno de liquidação')).toBeInTheDocument();expect(screen.getByText('R$ 500,00')).toBeInTheDocument();expect(screen.getByText('R$ 1.000,00')).toBeInTheDocument();expect(screen.getByText('Valor incorreto')).toBeInTheDocument();
});
