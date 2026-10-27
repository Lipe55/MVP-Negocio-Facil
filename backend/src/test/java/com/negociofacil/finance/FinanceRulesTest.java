package com.negociofacil.finance;

import static org.assertj.core.api.Assertions.*;
import com.negociofacil.common.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static com.negociofacil.finance.FinanceTypes.*;

class FinanceRulesTest {
  @Test void cashSignsAndOperationalClassificationAreDistinct() {
    assertThat(FinanceRules.signed(Kind.RECEITA,new BigDecimal("100.01"))).isEqualByComparingTo("100.01");
    assertThat(FinanceRules.signed(Kind.DESPESA,new BigDecimal("20.02"))).isEqualByComparingTo("-20.02");
    assertThat(FinanceRules.signed(Kind.RETIRADA,new BigDecimal("30.03"))).isEqualByComparingTo("-30.03");
    assertThat(FinanceRules.movementKind(Kind.DESPESA)).isEqualTo(MovementKind.PAGAMENTO);
    assertThat(FinanceRules.movementKind(Kind.RETIRADA)).isEqualTo(MovementKind.RETIRADA);
  }
  @Test void rejectsFutureCashAndCashBeforeOpeningReference() {
    var today=FinanceRules.today();
    assertThatThrownBy(()->FinanceRules.effectiveDate(today.plusDays(1),today)).isInstanceOf(ApiException.class);
    assertThatThrownBy(()->FinanceRules.effectiveDate(today.minusDays(1),today)).isInstanceOf(ApiException.class);
    assertThatCode(()->FinanceRules.effectiveDate(today,today)).doesNotThrowAnyException();
  }
  @Test void detectsStaleEditsAndPaginationOverflow() {
    assertThatThrownBy(()->FinanceRules.version(1L,2)).isInstanceOf(ApiException.class);
    assertThatThrownBy(()->FinanceRules.page(Integer.MAX_VALUE,100)).isInstanceOf(ApiException.class);
    assertThatCode(()->FinanceRules.version(2L,2)).doesNotThrowAnyException();
  }
}
