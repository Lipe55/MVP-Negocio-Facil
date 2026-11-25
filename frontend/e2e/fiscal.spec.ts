import { expect, test } from "@playwright/test";

const xml = Buffer.from(`<?xml version="1.0" encoding="UTF-8"?>
<NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.00">
  <infNFSe Id="NFS${"9".padStart(50, "0")}">
    <nNFSe>9</nNFSe><dhProc>2026-09-01T13:00:00-03:00</dhProc><cStat>100</cStat>
    <emit><CNPJ>12345678000100</CNPJ><xNome>Prestador sintético</xNome></emit>
    <valores><vLiq>110.20</vLiq></valores>
    <DPS><infDPS>
      <dCompet>2026-09-01</dCompet><tpAmb>2</tpAmb>
      <toma><xNome>Tomador sintético</xNome></toma>
      <serv><cServ><xDescServ>Serviço sintético</xDescServ></cServ></serv>
      <valores><vServPrest><vServ>115.30</vServ></vServPrest></valores>
    </infDPS></DPS>
  </infNFSe>
</NFSe>`);

test("importa, filtra e baixa uma nota com o gerador fiscal real", async ({ page }) => {
  await page.goto("/cadastro");
  await page.getByLabel("Nome do responsável").fill("Responsável Fiscal E2E");
  await page.getByLabel("E-mail", { exact: true }).fill(`fiscal-e2e-${Date.now()}@example.com`);
  await page.getByLabel("Senha", { exact: true }).fill("E2eSecurePassword123!");
  await page.getByLabel("Nome da empresa").fill("Fiscal E2E");
  await page.getByLabel("Telefone da empresa").fill("11999999999");
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page).toHaveURL(/dashboard/);

  await page.getByRole("link", { name: "Fiscal", exact: true }).click();
  const file = page.getByLabel("XML da NFS-e nacional");
  const document = { name: "synthetic.xml", mimeType: "application/xml", buffer: xml };
  await file.setInputFiles(document);
  await page.getByRole("button", { name: "Importar XML" }).click();
  await expect(page.getByText("Nota fiscal importada.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Detalhes da nota 9" })).toBeVisible();

  await file.setInputFiles(document);
  await page.getByRole("button", { name: "Importar XML" }).click();
  await expect(page.getByText("Esta nota fiscal já foi importada.")).toBeVisible();

  await page.getByLabel("Situação").selectOption("CANCELADA");
  await expect(page.getByText("Nenhuma nota encontrada")).toBeVisible();
  await page.getByLabel("Situação").selectOption("VALIDA");
  await page.getByRole("link", { name: "Detalhes da nota 9" }).click();
  await expect(page.getByRole("heading", { name: "NFS-e 9" })).toBeVisible();

  const xmlDownload = page.waitForEvent("download");
  await page.getByRole("button", { name: "Baixar XML" }).click();
  expect((await xmlDownload).suggestedFilename()).toMatch(/^nfse-\d+\.xml$/);

  const preview = page.waitForEvent("popup");
  const previewResponse = page.waitForResponse((response) => response.url().includes("/danfse?download=false"));
  await page.getByRole("button", { name: "Visualizar DANFSe" }).click();
  expect((await previewResponse).headers()["content-type"]).toContain("application/pdf");
  const popup = await preview;
  await popup.waitForURL(/^blob:/);
  await popup.close();

  const pdfResponse = page.waitForResponse((response) => response.url().includes("/danfse?download=true"));
  const pdfDownload = page.waitForEvent("download");
  await page.getByRole("button", { name: "Baixar DANFSe" }).click();
  const response = await pdfResponse;
  expect(response.ok()).toBeTruthy();
  expect((await response.body()).subarray(0, 5).toString()).toBe("%PDF-");
  expect((await pdfDownload).suggestedFilename()).toMatch(/^danfse-\d+\.pdf$/);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("link", { name: "Voltar às notas" }).click();
  await expect(page.getByRole("link", { name: "Detalhes da nota 9" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
