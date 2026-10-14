import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import Proposal from "./Proposal";

it("shows commercial conditions and distinguishes administrative notes", () => {
  render(<Proposal number={1} companyName="Empresa original" customerName="Cliente original"
    status="ACEITO" validUntil="2099-01-01" items={[]}
    subtotal="10.00" discount="0.00" total="10.00"
    terms="Pagamento em 30 dias." notes="Informação interna." />);
  expect(screen.getByText("Pagamento em 30 dias.")).toBeInTheDocument();
  expect(screen.getByText("Condições comerciais")).toBeInTheDocument();
  expect(screen.getByText("Notas internas (não compartilhadas)")).toBeInTheDocument();
  expect(screen.getByText("Empresa original")).toBeInTheDocument();
  expect(screen.getByText("Cliente original")).toBeInTheDocument();
});
