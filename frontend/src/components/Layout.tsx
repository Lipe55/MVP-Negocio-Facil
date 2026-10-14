import { useState } from "react";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  FileText,
  Settings,
  Menu,
  LogOut,
  ChevronDown,
  Building2,
  X,
  Plus,
  ArrowUpRight,
} from "lucide-react";
import { useAuth } from "../auth";
import { useNotify } from "./ui";
import { errorMessage } from "../api";
import ThemeToggle from "./ThemeToggle";
const navigation = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/clientes", label: "Clientes", icon: Users },
  { to: "/orcamentos", label: "Orçamentos", icon: FileText },
  { to: "/configuracoes", label: "Configurações", icon: Settings },
];
export default function Layout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const notify = useNotify();
  const location = useLocation();
  const section = navigation.find((item) => location.pathname.startsWith(item.to))?.label ?? "Seu negócio";
  return (
    <div className="app-shell">
      {open && (
        <button
          className="nav-backdrop"
          aria-label="Fechar navegação"
          onClick={() => setOpen(false)}
        />
      )}
      <aside className={`sidebar ${open ? "is-open" : ""}`}>
        <NavLink className="brand" to="/dashboard">
          <Building2 size={25} />
          <span>
            negócio<span className="brand-light">fácil</span>
          </span>
        </NavLink>
        <span className="sidebar-caption">Seu negócio. Mais simples.</span>
        <button
          className="mobile-close icon-btn"
          onClick={() => setOpen(false)}
          aria-label="Fechar menu"
        >
          <X />
        </button>
        <span className="nav-label">GESTÃO DO NEGÓCIO</span>
        <nav aria-label="Menu principal">
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                isActive ? "nav-item active" : "nav-item"
              }
            >
              <Icon size={19} />
              {label}
            </NavLink>
          ))}
        </nav>
        <NavLink to="/orcamentos/novo" className="sidebar-create" onClick={() => setOpen(false)}>
          <Plus size={17} aria-hidden="true" /> Criar proposta
          <ArrowUpRight size={16} aria-hidden="true" />
        </NavLink>
        <div className="sidebar-footer">
          <div className="company-mark">
            <Building2 size={19} />
          </div>
          <div>
            <strong>{user?.companyName}</strong>
            <span>Seu espaço de trabalho</span>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <button
            className="mobile-menu icon-btn"
            aria-label="Abrir menu"
            aria-expanded={open}
            onClick={() => setOpen(!open)}
          >
            <Menu />
          </button>
          <div className="workspace-title">
            <span className="workspace-section">{section === "Dashboard" ? "Visão geral" : section}</span>
            <span className="workspace-company">{user?.companyName}</span>
          </div>
          <div className="topbar-actions">
            <ThemeToggle />
            <details className="profile-menu">
              <summary>
                <span className="avatar">
                  {user?.name.slice(0, 1).toUpperCase()}
                </span>
                <span>{user?.name}</span>
                <ChevronDown size={15} />
              </summary>
              <div className="profile-dropdown">
                <NavLink to="/configuracoes">Minha conta</NavLink>
                <button
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await logout();
                      navigate("/login");
                    } catch (e) {
                      notify(errorMessage(e), true);
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  <LogOut size={16} />
                  {busy ? "Saindo…" : "Sair"}
                </button>
              </div>
            </details>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
        <footer className="app-footer">
          Negócio Fácil · Gestão simples, negócios organizados.
        </footer>
      </div>
    </div>
  );
}
