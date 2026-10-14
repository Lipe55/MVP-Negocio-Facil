import {
  useEffect,
  useRef,
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { AlertCircle, CheckCircle2, FileText, X } from "lucide-react";
import { errorMessage } from "../api";
import type { Status } from "../types";
export const labels: Record<Status, string> = {
  RASCUNHO: "Rascunho",
  AGUARDANDO: "Aguardando",
  ACEITO: "Aceito",
  RECUSADO: "Recusado",
  EXPIRADO: "Expirado",
};
export function Badge({ status }: { status: Status }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      <span aria-hidden="true" />
      {labels[status]}
    </span>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status" aria-label="Carregando">
      <div className="skeleton h-8 w-48" />
      <div className="skeleton h-28" />
      <div className="skeleton h-48" />
      <span className="sr-only">Carregando dados…</span>
    </div>
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={24} />
      <p>{errorMessage(error)}</p>
      {retry && (
        <button className="btn secondary" onClick={retry}>
          Tentar novamente
        </button>
      )}
    </div>
  );
}
export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <FileText size={25} />
      </div>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  );
}
export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {action}
    </div>
  );
}
export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {error && (
        <small className="field-error" role="alert">
          {error}
        </small>
      )}
    </label>
  );
}
export function Pagination({
  page,
  totalPages,
  total,
  onChange,
}: {
  page: number;
  totalPages: number;
  total: number;
  onChange: (p: number) => void;
}) {
  return (
    <div className="pagination">
      <span>
        {total} registro{total === 1 ? "" : "s"} · Página {page + 1} de{" "}
        {Math.max(1, totalPages)}
      </span>
      <div>
        <button
          className="btn secondary small"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          Anterior
        </button>
        <button
          className="btn secondary small"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Próxima
        </button>
      </div>
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={onClose}
      aria-labelledby="modal-title"
      className="modal"
    >
      <div className="modal-head">
        <h2 id="modal-title">{title}</h2>
        <button className="icon-btn" aria-label="Fechar" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Confirm({
  title,
  description,
  busy,
  onConfirm,
  onClose,
  danger = false,
}: {
  title: string;
  description: string;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
  danger?: boolean;
}) {
  return (
    <Modal title={title} onClose={onClose}>
      <p className="muted">{description}</p>
      <div className="actions mt-6">
        <button className="btn secondary" onClick={onClose} disabled={busy}>
          Cancelar
        </button>
        <button
          className={`btn ${danger ? "danger" : ""}`}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy ? "Aguarde…" : "Confirmar"}
        </button>
      </div>
    </Modal>
  );
}
const ToastContext = createContext<(message: string, error?: boolean) => void>(
  () => {},
);
export function Notifications({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{
    message: string;
    error: boolean;
  } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(timer);
  }, [toast]);
  return (
    <ToastContext.Provider
      value={(message, error = false) => setToast({ message, error })}
    >
      {children}
      {toast && (
        <div
          className={`toast ${toast.error ? "toast-error" : ""}`}
          role={toast.error ? "alert" : "status"}
        >
          {toast.error ? <AlertCircle size={19} /> : <CheckCircle2 size={19} />}
          <span>{toast.message}</span>
          <button aria-label="Fechar mensagem" onClick={() => setToast(null)}>
            <X size={17} />
          </button>
        </div>
      )}
    </ToastContext.Provider>
  );
}
export const useNotify = () => useContext(ToastContext);
