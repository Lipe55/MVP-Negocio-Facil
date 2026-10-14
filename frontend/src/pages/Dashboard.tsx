import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Users,
  FileText,
  Clock,
  CheckCircle2,
  Plus,
  ArrowRight,
  CalendarDays,
  ArrowUpRight,
} from "lucide-react";
import { api } from "../api";
import { useAuth } from "../auth";
import { Loading, ErrorState, PageHeader } from "../components/ui";
import QuotationTable from "../components/QuotationTable";
import type { Summary } from "../types";
import { date, today } from "../money";
export default function Dashboard() {
  const { user } = useAuth();
  const query = useQuery({
    queryKey: ["dashboard"],
    queryFn: async () => (await api.get<Summary>("/dashboard/summary")).data,
    refetchInterval: 30000,
  });
  if (query.isLoading) return <Loading />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  const data = query.data!;
  const stats = [
    {
      title: "Clientes ativos",
      value: data.customers,
      icon: Users,
      to: "/clientes",
      description: "Sua base de relacionamento",
    },
    {
      title: "Total de orçamentos",
      value: data.quotations,
      icon: FileText,
      to: "/orcamentos",
      description: "Todas as suas propostas",
    },
    {
      title: "Aguardando resposta",
      value: data.awaiting,
      icon: Clock,
      to: "/orcamentos?status=AGUARDANDO",
      description: "Propostas enviadas",
    },
    {
      title: "Orçamentos aceitos",
      value: data.accepted,
      icon: CheckCircle2,
      to: "/orcamentos?status=ACEITO",
      description: "Negócios aprovados",
    },
  ];
  return (
    <>
      <section className="dashboard-welcome">
        <div className="dashboard-overline">
          <span className="eyebrow">VISÃO GERAL DO NEGÓCIO</span>
          <span className="dashboard-date">
            <CalendarDays size={14} aria-hidden="true" />
            {date(today())}
          </span>
        </div>
        <PageHeader
          title={`Olá, ${user?.name.split(" ")[0]}.`}
          subtitle="Aqui está o panorama do seu negócio."
          action={
            <Link className="btn" to="/orcamentos/novo">
              <Plus size={18} />
              Novo orçamento
            </Link>
          }
        />
      </section>
      <div className="stats-grid">
        {stats.map(({ title, value, icon: Icon, to, description }) => (
          <Link className="stat-card" to={to} key={title}>
            <div className="stat-top">
              <span>{title}</span>
              <span className="stat-icon">
                <Icon size={18} />
              </span>
            </div>
            <strong>{value.toLocaleString("pt-BR")}</strong>
            <div className="stat-footer">
              <small>{description}</small>
              <ArrowUpRight size={15} aria-hidden="true" />
            </div>
          </Link>
        ))}
      </div>
      <section className="panel">
        <div className="panel-head">
          <div>
            <h2>Últimos orçamentos</h2>
            <p>Acompanhe suas propostas mais recentes.</p>
          </div>
          <Link className="text-link" to="/orcamentos">
            Ver todos <ArrowRight size={16} />
          </Link>
        </div>
        <QuotationTable rows={data.recent} />
      </section>
      <div className="quick-grid">
        <Link to="/clientes" className="quick-card">
          <Users size={21} />
          <div>
            <h3>Relacionamentos que crescem</h3>
            <p>Mantenha seus clientes organizados e por perto.</p>
          </div>
          <ArrowRight size={18} />
        </Link>
        <Link to="/orcamentos/novo" className="quick-card">
          <FileText size={21} />
          <div>
            <h3>Do orçamento ao próximo negócio</h3>
            <p>Crie uma proposta clara e pronta para compartilhar.</p>
          </div>
          <ArrowRight size={18} />
        </Link>
      </div>
    </>
  );
}
