import type { FinanceEntry, FinanceStatus, PaymentStatus } from '../types';
export type ObligationKind = 'RECEITA' | 'DESPESA';
export interface Installment {
  id:number; obligationId:number; kind:ObligationKind; description:string; amount:string;
  paidAmount:string; remainingAmount:string; dueDate:string|null; entryDate:string;
  accountId:number; accountName:string; categoryId:number; categoryName:string;
  paymentStatus:PaymentStatus; overdue:boolean; status:FinanceStatus; version:number;
  installmentNumber:number; installmentCount:number; recurrenceId:number|null;
  occurrenceNumber:number|null; notes:string|null;
  hasSettlementHistory?:boolean;
}
export interface ObligationDetail {
  id:number; kind:ObligationKind; description:string; totalAmount:string;
  installmentCount:number; recurrenceId:number|null; occurrenceNumber:number|null;
  version:number; installments:Installment[];
}
export interface Settlement {
  id:number; amount:string; settlementDate:string; reversedAt:string|null; createdAt:string;
}
export interface Recurrence {
  id:number; description:string; amount:string; categoryId:number; accountId:number;
  firstDueDate:string; occurrences:number; active:boolean; version:number;
  notes?:string|null;
}
export interface FutureResult { updatedCount:number; skippedCount:number; }
export const paymentLabels:Record<PaymentStatus,string>={PENDENTE:'Pendente',PARCIAL:'Parcial',QUITADO:'Quitado',VENCIDO:'Vencido',CANCELADO:'Cancelado'};
export function asEntry(item:Installment):FinanceEntry {
  return {...item,settlementDate:null,createdAt:''};
}
