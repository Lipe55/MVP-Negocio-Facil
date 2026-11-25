import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { api, errorMessage } from "../api";
import { Empty, ErrorState, Field, Loading, PageHeader, Pagination, useNotify } from "../components/ui";
import { date, money } from "../money";
import type { Page } from "../types";
import "./fiscal.css";

const statuses = {
  VALIDA: "Válida no XML",
  CANCELADA: "Cancelada no XML",
  SUBSTITUIDA: "Substituída no XML",
  NAO_VERIFICADA: "Não verificada",
};

interface Note {
  id: number;
  number: string;
  accessKey: string;
  issuedOn: string;
  competence: string | null;
  customerName: string | null;
  recipientName: string | null;
  description: string | null;
  gross: string | null;
  net: string | null;
  status: keyof typeof statuses;
}

interface Summary {
  count: number;
  gross: string;
  valid: number;
  canceled: number;
  unverified: number;
  replaced: number;
}

interface Links {
  customerId: number | null;
  quotationId: number | null;
  quotationItemId: number | null;
  obligationId: number | null;
}

interface Detail {
  note: Note;
  links: Links;
  providerDocument: string;
  providerName: string | null;
  recipientDocument: string | null;
  municipality: string | null;
  serviceCode: string | null;
  amounts: Record<string, string>;
}

const amountLabels: Record<string, string> = {
  vBC: "Base de cálculo", vISSQN: "ISS", vCalcBM: "Benefício municipal", vCalcDR: "Dedução ou redução calculada",
  vTotalRet: "Total retido", vDescCond: "Desconto condicionado", vDescIncond: "Desconto incondicionado",
  vDR: "Dedução ou redução declarada", vRetIRRF: "IRRF retido", vRetCSLL: "CSLL retida", vRetCP: "Contribuição previdenciária retida",
  vPis: "PIS", vCofins: "Cofins", vTotTribFed: "Tributos federais", vTotTribEst: "Tributos estaduais", vTotTribMun: "Tributos municipais",
  vTotNF: "Total da nota informado com IBS/CBS", vIBSTot: "IBS total", vIBSMun: "IBS municipal", vIBSUF: "IBS estadual", vCBS: "CBS",
};

function amount(value: string | null) {
  return value == null ? "—" : money(value);
}

function ImportXml({ onSuccess }: { onSuccess: () => void }) {
  const notify = useNotify();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File>();
  const [validation, setValidation] = useState("");
  const upload = useMutation({
    mutationFn: async (selected: File) => {
      const form = new FormData();
      form.append("file", selected);
      await api.post("/fiscal/notas/importar", form);
    },
    onSuccess: () => {
      setFile(undefined);
      if (input.current) input.current.value = "";
      notify("Nota fiscal importada.");
      onSuccess();
    },
  });
  return <form className="panel fiscal-toolbar" onSubmit={(event) => {
    event.preventDefault();
    if (file && !validation) upload.mutate(file);
  }}>
    <Field label="XML da NFS-e nacional" error={validation || undefined}>
      <input type="file" accept=".xml,application/xml,text/xml" ref={input} disabled={upload.isPending}
        onChange={(event) => {
          const selected = event.target.files?.[0];
          upload.reset();
          setFile(selected);
          setValidation(!selected ? "" : !selected.name.toLowerCase().endsWith(".xml") || selected.size === 0
            ? "Selecione um arquivo XML com conteúdo." : selected.size > 1_048_576 ? "O arquivo deve ter até 1 MiB." : "");
        }} />
    </Field>
    <button className="btn" disabled={!file || !!validation || upload.isPending}>
      {upload.isPending ? "Importando…" : "Importar XML"}
    </button>
    {upload.error && <p role="alert" className="field-error">{errorMessage(upload.error)}</p>}
  </form>;
}

export default function Fiscal() {
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState({
    search: "", from: "", to: "", competenceFrom: "", competenceTo: "", status: "", customerId: "",
  });
  const [order, setOrder] = useState("EMISSAO_RECENTE");
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ""));
  const list = useQuery({
    queryKey: ["fiscal", "list", params, page, order],
    queryFn: async ({ signal }) => (await api.get<Page<Note>>("/fiscal/notas", { params: { ...params, page, order }, signal })).data,
  });
  const summary = useQuery({
    queryKey: ["fiscal", "summary", params],
    queryFn: async ({ signal }) => (await api.get<Summary>("/fiscal/resumo", { params, signal })).data,
  });

  function filter(key: keyof typeof filters, value: string) {
    setPage(0);
    setFilters((previous) => ({ ...previous, [key]: value }));
  }

  return <section className="fiscal-page">
    <PageHeader title="Fiscal" subtitle="Importe, consulte e baixe suas notas fiscais de serviço." />
    <p className="muted">O módulo importa documentos existentes. Emissão, cancelamento e transmissão ficam fora desta versão. A situação exibida vem do XML e a autenticidade não é consultada. O valor das notas representa faturamento documentado, independentemente de pagamento.</p>
    <ImportXml onSuccess={() => {
      setPage(0);
      void cache.invalidateQueries({ queryKey: ["fiscal"] });
    }} />
    <div className="panel fiscal-toolbar" aria-label="Filtros das notas">
      <Field label="Buscar nota"><input value={filters.search} maxLength={200} onChange={(e) => filter("search", e.target.value)} /></Field>
      <Field label="Emissão inicial"><input type="date" value={filters.from} onChange={(e) => filter("from", e.target.value)} /></Field>
      <Field label="Emissão final"><input type="date" value={filters.to} onChange={(e) => filter("to", e.target.value)} /></Field>
      <Field label="Competência inicial"><input type="date" value={filters.competenceFrom} onChange={(e) => filter("competenceFrom", e.target.value)} /></Field>
      <Field label="Competência final"><input type="date" value={filters.competenceTo} onChange={(e) => filter("competenceTo", e.target.value)} /></Field>
      <Field label="Situação"><select value={filters.status} onChange={(e) => filter("status", e.target.value)}>
        <option value="">Todas</option>
        {Object.entries(statuses).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
      </select></Field>
      <Field label="Ordenar por"><select value={order} onChange={(e) => { setOrder(e.target.value); setPage(0); }}>
        <option value="EMISSAO_RECENTE">Emissão mais recente</option>
        <option value="EMISSAO_ANTIGA">Emissão mais antiga</option>
        <option value="MAIOR_VALOR">Maior valor bruto</option>
      </select></Field>
      <LinkPicker label="Cliente" kind="clientes" emptyLabel="Todos os clientes"
        value={filters.customerId ? Number(filters.customerId) : null}
        onChange={(id) => filter("customerId", id == null ? "" : String(id))} />
    </div>
    {summary.isPending ? <Loading /> : summary.error ? <ErrorState error={summary.error} retry={() => void summary.refetch()} /> :
      <dl className="fiscal-stats">
        <div className="panel"><dt>Notas no resultado</dt><dd>{summary.data.count}</dd></div>
        <div className="panel"><dt>Valor bruto das notas</dt><dd>{money(summary.data.gross)}</dd></div>
        <div className="panel"><dt>Válidas no XML</dt><dd>{summary.data.valid}</dd></div>
        <div className="panel"><dt>Canceladas no XML</dt><dd>{summary.data.canceled}</dd></div>
        <div className="panel"><dt>Não verificadas</dt><dd>{summary.data.unverified}</dd></div>
        <div className="panel"><dt>Substituídas no XML</dt><dd>{summary.data.replaced}</dd></div>
      </dl>}
    {list.isPending ? <Loading /> : list.error ? <ErrorState error={list.error} retry={() => void list.refetch()} /> :
      <div className="panel">
        {list.data.content.length ? <div className="table-wrap"><table>
          <thead><tr><th>Número</th><th>Cliente ou tomador</th><th>Emissão</th><th>Descrição</th><th>Valor bruto</th><th>Situação</th><th>Ações</th></tr></thead>
          <tbody>{list.data.content.map((note) => <tr key={note.id}>
            <td>{note.number}</td><td>{note.customerName ?? note.recipientName ?? "—"}</td>
            <td>{date(note.issuedOn)}</td><td className="fiscal-description">{note.description?.slice(0, 120) || "—"}</td>
            <td>{amount(note.gross)}</td><td>{statuses[note.status]}</td>
            <td><Link className="btn secondary small" to={`/fiscal/${note.id}`} aria-label={`Detalhes da nota ${note.number}`}>Detalhes</Link></td>
          </tr>)}</tbody>
        </table></div> : <Empty title="Nenhuma nota encontrada">Importe um XML ou ajuste os filtros.</Empty>}
        <Pagination page={page} totalPages={list.data.totalPages} total={list.data.totalElements} onChange={setPage} />
      </div>}
  </section>;
}

type LinkKind = "clientes" | "orcamentos" | "servicos" | "receber";

function LinkPicker({ label, kind, value, onChange, quotationId, emptyLabel = "Sem vínculo" }: {
  label: string; kind: LinkKind; value: number | null; onChange: (id: number | null) => void;
  quotationId?: number | null; emptyLabel?: string;
}) {
  const [search, setSearch] = useState("");
  const enabled = kind !== "servicos" || quotationId != null;
  const query = useQuery({
    queryKey: ["fiscal", "options", kind, search, quotationId, value],
    enabled,
    queryFn: async ({ signal }) => (await api.get<{ id: number; label: string }[]>(`/fiscal/vinculos/${kind}`, {
      params: { search, quotationId, selected: value }, signal,
    })).data,
  });
  return <div className="fiscal-picker">
    <Field label={label}><select value={value ?? ""} disabled={!enabled}
      onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
      <option value="">{emptyLabel}</option>
      {value != null && !query.data?.some((item) => item.id === value) && <option value={value}>Registro #{value}</option>}
      {query.data?.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
    </select></Field>
    <input aria-label={`Buscar ${label.toLowerCase()}`} placeholder="Buscar opções…" maxLength={200}
      value={search} disabled={!enabled} onChange={(e) => setSearch(e.target.value)} />
    {enabled && query.isFetching && <small role="status">Buscando opções…</small>}
    {query.error && <ErrorState error={query.error} retry={() => void query.refetch()} />}
  </div>;
}

function LinkForm({ invoice }: { invoice: Detail }) {
  const [links, setLinks] = useState(invoice.links);
  const cache = useQueryClient();
  const notify = useNotify();
  const save = useMutation({
    mutationFn: async () => (await api.patch<Detail>(`/fiscal/notas/${invoice.note.id}/vinculos`, links)).data,
    onSuccess: (detail) => {
      cache.setQueryData(["fiscal", "detail", String(invoice.note.id)], detail);
      void cache.invalidateQueries({ queryKey: ["fiscal", "list"] });
      void cache.invalidateQueries({ queryKey: ["fiscal", "summary"] });
      notify("Vínculos atualizados.");
    },
  });
  return <form className="panel fiscal-page" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
    <h2>Vínculos</h2>
    <p className="muted">Selecione registros existentes. Vincular uma conta a receber preserva seus valores e pagamentos.</p>
    <fieldset className="fiscal-toolbar" disabled={save.isPending}>
      <LinkPicker label="Cliente" kind="clientes" value={links.customerId} onChange={(customerId) => setLinks({ ...links, customerId })} />
      <LinkPicker label="Orçamento" kind="orcamentos" value={links.quotationId}
        onChange={(quotationId) => setLinks({ ...links, quotationId, quotationItemId: null })} />
      <LinkPicker label="Serviço do orçamento" kind="servicos" value={links.quotationItemId}
        quotationId={links.quotationId} onChange={(quotationItemId) => setLinks({ ...links, quotationItemId })} />
      <LinkPicker label="Conta a receber" kind="receber" value={links.obligationId} onChange={(obligationId) => setLinks({ ...links, obligationId })} />
      <button className="btn">{save.isPending ? "Salvando…" : "Salvar vínculos"}</button>
    </fieldset>
    {save.error && <p role="alert" className="field-error">{errorMessage(save.error)}</p>}
  </form>;
}

function Documents({ id }: { id: number }) {
  const [problem, setProblem] = useState("");
  const [busy, setBusy] = useState(false);
  const readiness = useQuery({
    queryKey: ["fiscal", "readiness"], retry: false,
    queryFn: async () => (await api.get<{ available: boolean }>("/fiscal/gerador")).data.available,
  });
  async function open(kind: "xml" | "danfse", download: boolean) {
    setProblem("");
    const viewer = download ? null : window.open("about:blank", "_blank");
    if (!download && !viewer) {
      setProblem("Permita a abertura de uma nova aba ou baixe o PDF.");
      return;
    }
    if (viewer) viewer.opener = null;
    setBusy(true);
    try {
      const body = (await api.get<Blob>(`/fiscal/notas/${id}/${kind}`, {
        responseType: "blob", params: kind === "danfse" ? { download } : undefined,
      })).data;
      // Keep the XML as an attachment and let the browser's PDF viewer show the PDF.
      const url = URL.createObjectURL(body);
      if (viewer) {
        viewer.location.replace(url);
      } else {
        const a = document.createElement("a");
        a.href = url;
        a.download = kind === "xml" ? `nfse-${id}.xml` : `danfse-${id}.pdf`;
        a.click();
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      viewer?.close();
      const data = (error as { response?: { data?: unknown } }).response?.data;
      let message = errorMessage(error);
      if (data instanceof Blob) {
        try { message = JSON.parse(await data.text()).message || message; } catch { /* fallback above */ }
      }
      setProblem(message);
      if (kind === "danfse") void readiness.refetch();
    } finally {
      setBusy(false);
    }
  }
  return <div className="panel fiscal-page">
    <h2>Documentos</h2>
    <div className="fiscal-toolbar">
      <button className="btn secondary" disabled={busy} onClick={() => void open("xml", true)}>Baixar XML</button>
      <button className="btn secondary" disabled={busy} onClick={() => void open("danfse", false)}>Visualizar DANFSe</button>
      <button className="btn secondary" disabled={busy} onClick={() => void open("danfse", true)}>Baixar DANFSe</button>
    </div>
    {busy && <p role="status">Preparando documento…</p>}
    {readiness.data === false && <p role="status">O gerador de DANFSe está indisponível. O XML continua disponível.</p>}
    {problem && <p role="alert" className="field-error">{problem}</p>}
  </div>;
}

export function FiscalDetail() {
  const { id } = useParams();
  const query = useQuery({
    queryKey: ["fiscal", "detail", id],
    queryFn: async ({ signal }) => (await api.get<Detail>(`/fiscal/notas/${id}`, { signal })).data,
  });
  if (query.isPending) return <Loading />;
  if (query.error) return <ErrorState error={query.error} retry={() => void query.refetch()} />;
  const invoice = query.data;
  const { note } = invoice;
  const fields = [
    ["Chave de acesso", note.accessKey], ["Situação", statuses[note.status]], ["Origem", "Importação manual"],
    ["Emissão", date(note.issuedOn)], ["Competência", note.competence && date(note.competence)],
    ["Prestador", invoice.providerName], ["CPF ou CNPJ do prestador", invoice.providerDocument],
    ["Tomador", note.recipientName], ["CPF ou CNPJ do tomador", invoice.recipientDocument],
    ["Município da prestação", invoice.municipality], ["Código do serviço", invoice.serviceCode],
    ["Valor bruto", amount(note.gross)], ["Valor líquido", amount(note.net)], ["Descrição", note.description],
    ...Object.entries(invoice.amounts).map(([key, value]) => [amountLabels[key] ?? key, money(value)]),
  ];
  return <section className="fiscal-page">
    <PageHeader title={`NFS-e ${note.number}`} subtitle="Documento importado do XML. Autenticidade não consultada."
      action={<Link to="/fiscal" className="btn secondary">Voltar às notas</Link>} />
    <dl className="panel fiscal-data">{fields.map(([label, value]) =>
      <div key={label}><dt>{label}</dt><dd>{value || "—"}</dd></div>)}</dl>
    <Documents key={note.id} id={note.id} />
    <LinkForm key={note.id} invoice={invoice} />
  </section>;
}
