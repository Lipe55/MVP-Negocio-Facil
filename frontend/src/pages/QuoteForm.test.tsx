import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { vi, it, expect } from "vitest";
import { api } from "../api";
import QuoteForm from "./QuoteForm";
vi.mock("../api", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
  errorMessage: () => "Servidor indisponível",
}));
const setup = () =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={["/orcamentos/novo"]}>
        <Routes>
          <Route path="/orcamentos/novo" element={<QuoteForm />} />
          <Route path="/orcamentos/:id" element={<h1>Detalhes salvos</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
it("creates a quote through API with decimal strings and multiple items", async () => {
  vi.mocked(api.get).mockResolvedValue({
    data: { content: [{ id: 1, name: "Cliente real" }], totalElements: 1 },
  });
  vi.mocked(api.post).mockResolvedValue({ data: { id: 42 } });
  const user = userEvent.setup();
  setup();
  await screen.findByRole("option", { name: "Cliente real" });
  await user.selectOptions(
    screen.getByRole("combobox", { name: "Cliente" }),
    "1",
  );
  await user.type(screen.getByLabelText("Descrição do item 1"), "Consultoria");
  const price = screen.getByLabelText("Valor unitário (R$)");
  await user.clear(price);
  await user.type(price, "100.10");
  await user.click(screen.getByRole("button", { name: "Adicionar item" }));
  await user.type(screen.getByLabelText("Descrição do item 2"), "Material");
  await user.click(screen.getByRole("button", { name: "Salvar rascunho" }));
  await screen.findByText("Detalhes salvos");
  expect(api.post).toHaveBeenCalledWith(
    "/quotations",
    expect.objectContaining({
      customerId: 1,
      discount: "0.00",
      items: [
        { description: "Consultoria", quantity: "1", unitPrice: "100.10" },
        { description: "Material", quantity: "1", unitPrice: "0.00" },
      ],
    }),
  );
});
it("shows API errors and keeps the form available", async () => {
  vi.mocked(api.get).mockRejectedValue(new Error("network"));
  setup();
  await screen.findByText("Servidor indisponível");
  expect(screen.getByRole("button", { name: "Salvar rascunho" })).toBeEnabled();
});
it("shows customer loading while request is pending", () => {
  vi.mocked(api.get).mockReturnValue(new Promise(() => {}));
  setup();
  expect(screen.getByRole("combobox", { name: "Cliente" })).toBeDisabled();
  expect(
    screen.getByRole("option", { name: "Carregando…" }),
  ).toBeInTheDocument();
});
