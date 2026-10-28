package com.negociofacil.finance.obligations;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ObligationRulesTest {
  @Test void sixInstallmentsPreserveExactOriginalAmount() {
    var amounts=ObligationRules.split(new BigDecimal("1200"),6);
    assertThat(amounts).hasSize(6).allSatisfy(a->assertThat(a).isEqualByComparingTo("200"));
    assertThat(amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1200");
  }
  @Test void remainderCentsGoToFirstInstallments() {
    var amounts=ObligationRules.split(new BigDecimal("100.00"),3);
    assertThat(amounts).containsExactly(new BigDecimal("33.34"),new BigDecimal("33.33"),new BigDecimal("33.33"));
    assertThat(ObligationRules.split(new BigDecimal("0.05"),3)).containsExactly(new BigDecimal("0.02"),new BigDecimal("0.02"),new BigDecimal("0.01"));
  }
  @Test void monthEndUsesOriginalAnchorAndRespectsLeapYears() {
    var jan31=LocalDate.of(2024,1,31);
    assertThat(ObligationRules.monthly(jan31,1)).isEqualTo(LocalDate.of(2024,2,29));
    assertThat(ObligationRules.monthly(jan31,2)).isEqualTo(LocalDate.of(2024,3,31));
    assertThat(ObligationRules.monthly(LocalDate.of(2025,1,31),1)).isEqualTo(LocalDate.of(2025,2,28));
  }
  @Test void rejectsZeroValuedInstallmentsAndInvalidPrecisionOrCounts() {
    assertThatThrownBy(()->ObligationRules.split(new BigDecimal("0.01"),2)).isInstanceOf(com.negociofacil.common.ApiException.class);
    assertThatThrownBy(()->ObligationRules.split(new BigDecimal("1.001"),1)).isInstanceOf(com.negociofacil.common.ApiException.class);
    assertThatThrownBy(()->ObligationRules.split(new BigDecimal("1"),121)).isInstanceOf(com.negociofacil.common.ApiException.class);
    assertThatThrownBy(()->ObligationRules.split(new BigDecimal("0"),1)).isInstanceOf(com.negociofacil.common.ApiException.class);
  }
}
