import { scaled,decimal } from '../../money';
import { moneyInput } from '../schemas';

export function validDate(value:string):boolean {
  if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;
  const date=new Date(`${value}T12:00:00Z`);
  return !Number.isNaN(date.getTime())&&date.toISOString().slice(0,10)===value;
}
/** A preview only; the backend independently validates and distributes cents. */
export function installmentAmounts(amount:string,count:number):string[] {
  if(!Number.isInteger(count)||count<1||count>120)throw new Error('Use de 1 a 120 parcelas.');
  const total=scaled(moneyInput(amount),2),n=BigInt(count);
  if(total<n)throw new Error('Cada parcela deve ter pelo menos um centavo.');
  const base=total/n,remainder=total%n;
  return Array.from({length:count},(_,index)=>decimal(base+(BigInt(index)<remainder?1n:0n)));
}
