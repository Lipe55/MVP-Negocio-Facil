export type Status =
  | "RASCUNHO"
  | "AGUARDANDO"
  | "ACEITO"
  | "RECUSADO"
  | "EXPIRADO";
export interface Me {
  id: number;
  name: string;
  email: string;
  companyId: number;
  companyName: string;
}
export interface Customer {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  document: string | null;
  notes: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface Item {
  id: number;
  description: string;
  quantity: string;
  unitPrice: string;
  total: string;
}
export interface Quotation {
  id: number;
  number: number;
  status: Status;
  customer: Customer;
  validUntil: string;
  notes: string | null;
  subtotal: string;
  discount: string;
  total: string;
  items: Item[];
  companyName: string;
  customerName: string;
  terms: string | null;
  publicRevokedAt: string | null;
  publicToken: string | null;
  sentAt: string | null;
  respondedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface PublicQuotation {
  terms: string | null;
  companyName: string;
  number: number;
  customerName: string;
  status: Status;
  validUntil: string;
  subtotal: string;
  discount: string;
  total: string;
  items: Omit<Item, "id">[];
  respondedAt: string | null;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
export interface Company {
  id: number;
  name: string;
  phone: string;
  document: string | null;
  responsibleName: string;
}
export interface Summary {
  customers: number;
  quotations: number;
  awaiting: number;
  accepted: number;
  recent: Quotation[];
}
