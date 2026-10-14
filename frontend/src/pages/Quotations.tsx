import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { Plus } from "lucide-react";
import { api } from "../api";
import type { Page, Quotation, Status } from "../types";
import {
  PageHeader,
  Loading,
  ErrorState,
  Pagination,
  labels,
} from "../components/ui";
import QuotationTable from "../components/QuotationTable";
import CustomerPicker from "../components/CustomerPicker";
export default function Quotations() {
  const [params, setParams] = useSearchParams();
  const [customerId, setCustomerId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const status = params.get("status") ?? "";
  const query = useQuery({
    queryKey: ["quotations", status, customerId, from, to, page],
    queryFn: async () =>
      (
        await api.get<Page<Quotation>>("/quotations", {
          params: {
            status: status || undefined,
            customerId: customerId || undefined,
            from: from || undefined,
            to: to || undefined,
            page,
          },
        })
      ).data,
    refetchInterval: 30000,
  });
  return (
    <>
      <PageHeader
        title="Orçamentos"
        subtitle="Da primeira proposta ao negócio fechado."
        action={
          <Link className="btn" to="/orcamentos/novo">
            <Plus size={18} />
            Novo orçamento
          </Link>
        }
      />
      <section className="panel">
        <div className="quote-filters">
          <label className="field">
            <span>Cliente</span>
            <CustomerPicker
              optional
              value={customerId}
              onChange={(v) => {
                setCustomerId(v);
                setPage(0);
              }}
            />
          </label>
          <label className="field">
            <span>Status</span>
            <select
              value={status}
              onChange={(e) => {
                setParams(e.target.value ? { status: e.target.value } : {});
                setPage(0);
              }}
            >
              <option value="">Todos os status</option>
              {(Object.keys(labels) as Status[]).map((s) => (
                <option key={s} value={s}>
                  {labels[s]}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Criado de</span>
            <input
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <label className="field">
            <span>Até</span>
            <input
              type="date"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
            />
          </label>
        </div>
        {query.isLoading ? (
          <Loading />
        ) : query.error ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            <QuotationTable rows={query.data!.content} />
            <Pagination
              page={page}
              totalPages={query.data!.totalPages}
              total={query.data!.totalElements}
              onChange={setPage}
            />
          </>
        )}
      </section>
    </>
  );
}
