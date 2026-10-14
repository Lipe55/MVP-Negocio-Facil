import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Pencil,
  Send,
  Copy,
  ExternalLink,
  MessageCircle,
  Clock,
  CheckCircle2,
} from "lucide-react";
import { api, errorMessage } from "../api";
import type { Quotation } from "../types";
import {
  PageHeader,
  Loading,
  ErrorState,
  Confirm,
  useNotify,
} from "../components/ui";
import { dateTime, money, whatsapp } from "../money";
import Proposal from "../components/Proposal";
export default function QuoteDetail() {
  const { id } = useParams();
  const notify = useNotify();
  const client = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const [confirmRevoke, setConfirmRevoke] = useState(false);
  const query = useQuery({
    queryKey: ["quotation", id],
    queryFn: async () => (await api.get<Quotation>(`/quotations/${id}`)).data,
    refetchInterval: 15000,
  });
  const send = useMutation({
    mutationFn: async () =>
      (await api.post<Quotation>(`/quotations/${id}/send`)).data,
    onSuccess: (data) => {
      client.setQueryData(["quotation", id], data);
      void client.invalidateQueries({ queryKey: ["quotations"] });
      void client.invalidateQueries({ queryKey: ["dashboard"] });
      setConfirm(false);
      notify("Orçamento enviado. O link está pronto para compartilhar.");
    },
    onError: (e) => notify(errorMessage(e), true),
  });
  const revoke = useMutation({
    mutationFn: async () =>
      (await api.post<Quotation>(`/quotations/${id}/revoke-public`)).data,
    onSuccess: (data) => {
      client.setQueryData(["quotation", id], data);
      void client.invalidateQueries({ queryKey: ["quotations"] });
      void client.invalidateQueries({ queryKey: ["dashboard"] });
      setConfirmRevoke(false);
      notify("Link revogado. O histórico foi preservado.");
    },
    onError: (e) => notify(errorMessage(e), true),
  });
  if (query.isLoading) return <Loading />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  const q = query.data!;
  const url = q.publicToken
    ? `${window.location.origin}/o/${q.publicToken}`
    : "";
  return (
    <>
      <Link className="back-link" to="/orcamentos">
        <ArrowLeft size={16} />
        Todos os orçamentos
      </Link>
      <PageHeader
        title={`Orçamento #${String(q.number).padStart(4, "0")}`}
        subtitle={`Proposta para ${q.customerName}`}
        action={
          q.status === "RASCUNHO" ? (
            <div className="actions">
              <Link className="btn secondary" to={`/orcamentos/${q.id}/editar`}>
                <Pencil size={16} />
                Editar
              </Link>
              <button className="btn" onClick={() => setConfirm(true)}>
                <Send size={16} />
                Enviar orçamento
              </button>
            </div>
          ) : undefined
        }
      />
      <Proposal {...q} />
      {url && (
        <section className="panel share-panel">
          <div>
            <h2>Compartilhe sua proposta</h2>
            <p className="muted">
              Quem receber o link poderá visualizar e responder ao orçamento.
            </p>
          </div>
          <div className="share-actions">
            <button className="btn danger" onClick={() => setConfirmRevoke(true)}>
              Revogar link público
            </button>
            <button
              className="btn secondary"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(url);
                  notify("Link copiado.");
                } catch {
                  notify(
                    "Não foi possível copiar. Selecione o link abaixo.",
                    true,
                  );
                }
              }}
            >
              <Copy size={16} />
              Copiar link
            </button>
            <button
              className="btn whatsapp"
              onClick={() => {
                try {
                  const message = `Olá, ${q.customerName}! Segue o orçamento #${q.number} da ${q.companyName}, no valor de ${money(q.total)}. Você pode visualizar e responder pelo link: ${url}.`;
                  window.open(
                    whatsapp(q.customer.phone, message),
                    "_blank",
                    "noopener,noreferrer",
                  );
                } catch (e) {
                  notify(errorMessage(e), true);
                }
              }}
            >
              <MessageCircle size={17} />
              Enviar pelo WhatsApp
            </button>
            <Link
              className="btn secondary"
              to={`/o/${q.publicToken}`}
              target="_blank"
              rel="noreferrer"
            >
              <ExternalLink size={16} />
              Página pública
            </Link>
          </div>
          <input
            className="public-url"
            aria-label="Link público do orçamento"
            readOnly
            value={url}
            onFocus={(e) => e.target.select()}
          />
        </section>
      )}
      {q.publicRevokedAt && (
        <p role="status">Link público revogado em {dateTime(q.publicRevokedAt)}.</p>
      )}
      <section className="panel timeline-panel">
        <h2>Histórico do orçamento</h2>
        <ol className="timeline">
          <li>
            <Clock size={17} />
            <div>
              <strong>Orçamento criado</strong>
              <span>{dateTime(q.createdAt)}</span>
            </div>
          </li>
          {q.sentAt && (
            <li>
              <Send size={17} />
              <div>
                <strong>Link disponibilizado para envio</strong>
                <span>{dateTime(q.sentAt)}</span>
              </div>
            </li>
          )}
          {q.respondedAt && (
            <li>
              <CheckCircle2 size={17} />
              <div>
                <strong>
                  {q.status === "ACEITO"
                    ? "Orçamento aceito"
                    : "Orçamento recusado"}
                </strong>
                <span>{dateTime(q.respondedAt)}</span>
              </div>
            </li>
          )}
        </ol>
      </section>
      {confirmRevoke && (
        <Confirm title="Revogar link público?"
          description="O link deixará de permitir consultas e respostas. Esta ação é definitiva e preserva o histórico."
          busy={revoke.isPending} danger
          onClose={() => setConfirmRevoke(false)} onConfirm={() => revoke.mutate()} />
      )}
      {confirm && (
        <Confirm
          title="Enviar orçamento?"
          description="Após o envio, os itens e valores não poderão ser alterados. O link público será gerado para você compartilhar."
          busy={send.isPending}
          onClose={() => setConfirm(false)}
          onConfirm={() => send.mutate()}
        />
      )}
    </>
  );
}
