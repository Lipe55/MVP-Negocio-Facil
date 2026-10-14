import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, X, Building2, CheckCircle2, ShieldCheck } from "lucide-react";
import { api, errorMessage } from "../api";
import type { PublicQuotation } from "../types";
import { Loading, ErrorState, Confirm, useNotify } from "../components/ui";
import { dateTime } from "../money";
import Proposal from "../components/Proposal";
import ThemeToggle from "../components/ThemeToggle";
export default function PublicQuote() {
  const { token } = useParams();
  const client = useQueryClient();
  const notify = useNotify();
  const [choice, setChoice] = useState<"accept" | "reject" | null>(null);
  const query = useQuery({
    queryKey: ["public-quote", token],
    queryFn: async () =>
      (await api.get<PublicQuotation>(`/public/quotations/${token}`)).data,
    retry: false,
    refetchInterval: 30000,
  });
  const mutation = useMutation({
    mutationFn: async () =>
      (await api.post<PublicQuotation>(`/public/quotations/${token}/${choice}`))
        .data,
    onSuccess: (data) => {
      client.setQueryData(["public-quote", token], data);
      setChoice(null);
      notify("Sua resposta foi registrada.");
    },
    onError: (e) => {
      notify(errorMessage(e), true);
      setChoice(null);
      void query.refetch();
    },
  });
  const q = query.data;
  return (
    <div className="public-page">
      <header className="public-header">
        <span className="brand">
          <Building2 size={24} />
          <span>
            negócio<span className="brand-light">fácil</span>
          </span>
        </span>
        <div className="public-header-actions">
          <span className="muted public-label">
            <ShieldCheck size={16} />
            Proposta comercial
          </span>
          <ThemeToggle />
        </div>
      </header>
      <main className="public-content">
        {query.isLoading ? (
          <Loading />
        ) : query.error ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          q && (
            <>
              <div className="public-intro">
                <span className="eyebrow">UMA PROPOSTA PARA VOCÊ</span>
                <h1>Olá, {q.customerName}.</h1>
                <p>Confira o orçamento preparado por {q.companyName}.</p>
              </div>
              <Proposal {...q} />
              {q.status === "AGUARDANDO" ? (
                <section className="panel public-response">
                  <h2>Vamos dar o próximo passo?</h2>
                  <p>Revise os itens e valores antes de responder.</p>
                  <div className="actions">
                    <button
                      className="btn secondary"
                      onClick={() => setChoice("reject")}
                    >
                      <X size={18} />
                      Recusar orçamento
                    </button>
                    <button className="btn" onClick={() => setChoice("accept")}>
                      <Check size={18} />
                      Aceitar orçamento
                    </button>
                  </div>
                </section>
              ) : (
                <section className="panel response-result" role="status">
                  <CheckCircle2 size={30} />
                  <h2>
                    {q.status === "ACEITO"
                      ? "Orçamento aceito"
                      : q.status === "RECUSADO"
                        ? "Orçamento recusado"
                        : q.status === "EXPIRADO"
                          ? "Este orçamento expirou"
                          : "Orçamento indisponível"}
                  </h2>
                  <p>
                    {q.respondedAt
                      ? `Resposta registrada em ${dateTime(q.respondedAt)}. Entre em contato com a empresa para os próximos passos.`
                      : "Entre em contato com a empresa para solicitar uma nova proposta."}
                  </p>
                </section>
              )}
              <p className="commercial-note">
                A resposta pelo link é uma confirmação comercial simples. Ela
                não verifica a identidade de quem responde e não constitui
                assinatura digital com identidade validada.
              </p>
            </>
          )
        )}
      </main>
      <footer className="public-footer">
        Proposta compartilhada com Negócio Fácil.
      </footer>
      {choice && (
        <Confirm
          title={
            choice === "accept"
              ? "Aceitar este orçamento?"
              : "Recusar este orçamento?"
          }
          description="Sua resposta será enviada à empresa e não poderá ser alterada por este link."
          busy={mutation.isPending}
          onClose={() => setChoice(null)}
          onConfirm={() => mutation.mutate()}
          danger={choice === "reject"}
        />
      )}
    </div>
  );
}
