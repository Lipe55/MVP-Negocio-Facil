import { z } from "zod";
import { scaled, totals, today } from "./money";
export const name = z
  .string()
  .trim()
  .min(1, "Campo obrigatório.")
  .max(160, "Máximo de 160 caracteres.");
export const phone = z
  .string()
  .min(10, "Informe DDD e telefone.")
  .max(20)
  .regex(/^[+0-9() .-]+$/, "Telefone inválido.");
export const email = z.string().trim().email("E-mail inválido.").max(254);
export const password = z
  .string()
  .min(10, "Use pelo menos 10 caracteres.")
  .max(64)
  .refine(
    (v) => new TextEncoder().encode(v).length <= 72,
    "Senha excede 72 bytes.",
  );
export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Informe sua senha.").max(64),
});
export const registerSchema = z.object({
  name,
  email,
  password,
  companyName: name,
  companyPhone: phone,
  companyDocument: z.string().max(20),
});
export const customerSchema = z.object({
  name,
  phone,
  email: z.union([email, z.literal("")]),
  document: z.string().max(20),
  notes: z.string().max(3000),
});
const numeric = (precision: number, positive = false) =>
  z.string().refine(
    (v) => {
      try {
        const n = scaled(v, precision);
        return positive ? n > 0n : n >= 0n;
      } catch {
        return false;
      }
    },
    positive
      ? "Informe uma quantidade maior que zero (até 3 casas)."
      : "Informe um valor não negativo (até 2 casas).",
  );
export const quoteSchema = z
  .object({
    customerId: z.string().min(1, "Selecione um cliente."),
    validUntil: z
      .string()
      .refine(
        (v) => /^\d{4}-\d{2}-\d{2}$/.test(v) && v >= today(),
        "Informe uma validade a partir de hoje.",
      ),
    notes: z.string().max(3000),
    terms: z.string().max(3000).default(""),
    discount: numeric(2),
    items: z
      .array(
        z.object({
          description: z.string().trim().min(1, "Descreva o item.").max(500),
          quantity: numeric(3, true),
          unitPrice: numeric(2),
        }),
      )
      .min(1, "Adicione ao menos um item.")
      .max(100),
  })
  .superRefine((v, ctx) => {
    try {
      if (totals(v.items, v.discount).total < 0n)
        ctx.addIssue({
          code: "custom",
          path: ["discount"],
          message: "Desconto não pode superar o subtotal.",
        });
    } catch {
    }
  });
export type QuoteInput = z.infer<typeof quoteSchema>;
export type CustomerInput = z.infer<typeof customerSchema>;
