package com.negociofacil;

import static org.assertj.core.api.Assertions.*;
import com.negociofacil.quotation.QuotationDtos;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotationInputValidationTest {
  @Test
  void validatesNullAndEmptyItemsBeforeCalculation() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (List<QuotationDtos.ItemInput> items :
          List.<List<QuotationDtos.ItemInput>>of(List.of(), Arrays.asList((QuotationDtos.ItemInput) null))) {
        var input = new QuotationDtos.Input(1L, LocalDate.now(), null, BigDecimal.ZERO, items);
        assertThat(validator.validate(input)).isNotEmpty();
      }
      var valid = new QuotationDtos.Input(1L, LocalDate.now(), null, BigDecimal.ZERO,
          List.of(new QuotationDtos.ItemInput("Serviço", BigDecimal.ONE, BigDecimal.TEN)),
          "Pagamento em 30 dias.");
      assertThat(validator.validate(valid)).isEmpty();
    }
  }
}
