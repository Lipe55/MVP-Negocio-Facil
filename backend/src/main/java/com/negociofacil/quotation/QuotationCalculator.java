package com.negociofacil.quotation;

import com.negociofacil.common.ApiException;
import java.math.*;

public final class QuotationCalculator {
  private QuotationCalculator() {}

  public static void apply(Quotation quote, QuotationDtos.Input input) {
    if (input.items() == null || input.items().isEmpty() || input.items().stream().anyMatch(java.util.Objects::isNull))
      throw new ApiException(400, "Adicione ao menos um item válido.");
    BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    quote.items.clear();
    for (var item : input.items()) {
      if (item.quantity().signum() <= 0 || item.unitPrice().signum() < 0)
        throw new ApiException(400, "Quantidade e valor inválidos.");
      var row = new QuotationItem();
      row.quotation = quote;
      row.description = item.description().trim();
      row.quantity = item.quantity();
      row.unitPrice = item.unitPrice();
      row.total = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(row.total);
      quote.items.add(row);
    }
    if (input.discount().signum() < 0 || input.discount().compareTo(subtotal) > 0)
      throw new ApiException(400, "Desconto não pode superar o subtotal.");
    if (subtotal.compareTo(new BigDecimal("99999999999999.99")) > 0)
      throw new ApiException(400, "Valor total excede o limite permitido.");
    quote.subtotal = subtotal;
    quote.discount = input.discount().setScale(2);
    quote.total = subtotal.subtract(quote.discount);
  }
}
