import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { Badge, Empty } from "./ui";
import { date, money } from "../money";
import type { Quotation } from "../types";
export default function QuotationTable({ rows }: { rows: Quotation[] }) {
  if (!rows.length)
    return (
      <Empty title="Nenhum orçamento por aqui">
        Crie uma proposta e dê o próximo passo com seus clientes.
      </Empty>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Orçamento</th>
            <th>Cliente</th>
            <th>Criado em</th>
            <th>Validade</th>
            <th>Valor total</th>
            <th>Status</th>
            <th>
              <span className="sr-only">Ações</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((q) => (
            <tr key={q.id}>
              <td>
                <Link className="strong-link" to={`/orcamentos/${q.id}`}>
                  #{String(q.number).padStart(4, "0")}
                </Link>
              </td>
              <td>{q.customerName}</td>
              <td className="muted">{date(q.createdAt)}</td>
              <td className="muted">{date(q.validUntil)}</td>
              <td className="table-money">{money(q.total)}</td>
              <td>
                <Badge status={q.status} />
              </td>
              <td>
                <Link
                  className="icon-btn"
                  to={`/orcamentos/${q.id}`}
                  aria-label={`Visualizar orçamento ${q.number}`}
                >
                  <ArrowUpRight size={18} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
