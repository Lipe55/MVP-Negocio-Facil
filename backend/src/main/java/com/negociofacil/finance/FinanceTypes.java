package com.negociofacil.finance;

public final class FinanceTypes {
  private FinanceTypes() {}
  public enum AccountType { BANCO, DINHEIRO }
  public enum Kind { RECEITA, DESPESA, RETIRADA }
  public enum Status { PENDENTE, PARCIAL, LIQUIDADO, CANCELADO }
  public enum PaymentStatus { PENDENTE, PARCIAL, QUITADO, VENCIDO, CANCELADO }
  public enum MovementKind { RECEBIMENTO, PAGAMENTO, RETIRADA, TRANSFERENCIA_ENTRADA, TRANSFERENCIA_SAIDA, ESTORNO }
}
