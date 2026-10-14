import { createContext, useEffect, useContext, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import axios from "axios";
import { api, resetCsrf } from "./api";
import { notifySessionChanged } from "./session-sync";
import type { Me } from "./types";
import { Loading, ErrorState } from "./components/ui";
interface Auth {
  user: Me | null | undefined;
  loading: boolean;
  error: unknown;
  refresh: () => Promise<unknown>;
  logout: () => Promise<void>;
}
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const location = useLocation();
  useEffect(() => {
    const expired = () => {
      resetCsrf();
      client.clear();
      client.setQueryData(["session"], null);
    };
    const changed = () => {
      resetCsrf();
      // Cancel old-account requests before dropping all private cached data.
      void client.cancelQueries().then(() => {
        client.removeQueries({ predicate: (query) => query.queryKey[0] !== "session" });
        client.setQueryData(["session"], null);
        void client.invalidateQueries({ queryKey: ["session"] });
      });
    };
    window.addEventListener("session-expired", expired);
    window.addEventListener("session-changed", changed);
    return () => {
      window.removeEventListener("session-expired", expired);
      window.removeEventListener("session-changed", changed);
    };
  }, [client]);

  const session = useQuery({
    queryKey: ["session"],
    queryFn: async () => {
      try {
        return (await api.get<Me>("/auth/me")).data;
      } catch (e) {
        if (axios.isAxiosError(e) && e.response?.status === 401) return null;
        throw e;
      }
    },
    retry: false,
    enabled: !location.pathname.startsWith("/o/"),
  });
  const logout = async () => {
    await api.post("/auth/logout");
    notifySessionChanged();
    resetCsrf();
    client.clear();
    client.setQueryData(["session"], null);
  };
  return (
    <Context.Provider
      value={{
        user: session.data,
        loading: session.isLoading,
        error: session.error,
        refresh: session.refetch,
        logout,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export function useAuth() {
  const ctx = useContext(Context);
  if (!ctx) throw new Error("Missing AuthProvider");
  return ctx;
}
export function ProtectedRoute() {
  const { user, loading, error, refresh } = useAuth();
  const location = useLocation();
  if (loading) return <Loading />;
  if (error) return <ErrorState error={error} retry={() => void refresh()} />;
  return user ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ from: location.pathname }} replace />
  );
}
