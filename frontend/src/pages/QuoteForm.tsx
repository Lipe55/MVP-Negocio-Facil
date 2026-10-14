import { useForm, useFieldArray, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useNavigate, Link } from "react-router-dom";
import { ArrowLeft, Plus, Trash2, Save } from "lucide-react";
import { api, errorMessage } from "../api";
import { quoteSchema, type QuoteInput } from "../schemas";
import type { Quotation } from "../types";
import { totals, money, itemCents, today } from "../money";
import {
  Field,
  PageHeader,
  Loading,
  ErrorState,
  useNotify,
} from "../components/ui";
import CustomerPicker from "../components/CustomerPicker";
function Editor({ quotation }: { quotation?: Quotation }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const notify = useNotify();
  const {
    register,
    control,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<QuoteInput>({
    resolver: zodResolver(quoteSchema),
    defaultValues: quotation
      ? {
          customerId: String(quotation.customer.id),
          validUntil: quotation.validUntil,
          notes: quotation.notes ?? "",
          terms: quotation.terms ?? "",
          discount: quotation.discount,
          items: quotation.items.map((i) => ({
            description: i.description,
            quantity: i.quantity,
            unitPrice: i.unitPrice,
          })),
        }
      : {
          customerId: "",
          validUntil: today(),
          notes: "",
          terms: "",
          discount: "0.00",
          items: [{ description: "", quantity: "1", unitPrice: "0.00" }],
        },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "items" });
  const values = watch();
  let amounts;
  try {
    amounts = totals(values.items, values.discount);
  } catch {
    amounts = null;
  }
  return (
    <form
      noValidate
      onSubmit={handleSubmit(async (values) => {
        try {
          const body = { ...values, customerId: Number(values.customerId) };
          const response = quotation
            ? await api.put<Quotation>(`/quotations/${quotation.id}`, body)
            : await api.post<Quotation>("/quotations", body);
          await client.invalidateQueries({ queryKey: ["quotations"] });
          await client.invalidateQueries({
            queryKey: ["quotation", String(response.data.id)],
          });
          await client.invalidateQueries({ queryKey: ["dashboard"] });
          notify("Rascunho salvo.");
          navigate(`/orcamentos/${response.data.id}`);
        } catch (e) {
          setError("root", { message: errorMessage(e) });
        }
      })}
    >
      <div className="quote-editor">
        <div className="editor-main">
          <section className="panel form-panel">
            <h2>Informações gerais</h2>
            <div className="form-grid">
              <Field label="Cliente" error={errors.customerId?.message}>
                <Controller
                  control={control}
                  name="customerId"
                  render={({ field }) => (
                    <CustomerPicker
                      value={field.value}
                      onChange={field.onChange}
                      selected={quotation?.customer}
                    />
                  )}
                />
              </Field>
              <Field label="Válido até" error={errors.validUntil?.message}>
                <input type="date" min={today()} {...register("validUntil")} />
              </Field>
            </div>
          </section>
          <section className="panel form-panel">
            <div className="panel-head inline">
              <div>
                <h2>Itens do orçamento</h2>
                <p>Descreva o que será entregue ao cliente.</p>
              </div>
            </div>
            <div className="item-list">
              {fields.map((field, index) => {
                let total;
                try {
                  total = itemCents(
                    values.items[index]?.quantity ?? "0",
                    values.items[index]?.unitPrice ?? "0",
                  );
                } catch {
                  total = null;
                }
                return (
                  <div className="item-editor" key={field.id}>
                    <Field
                      label={`Descrição do item ${index + 1}`}
                      error={errors.items?.[index]?.description?.message}
                    >
                      <input
                        placeholder="Ex.: Instalação e configuração"
                        {...register(`items.${index}.description`)}
                      />
                    </Field>
                    <Field
                      label="Quantidade"
                      error={errors.items?.[index]?.quantity?.message}
                    >
                      <input
                        inputMode="decimal"
                        {...register(`items.${index}.quantity`)}
                      />
                    </Field>
                    <Field
                      label="Valor unitário (R$)"
                      error={errors.items?.[index]?.unitPrice?.message}
                    >
                      <input
                        inputMode="decimal"
                        {...register(`items.${index}.unitPrice`)}
                      />
                    </Field>
                    <div className="item-total">
                      <span>Total do item</span>
                      <strong>{total === null ? "—" : money(total)}</strong>
                    </div>
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`Remover item ${index + 1}`}
                      onClick={() => remove(index)}
                    >
                      <Trash2 size={18} />
                    </button>
                  </div>
                );
              })}
            </div>
            {errors.items?.message && (
              <p className="field-error" role="alert">
                {errors.items.message}
              </p>
            )}
            <button
              type="button"
              className="btn secondary"
              disabled={fields.length >= 100}
              onClick={() =>
                append({ description: "", quantity: "1", unitPrice: "0.00" })
              }
            >
              <Plus size={17} />
              Adicionar item
            </button>
            <p className="input-hint">
              Use ponto para casas decimais. Exemplo: 1.5 e 150.00.
            </p>
          </section>
          <section className="panel form-panel">
            <Field label="Condições comerciais (visíveis ao cliente)" error={errors.terms?.message}>
              <textarea rows={4}
                placeholder="Condições de pagamento e prazo de entrega."
                {...register("terms")} />
            </Field>
            <Field label="Notas internas (não compartilhadas)" error={errors.notes?.message}>
              <textarea
                rows={4}
                placeholder="Informações internas da sua empresa."
                {...register("notes")}
              />
            </Field>
          </section>
        </div>
        <aside className="panel summary-panel">
          <h2>Resumo do orçamento</h2>
          <div className="summary-line">
            <span>Subtotal</span>
            <strong>{amounts ? money(amounts.subtotal) : "—"}</strong>
          </div>
          <Field label="Desconto (R$)" error={errors.discount?.message}>
            <input inputMode="decimal" {...register("discount")} />
          </Field>
          <div className="summary-total">
            <span>Valor total</span>
            <strong>{amounts ? money(amounts.total) : "—"}</strong>
          </div>
          <p className="muted small-text">
            O orçamento será salvo como rascunho. Você poderá revisar antes de
            enviar.
          </p>
          {errors.root && (
            <p className="form-error" role="alert">
              {errors.root.message}
            </p>
          )}
          <button className="btn w-full" disabled={isSubmitting}>
            <Save size={17} />
            {isSubmitting ? "Salvando…" : "Salvar rascunho"}
          </button>
          <Link
            className="btn secondary w-full"
            to={quotation ? `/orcamentos/${quotation.id}` : "/orcamentos"}
          >
            Cancelar
          </Link>
        </aside>
      </div>
    </form>
  );
}
export default function QuoteForm() {
  const { id } = useParams();
  const query = useQuery({
    queryKey: ["quotation", id],
    queryFn: async () => (await api.get<Quotation>(`/quotations/${id}`)).data,
    enabled: !!id,
  });
  if (id && query.isLoading) return <Loading />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  if (query.data && query.data.status !== "RASCUNHO")
    return (
      <ErrorState
        error={
          new Error("Este orçamento foi enviado e não pode mais ser editado.")
        }
      />
    );
  return (
    <>
      <Link className="back-link" to={id ? `/orcamentos/${id}` : "/orcamentos"}>
        <ArrowLeft size={16} />
        Voltar
      </Link>
      <PageHeader
        title={id ? "Editar orçamento" : "Novo orçamento"}
        subtitle="Uma proposta clara é o começo de um bom negócio."
      />
      <Editor key={id ?? "new"} quotation={query.data} />
    </>
  );
}
