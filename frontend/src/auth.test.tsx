import { render, screen, waitFor, act } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { vi, it, expect } from "vitest";
import { api } from "./api";
import { AuthProvider, ProtectedRoute } from "./auth";
vi.mock("./session-sync", () => ({ notifySessionChanged: vi.fn() }));
vi.mock("./api", () => ({
  api: { get: vi.fn(), post: vi.fn() },
  resetCsrf: vi.fn(),
  errorMessage: () => "Erro de conexão",
}));
function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/dashboard"]}>
        <AuthProvider>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<h1>Área privada</h1>} />
            </Route>
            <Route path="/login" element={<h1>Entrar</h1>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
it("redirects unauthenticated users away from private content", async () => {
  vi.mocked(api.get).mockResolvedValue({ data: null });
  setup();
  expect(await screen.findByText("Entrar")).toBeInTheDocument();
  expect(screen.queryByText("Área privada")).not.toBeInTheDocument();
});
it("renders private content after recovering a session", async () => {
  vi.mocked(api.get).mockResolvedValue({
    data: { id: 1, name: "User", companyId: 1 },
  });
  setup();
  expect(await screen.findByText("Área privada")).toBeInTheDocument();
});
it("does not redirect on network failure and exposes retry", async () => {
  vi.mocked(api.get).mockRejectedValue(new Error("network"));
  setup();
  expect(await screen.findByText("Erro de conexão")).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Tentar novamente" }),
  ).toBeInTheDocument();
});

it("clears private cached data and recovers the shared session after another tab changes it", async () => {
  vi.mocked(api.get).mockResolvedValueOnce({ data: { id: 1, name: "User", companyId: 1 } });
  const client = setup();
  await screen.findByText("Área privada");
  client.setQueryData(["customers"], { private: true });
  vi.mocked(api.get).mockResolvedValue({ data: null });
  await act(async () => { window.dispatchEvent(new Event("session-changed")); });
  await screen.findByText("Entrar");
  await waitFor(() => expect(client.getQueryData(["customers"])).toBeUndefined());
  expect(api.get).toHaveBeenCalledWith("/auth/me");
});
