import { Routes, Route, Navigate, Link } from "react-router-dom";
import { AuthProvider, ProtectedRoute } from "./auth";
import { Notifications } from "./components/ui";
import Layout from "./components/Layout";
import AuthPage from "./pages/AuthPage";
import Dashboard from "./pages/Dashboard";
import Customers from "./pages/Customers";
import Quotations from "./pages/Quotations";
import QuoteForm from "./pages/QuoteForm";
import QuoteDetail from "./pages/QuoteDetail";
import PublicQuote from "./pages/PublicQuote";
import Settings from "./pages/Settings";
import FinanceMovements from "./finance/Movements";
import FinanceAccounts from "./finance/Accounts";
import FinanceCharts from "./finance/charts/Charts";
export default function App() {
  return (
    <Notifications>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/cadastro" element={<AuthPage register />} />
          <Route path="/o/:token" element={<PublicQuote />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/clientes" element={<Customers />} />
              <Route path="/orcamentos" element={<Quotations />} />
              <Route path="/orcamentos/novo" element={<QuoteForm />} />
              <Route path="/orcamentos/:id/editar" element={<QuoteForm />} />
              <Route path="/orcamentos/:id" element={<QuoteDetail />} />
              <Route path="/financeiro" element={<Navigate to="/financeiro/movimentacoes" replace />} />
              <Route path="/financeiro/movimentacoes" element={<FinanceMovements />} />
              <Route path="/financeiro/graficos" element={<FinanceCharts />} />
              <Route path="/financeiro/contas" element={<FinanceAccounts />} />
              <Route path="/configuracoes" element={<Settings />} />
            </Route>
          </Route>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route
            path="*"
            element={
              <main className="not-found">
                <h1>Página não encontrada</h1>
                <Link to="/dashboard" className="btn">
                  Voltar ao início
                </Link>
              </main>
            }
          />
        </Routes>
      </AuthProvider>
    </Notifications>
  );
}
