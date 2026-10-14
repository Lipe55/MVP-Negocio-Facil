import { test, expect } from "@playwright/test";
test("register, create customer and multi-item quote, share, accept, and isolate tenants", async ({
  page,
  browser,
}) => {
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto("/cadastro");
  await page.getByLabel("Nome do responsável").fill("Ana Lima");
  await page.getByLabel("E-mail", { exact: true }).fill(email);
  await page.getByLabel("Senha", { exact: true }).fill("E2eSecurePassword123!");
  await page.getByLabel("Nome da empresa").fill("Oficina E2E");
  await page.getByLabel("Telefone da empresa").fill("11999999999");
  await page.getByRole("button", { name: "Criar conta" }).click();
  await expect(page).toHaveURL(/dashboard/);
  await page.getByRole("link", { name: "Clientes", exact: true }).click();
  await page.getByRole("button", { name: "Novo cliente" }).click();
  await page.getByLabel("Nome", { exact: true }).fill("Cliente E2E");
  await page.getByLabel("WhatsApp", { exact: true }).fill("11988888888");
  await page.getByRole("button", { name: "Salvar cliente" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("link", { name: "Orçamentos", exact: true }).click();
  await page.getByRole("link", { name: "Novo orçamento" }).click();
  await page
    .getByRole("combobox", { name: "Cliente", exact: true })
    .selectOption({ label: "Cliente E2E" });
  await page.getByLabel("Descrição do item 1").fill("Serviço");
  await page.getByLabel("Quantidade", { exact: true }).fill("2.5");
  await page.getByLabel("Valor unitário (R$)", { exact: true }).fill("100.10");
  await page.getByRole("button", { name: "Adicionar item" }).click();
  await page.getByLabel("Descrição do item 2").fill("Material");
  await page
    .getByLabel("Valor unitário (R$)", { exact: true })
    .nth(1)
    .fill("50.00");
  await page.getByLabel("Desconto (R$)").fill("10.00");
  await expect(page.getByText("R$ 290,25", { exact: true })).toBeVisible();
  await page.getByLabel("Condições comerciais (visíveis ao cliente)").fill("Pagamento em 30 dias.");
  await page.getByLabel("Notas internas (não compartilhadas)").fill("Nota reservada.");
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await expect(page).toHaveURL(/\/orcamentos\/\d+$/);
  const privateUrl = page.url();
  await page
    .getByRole("button", { name: "Enviar orçamento", exact: true })
    .click();
  await page.getByRole("button", { name: "Confirmar", exact: true }).click();
  const publicUrl = await page
    .getByLabel("Link público do orçamento")
    .inputValue();
  expect(publicUrl).toMatch(/\/o\/[A-Za-z0-9_-]{43}$/);
  const publicContext = await browser.newContext({
    baseURL: new URL(privateUrl).origin,
  });
  const publicPage = await publicContext.newPage();
  await publicPage.goto(publicUrl);
  await expect(
    publicPage.getByText("R$ 290,25", { exact: true }),
  ).toBeVisible();
  await expect(publicPage.getByText("Pagamento em 30 dias.")).toBeVisible();
  await expect(publicPage.getByText("Nota reservada.")).toHaveCount(0);
  await publicPage.getByRole("button", { name: "Aceitar orçamento" }).click();
  await publicPage
    .getByRole("button", { name: "Confirmar", exact: true })
    .click();
  await expect(
    publicPage.getByRole("heading", { name: "Orçamento aceito" }),
  ).toBeVisible();
  await expect(
    publicPage.getByRole("button", { name: "Aceitar orçamento" }),
  ).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("Aceito", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Editar", exact: true }),
  ).toHaveCount(0);
  await publicPage.goto("/cadastro");
  await publicPage.getByLabel("Nome do responsável").fill("Bruno");
  await publicPage.getByLabel("E-mail", { exact: true }).fill(`other-${email}`);
  await publicPage
    .getByLabel("Senha", { exact: true })
    .fill("E2eSecurePassword123!");
  await publicPage.getByLabel("Nome da empresa").fill("Outra empresa");
  await publicPage.getByLabel("Telefone da empresa").fill("11999999999");
  await publicPage.getByRole("button", { name: "Criar conta" }).click();
  await expect(publicPage).toHaveURL(/dashboard/);
  await publicPage.goto(privateUrl);
  await expect(publicPage.getByText("Registro não encontrado.")).toBeVisible();
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Olá, Ana." })).toBeVisible();
  await page.screenshot({
    path: "test-results/dashboard-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Olá, Ana." })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({
    path: "test-results/dashboard-mobile.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Abrir menu" }).click();
  await expect(
    page.getByRole("link", { name: "Clientes", exact: true }),
  ).toBeVisible();
  await page.goto(privateUrl);
  await page.getByRole("button", { name: "Revogar link público" }).click();
  await page.getByRole("button", { name: "Confirmar", exact: true }).click();
  await expect(page.getByText(/Link público revogado em/)).toBeVisible();
  await publicPage.goto(publicUrl);
  await expect(publicPage.getByText("Registro não encontrado.")).toBeVisible();
  await page.goto("/dashboard");
  const secondTab = await page.context().newPage();
  await secondTab.goto("/dashboard");
  await secondTab.getByText("Ana Lima", { exact: true }).click();
  await secondTab.getByRole("button", { name: "Sair", exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await secondTab.close();
  await publicContext.close();
});
