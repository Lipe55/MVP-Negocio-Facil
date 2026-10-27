import { z } from 'zod';
import { decimal, scaled, today } from '../money';

/** Accept Brazilian grouping/decimal notation without using floating point. */
export function moneyInput(value: string, signed = false): string {
  let normalized = value.trim();
  const negative = normalized.startsWith('-');
  if (negative) {
    if (!signed) throw new Error('Informe um valor positivo.');
    normalized = normalized.slice(1);
  }
  if (normalized.includes(',')) {
    if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+),\d{1,2}$/.test(normalized)) throw new Error('Valor inválido.');
    normalized = normalized.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(?:\.\d{3})+$/.test(normalized)) {
    normalized = normalized.replace(/\./g, '');
  }
  const cents = scaled(normalized, 2);
  if (cents > 9999999999999999n) throw new Error('Valor excede o limite.');
  return decimal(negative ? -cents : cents);
}
export function validAmount(value: string, signed = false) {
  try { const normalized = moneyInput(value, signed); return signed || scaled(normalized, 2) > 0n; }
  catch { return false; }
}
const financialDate = z.string().refine((value) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T12:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}, 'Informe uma data válida.');
export const entrySchema = z.object({
  kind: z.enum(['RECEITA','DESPESA','RETIRADA']),
  description: z.string().trim().min(1,'Informe a descrição.').max(500),
  amount: z.string().refine(v => validAmount(v), 'Informe um valor maior que zero, com até duas casas decimais.'),
  categoryId: z.string(), accountId: z.string().min(1,'Selecione a conta.'),
  state: z.enum(['LIQUIDADO','PENDENTE']), entryDate: financialDate,
  settlementDate: z.string(), notes: z.string().max(3000),
}).superRefine((v,ctx) => {
  if (v.kind !== 'RETIRADA' && !v.categoryId) ctx.addIssue({code:'custom',path:['categoryId'],message:'Selecione a categoria.'});
  if (v.state === 'LIQUIDADO' && (!financialDate.safeParse(v.settlementDate).success || v.settlementDate > today()))
    ctx.addIssue({code:'custom',path:['settlementDate'],message:'Informe uma data efetiva até hoje.'});
});
export const accountSchema = z.object({
  name: z.string().trim().min(1,'Informe o nome.').max(160), type: z.enum(['BANCO','DINHEIRO']),
  openingBalance: z.string().refine(v => validAmount(v,true),'Informe um saldo válido.'),
  openingDate: financialDate.refine(v => v <= today(),'A referência não pode ser futura.'), active: z.boolean(),
});
export const transferSchema = z.object({
  sourceAccountId: z.string().min(1,'Selecione a origem.'), targetAccountId: z.string().min(1,'Selecione o destino.'),
  amount: z.string().refine(v => validAmount(v),'Informe um valor maior que zero.'),
  transferDate: financialDate.refine(v=>v<=today(),'A data não pode ser futura.'),
  description: z.string().max(500),
}).refine(v=>v.sourceAccountId!==v.targetAccountId,{path:['targetAccountId'],message:'Selecione contas diferentes.'});
export type EntryFormInput = z.infer<typeof entrySchema>;
export type AccountFormInput = z.infer<typeof accountSchema>;
export type TransferFormInput = z.infer<typeof transferSchema>;
