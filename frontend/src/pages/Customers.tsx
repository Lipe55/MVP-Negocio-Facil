import axios from "axios";
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Search, Eye, Pencil, Trash2, Archive } from "lucide-react";
import { api, errorMessage } from "../api";
import type { Customer, Page } from "../types";
import { customerSchema, type CustomerInput } from "../schemas";
import {
  PageHeader,
  Loading,
  ErrorState,
  Empty,
  Field,
  Modal,
  Confirm,
  Pagination,
  useNotify,
} from "../components/ui";
import { dateTime } from "../money";
function CustomerForm({
  customer,
  onClose,
}: {
  customer: Customer | null;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const notify = useNotify();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CustomerInput>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      name: customer?.name ?? "",
      phone: customer?.phone ?? "",
      email: customer?.email ?? "",
      document: customer?.document ?? "",
      notes: customer?.notes ?? "",
    },
  });
  return (
    <Modal
      title={customer ? "Editar cliente" : "Novo cliente"}
      onClose={onClose}
    >
      <form
        noValidate
        onSubmit={handleSubmit(async (values) => {
          try {
            const body = { ...values, email: values.email || null };
            if (customer) await api.put(`/customers/${customer.id}`, body);
            else await api.post("/customers", body);
            await client.invalidateQueries({ queryKey: ["customers"] });
            await client.invalidateQueries({ queryKey: ["dashboard"] });
            notify(customer ? "Cliente atualizado." : "Cliente cadastrado.");
            onClose();
          } catch (e) {
            setError("root", { message: errorMessage(e) });
          }
        })}
      >
        <Field label="Nome" error={errors.name?.message}>
          <input autoFocus {...register("name")} />
        </Field>
        <Field label="WhatsApp" error={errors.phone?.message}>
          <input
            type="tel"
            placeholder="(11) 99999-9999"
            {...register("phone")}
          />
        </Field>
        <Field label="E-mail (opcional)" error={errors.email?.message}>
          <input type="email" {...register("email")} />
        </Field>
        <Field label="CPF/CNPJ (opcional)" error={errors.document?.message}>
          <input {...register("document")} />
        </Field>
        <Field label="Observações (opcional)" error={errors.notes?.message}>
          <textarea rows={3} {...register("notes")} />
        </Field>
        {errors.root && (
          <p className="form-error" role="alert">
            {errors.root.message}
          </p>
        )}
        <div className="actions">
          <button type="button" className="btn secondary" onClick={onClose}>
            Cancelar
          </button>
          <button className="btn" disabled={isSubmitting}>
            {isSubmitting ? "Salvando…" : "Salvar cliente"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
export default function Customers() {
  const [search, setSearch] = useState("");
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Customer | null | undefined>();
  const [detail, setDetail] = useState<Customer | null>(null);
  const [removing, setRemoving] = useState<Customer | null>(null);
  const [archiveOffer, setArchiveOffer] = useState(false);
  const client = useQueryClient();
  const notify = useNotify();
  const query = useQuery({
    queryKey: ["customers", search, archived, page],
    queryFn: async () =>
      (
        await api.get<Page<Customer>>("/customers", {
          params: { search, archived, page },
        })
      ).data,
  });
  const removal = useMutation({
    mutationFn: async () => {
      if (!removing) return;
      if (archiveOffer) await api.post(`/customers/${removing.id}/archive`);
      else await api.delete(`/customers/${removing.id}`);
    },
    onSuccess: () => {
      notify(archiveOffer ? "Cliente arquivado." : "Cliente excluído.");
      if (query.data?.content.length === 1 && page > 0) setPage(page - 1);
      setRemoving(null);
      setArchiveOffer(false);
      void client.invalidateQueries({ queryKey: ["customers"] });
      void client.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (e) => {
      notify(errorMessage(e), true);
      if (!archiveOffer && axios.isAxiosError(e) && e.response?.status === 409)
        setArchiveOffer(true);
    },
  });
  return (
    <>
      <PageHeader
        title="Clientes"
        subtitle="Pessoas e empresas que fazem parte do seu negócio."
        action={
          <button className="btn" onClick={() => setEditing(null)}>
            <Plus size={18} />
            Novo cliente
          </button>
        }
      />
      <section className="panel">
        <div className="filters">
          <div className="search-input">
            <Search size={18} />
            <input
              aria-label="Buscar clientes por nome"
              placeholder="Buscar por nome…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={archived}
              onChange={(e) => {
                setArchived(e.target.checked);
                setPage(0);
              }}
            />
            Incluir arquivados
          </label>
        </div>
        {query.isLoading ? (
          <Loading />
        ) : query.error ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : query.data?.content.length ? (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Cliente</th>
                    <th>WhatsApp</th>
                    <th>E-mail</th>
                    <th>Situação</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.content.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <button
                          className="customer-name"
                          onClick={() => setDetail(c)}
                        >
                          <span className="customer-avatar">
                            {c.name.slice(0, 1).toUpperCase()}
                          </span>
                          <strong>{c.name}</strong>
                        </button>
                      </td>
                      <td>{c.phone}</td>
                      <td className="muted">{c.email || "—"}</td>
                      <td>
                        <span className="subtle-label">
                          {c.archivedAt ? "Arquivado" : "Ativo"}
                        </span>
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="icon-btn"
                            aria-label={`Ver ${c.name}`}
                            onClick={() => setDetail(c)}
                          >
                            <Eye size={17} />
                          </button>
                          <button
                            className="icon-btn"
                            aria-label={`Editar ${c.name}`}
                            onClick={() => setEditing(c)}
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            className="icon-btn"
                            aria-label={`Excluir ${c.name}`}
                            onClick={() => {
                              setRemoving(c);
                              setArchiveOffer(false);
                            }}
                          >
                            <Trash2 size={16} />
                          </button>
                          {!c.archivedAt && (
                            <button
                              className="icon-btn"
                              aria-label={`Arquivar ${c.name}`}
                              onClick={() => {
                                setRemoving(c);
                                setArchiveOffer(true);
                              }}
                            >
                              <Archive size={16} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              totalPages={query.data.totalPages}
              total={query.data.totalElements}
              onChange={setPage}
            />
          </>
        ) : (
          <Empty
            title={
              search ? "Nenhum cliente encontrado" : "Sua carteira começa aqui"
            }
            action={
              !search && (
                <button
                  className="btn secondary"
                  onClick={() => setEditing(null)}
                >
                  Cadastrar primeiro cliente
                </button>
              )
            }
          >
            {search
              ? "Experimente buscar por outro nome."
              : "Cadastre seu primeiro cliente para criar uma proposta."}
          </Empty>
        )}
      </section>
      {editing !== undefined && (
        <CustomerForm
          customer={editing}
          onClose={() => setEditing(undefined)}
        />
      )}{" "}
      {detail && (
        <Modal title={detail.name} onClose={() => setDetail(null)}>
          <dl className="detail-list">
            <dt>WhatsApp</dt>
            <dd>{detail.phone}</dd>
            <dt>E-mail</dt>
            <dd>{detail.email || "Não informado"}</dd>
            <dt>CPF/CNPJ</dt>
            <dd>{detail.document || "Não informado"}</dd>
            <dt>Observações</dt>
            <dd className="whitespace-pre-wrap">
              {detail.notes || "Nenhuma observação."}
            </dd>
            <dt>Cadastrado em</dt>
            <dd>{dateTime(detail.createdAt)}</dd>
            <dt>Atualizado em</dt>
            <dd>{dateTime(detail.updatedAt)}</dd>
          </dl>
        </Modal>
      )}
      {removing && (
        <Confirm
          title={archiveOffer ? "Arquivar cliente?" : "Excluir cliente?"}
          description={
            archiveOffer
              ? "O cliente sai da lista de ativos. Seus orçamentos e o histórico serão preservados."
              : "Esta ação exclui o cadastro. Se houver orçamentos vinculados, será necessário arquivar o cliente."
          }
          busy={removal.isPending}
          danger={!archiveOffer}
          onClose={() => setRemoving(null)}
          onConfirm={() => removal.mutate()}
        />
      )}
    </>
  );
}
