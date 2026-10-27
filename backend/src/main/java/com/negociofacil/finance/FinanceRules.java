package com.negociofacil.finance;

import com.negociofacil.common.ApiException;
import java.math.*;
import java.time.*;
import static com.negociofacil.finance.FinanceTypes.*;

public final class FinanceRules {
  private FinanceRules() {}
  public static LocalDate today() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")); }
  public static BigDecimal signed(Kind kind, BigDecimal amount) {
    return kind == Kind.RECEITA ? amount : amount.negate();
  }
  public static MovementKind movementKind(Kind kind) {
    return switch(kind) {
      case RECEITA -> MovementKind.RECEBIMENTO;
      case DESPESA -> MovementKind.PAGAMENTO;
      case RETIRADA -> MovementKind.RETIRADA;
    };
  }
  public static void effectiveDate(LocalDate date, LocalDate openingDate) {
    if (date == null || date.isAfter(today()) || date.isBefore(openingDate))
      throw new ApiException(400, "A data efetiva deve estar entre a data do saldo inicial e hoje.");
  }
  public static void version(Long expected, long actual) {
    if (expected == null || expected != actual)
      throw new ApiException(409, "Este registro foi atualizado. Recarregue antes de alterar.");
  }
  public static void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new ApiException(400, "Paginação inválida.");
  }
}
