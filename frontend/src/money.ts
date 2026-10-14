/** Decimal input -> fixed-point integers. Item totals round HALF_UP like the API. */
export function scaled(value: string, precision: number): bigint {
  if (!/^\d+(?:\.\d+)?$/.test(value))
    throw new Error("Informe um número válido.");
  const [whole, fraction = ""] = value.split(".");
  if (fraction.length > precision)
    throw new Error(`Use no máximo ${precision} casas decimais.`);
  return (
    BigInt(whole) * 10n ** BigInt(precision) +
    BigInt(fraction.padEnd(precision, "0") || "0")
  );
}
export function itemCents(quantity: string, unitPrice: string): bigint {
  return (scaled(quantity, 3) * scaled(unitPrice, 2) + 500n) / 1000n;
}
export function totals(
  items: { quantity: string; unitPrice: string }[],
  discount: string,
) {
  const subtotal = items.reduce(
    (sum, item) => sum + itemCents(item.quantity, item.unitPrice),
    0n,
  );
  const discountCents = scaled(discount || "0", 2);
  return { subtotal, discount: discountCents, total: subtotal - discountCents };
}
export function decimal(cents: bigint): string {
  return `${cents / 100n}.${(cents % 100n).toString().padStart(2, "0")}`;
}
export function money(value: string | bigint): string {
  const cents = typeof value === "bigint" ? value : scaled(value, 2);
  const abs = cents < 0n ? -cents : cents;
  return `${cents < 0n ? "-" : ""}R$ ${(abs / 100n).toLocaleString("pt-BR")},${(abs % 100n).toString().padStart(2, "0")}`;
}
export function date(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
  }).format(new Date(value.length === 10 ? `${value}T12:00:00Z` : value));
}
export function dateTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "America/Sao_Paulo",
  }).format(new Date(value));
}
export function today() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  return ["year", "month", "day"]
    .map((t) => parts.find((p) => p.type === t)!.value)
    .join("-");
}
export function whatsapp(phone: string, message: string) {
  let digits = phone.replace(/\D/g, "");
  if (digits.length === 10 || digits.length === 11) digits = "55" + digits;
  if (!/^55[1-9]\d{9,10}$/.test(digits))
    throw new Error("WhatsApp inválido. Informe DDD e número brasileiro.");
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
}

export function quantity(value: string): string {
  const milli = scaled(value, 3);
  const fraction = (milli % 1000n)
    .toString()
    .padStart(3, "0")
    .replace(/0+$/, "");
  return (
    (milli / 1000n).toLocaleString("pt-BR") + (fraction ? "," + fraction : "")
  );
}
