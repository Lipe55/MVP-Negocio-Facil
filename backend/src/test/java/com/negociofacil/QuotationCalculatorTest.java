package com.negociofacil;

import static org.assertj.core.api.Assertions.*;

import com.negociofacil.common.ApiException;
import com.negociofacil.quotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotationCalculatorTest {
  @Test
  void roundsEachItemHalfUpBeforeSumming() {
    var q = new Quotation();
    QuotationCalculator.apply(
        q,
        new QuotationDtos.Input(
            1L,
            LocalDate.now(),
            null,
            new BigDecimal("0.01"),
            List.of(
                new QuotationDtos.ItemInput("A", new BigDecimal("0.005"), new BigDecimal("1.00")),
                new QuotationDtos.ItemInput(
                    "B", new BigDecimal("0.005"), new BigDecimal("1.00")))));
    assertThat(q.subtotal).isEqualByComparingTo("0.02");
    assertThat(q.total).isEqualByComparingTo("0.01");
  }

  @Test
  void rejectsExcessDiscount() {
    assertThatThrownBy(
            () ->
                QuotationCalculator.apply(
                    new Quotation(),
                    new QuotationDtos.Input(1L, LocalDate.now(), null, new BigDecimal("2.00"),
                        List.of(new QuotationDtos.ItemInput("Item", BigDecimal.ONE, BigDecimal.ONE)))))
        .isInstanceOf(ApiException.class);
  }
}
