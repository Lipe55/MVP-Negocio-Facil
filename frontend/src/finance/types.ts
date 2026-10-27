import type { Page } from '../types';
export type FinanceKind = 'RECEITA' | 'DESPESA' | 'RETIRADA';
export type FinanceStatus = 'PENDENTE' | 'LIQUIDADO' | 'CANCELADO';
export interface FinanceAccount {
  id: number; name: string; type: 'BANCO' | 'DINHEIRO'; primaryAccount: boolean;
  active: boolean; openingBalance: string; openingDate: string; balance: string; version: number;
}
export interface FinanceCategory { id: number; name: string; kind: 'RECEITA' | 'DESPESA'; }
export interface FinanceEntry {
  id: number; kind: FinanceKind; status: FinanceStatus; accountId: number; accountName: string;
  categoryId: number | null; categoryName: string | null; description: string; amount: string;
  entryDate: string; settlementDate: string | null; notes: string | null; version: number; createdAt: string;
}
export interface FinanceMovement {
  id: number; effectiveDate: string;
  kind: 'RECEBIMENTO' | 'PAGAMENTO' | 'RETIRADA' | 'TRANSFERENCIA_ENTRADA' | 'TRANSFERENCIA_SAIDA' | 'ESTORNO';
  description: string; amount: string; runningBalance: string; entryId: number | null;
  transferId: number | null; transferVersion: number | null; reversalOfId: number | null; reversed: boolean; createdAt: string;
}
export interface FinanceStatement {
  account: FinanceAccount; balanceBeforePeriod: string; periodChange: string; balanceAtEnd: string;
  movements: Page<FinanceMovement>;
}
export interface FinanceAudit { id: number; actorId: number | null; action: string; beforeData: string | null; afterData: string; createdAt: string; }
