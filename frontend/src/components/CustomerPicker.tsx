import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import type { Customer, Page } from "../types";
import { ErrorState } from "./ui";
export default function CustomerPicker({
  value,
  onChange,
  selected,
  optional = false,
}: {
  value: string;
  onChange: (v: string) => void;
  selected?: Customer;
  optional?: boolean;
}) {
  const [search, setSearch] = useState("");
  const query = useQuery({
    queryKey: ["customers", "picker", search, optional],
    queryFn: async () =>
      (
        await api.get<Page<Customer>>("/customers", {
          params: { search, size: 100, archived: optional },
        })
      ).data,
  });
  let rows = query.data?.content ?? [];
  if (selected && !rows.some((c) => c.id === selected.id))
    rows = [selected, ...rows];
  return (
    <div className="customer-picker">
      <input
        aria-label="Buscar cliente para seleção"
        placeholder="Digite um nome para buscar…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <select
        aria-label="Cliente"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={query.isLoading}
      >
        <option value="">
          {optional
            ? "Todos os clientes"
            : query.isLoading
              ? "Carregando…"
              : "Selecione um cliente"}
        </option>
        {rows.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
            {c.archivedAt ? " (arquivado)" : ""}
          </option>
        ))}
      </select>
      {query.error && (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      )}
      <small className="muted">
        {query.data && query.data.totalElements > 100
          ? "Há mais resultados. Refine a busca pelo nome."
          : !rows.length && !query.isLoading
            ? optional
              ? "Nenhum cliente encontrado."
              : "Nenhum cliente ativo encontrado. Cadastre um cliente primeiro."
            : ""}
      </small>
    </div>
  );
}
