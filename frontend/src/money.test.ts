import { describe, it, expect } from "vitest";
import { totals, itemCents, money, whatsapp, quantity } from "./money";
import { customerSchema, registerSchema, quoteSchema } from "./schemas";
describe("money", () => {
  it("formats fractional quantities in Portuguese without treating a decimal point as a thousands separator", () => {
    expect(quantity("2.500")).toBe("2,5");
    expect(quantity("1.000")).toBe("1");
  });

  it("calculates decimal quantities and discount without floating point drift", () => {
    expect(
      totals(
        [
          { quantity: "2.5", unitPrice: "100.10" },
          { quantity: "1", unitPrice: "50.00" },
        ],
        "10.00",
      ),
    ).toEqual({ subtotal: 30025n, discount: 1000n, total: 29025n });
    expect(money("290.25")).toBe("R$ 290,25");
  });
  it("matches backend HALF_UP item rounding", () => {
    expect(itemCents("0.005", "1.00")).toBe(1n);
  });
  it("encodes WhatsApp messages and normalizes Brazilian phone numbers", () => {
    expect(whatsapp("(11) 99999-9999", "Olá, você!")).toBe(
      "https://wa.me/5511999999999?text=Ol%C3%A1%2C%20voc%C3%AA!",
    );
    expect(() => whatsapp("123", "test")).toThrow();
  });
});
describe("forms", () => {
  it("rejects missing customer name and invalid email", () => {
    expect(
      customerSchema.safeParse({
        name: "",
        phone: "11999999999",
        email: "invalid",
        document: "",
        notes: "",
      }).success,
    ).toBe(false);
  });
  it("rejects weak passwords and invalid signup", () => {
    expect(
      registerSchema.safeParse({
        name: "User",
        email: "user@example.com",
        password: "123",
        companyName: "Company",
        companyPhone: "11999999999",
        companyDocument: "",
      }).success,
    ).toBe(false);
  });
  it("rejects excess discount, empty items and negative quantity", () => {
    const base = {
      customerId: "1",
      validUntil: "2099-01-01",
      notes: "",
      discount: "0",
      items: [{ description: "Item", quantity: "1", unitPrice: "10.00" }],
    };
    expect(quoteSchema.safeParse({ ...base, discount: "11" }).success).toBe(
      false,
    );
    expect(quoteSchema.safeParse({ ...base, items: [] }).success).toBe(false);
    expect(
      quoteSchema.safeParse({
        ...base,
        items: [{ description: "Item", quantity: "-1", unitPrice: "10" }],
      }).success,
    ).toBe(false);
  });
});
