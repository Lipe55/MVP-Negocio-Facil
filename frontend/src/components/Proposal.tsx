import { date, money, quantity } from "../money";
import { Badge } from "./ui";
import type { Status, Item } from "../types";
export default function Proposal({
  number,
  companyName,
  customerName,
  status,
  validUntil,
  items,
  subtotal,
  discount,
  total,
  notes,
  terms,
}: {
  number: number;
  companyName: string;
  customerName: string;
  status: Status;
  validUntil: string;
  items: Omit<Item, "id">[];
  subtotal: string;
  discount: string;
  total: string;
  notes?: string | null;
  terms?: string | null;
}) {
  return (
    <section className="panel proposal">
      <div className="proposal-head">
        <div>
          <span className="eyebrow">PROPOSTA COMERCIAL</span>
          <h2>Orçamento #{String(number).padStart(4, "0")}</h2>
          <p>{companyName}</p>
        </div>
        <Badge status={status} />
      </div>
      <div className="proposal-parties">
        <div>
          <span>PREPARADO PARA</span>
          <strong>{customerName}</strong>
        </div>
        <div>
          <span>VÁLIDO ATÉ</span>
          <strong>{date(validUntil)}</strong>
        </div>
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Descrição</th>
              <th>Qtd.</th>
              <th>Valor unitário</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => (
              <tr key={index}>
                <td className="item-description">{item.description}</td>
                <td>{quantity(item.quantity)}</td>
                <td className="nowrap">{money(item.unitPrice)}</td>
                <td className="table-money nowrap">{money(item.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="proposal-totals">
        <div className="summary-line">
          <span>Subtotal</span>
          <strong>{money(subtotal)}</strong>
        </div>
        <div className="summary-line">
          <span>Desconto</span>
          <strong>{money(discount)}</strong>
        </div>
        <div className="summary-total">
          <span>Total do orçamento</span>
          <strong>{money(total)}</strong>
        </div>
      </div>
      {terms && (
        <div className="proposal-notes">
          <h3>Condições comerciais</h3>
          <p className="whitespace-pre-wrap">{terms}</p>
        </div>
      )}
      {notes && (
        <div className="proposal-notes">
          <h3>Notas internas (não compartilhadas)</h3>
          <p className="whitespace-pre-wrap">{notes}</p>
        </div>
      )}
    </section>
  );
}
