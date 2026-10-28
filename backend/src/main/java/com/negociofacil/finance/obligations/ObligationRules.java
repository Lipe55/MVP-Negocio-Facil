package com.negociofacil.finance.obligations;

import com.negociofacil.common.ApiException;
import java.math.*;
import java.time.*;
import java.util.*;

public final class ObligationRules {
  private ObligationRules() {}
  /** Distribute the remainder in the first installments, never lose a cent. */
  public static List<BigDecimal> split(BigDecimal total,int count) {
    if(total==null || total.signum()<=0 || total.scale()>2 || count<1 || count>120)
      throw new ApiException(400,"Informe um valor positivo com até duas casas e de 1 a 120 parcelas.");
    var cents=total.movePointRight(2).toBigIntegerExact();
    if(cents.compareTo(BigInteger.valueOf(count))<0)
      throw new ApiException(400,"O valor precisa permitir pelo menos um centavo em cada parcela.");
    var parts=cents.divideAndRemainder(BigInteger.valueOf(count));var result=new ArrayList<BigDecimal>();
    for(int i=0;i<count;i++)result.add(new BigDecimal(parts[0].add(BigInteger.valueOf(i<parts[1].intValue()?1:0)),2));
    return result;
  }
  public static LocalDate monthly(LocalDate anchor,int offset) {
    return YearMonth.from(anchor).plusMonths(offset).atDay(Math.min(anchor.getDayOfMonth(),YearMonth.from(anchor).plusMonths(offset).lengthOfMonth()));
  }
  public static void date(LocalDate date) {
    if(date!=null && (date.getYear()<1900 || date.getYear()>2200))throw new ApiException(400,"Informe uma data entre 1900 e 2200.");
  }
}
