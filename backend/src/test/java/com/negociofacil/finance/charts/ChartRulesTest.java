package com.negociofacil.finance.charts;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.negociofacil.finance.FinanceRules;
import static com.negociofacil.finance.charts.ChartTypes.*;

class ChartRulesTest {
  private ChartDtos.Input input(){var i=new ChartDtos.Input();i.title="  Gastos  ";i.type=Type.BARRAS;i.metrics=List.of(Metric.DESPESAS_PAGAS);i.grouping=Grouping.MES;i.period=ChartTypes.Period.ULTIMOS_12_MESES;return i;}
  @Test void calendarMonthsIncludeCurrentPartialMonthAndNormalizeFilters(){
    var input=input();input.categoryIds=List.of(9L,2L,9L);var c=ChartRules.normalize(input);var range=ChartRules.range(c);
    assertThat(c.title()).isEqualTo("Gastos");assertThat(c.categoryIds()).containsExactly(2L,9L);
    assertThat(range.from()).isEqualTo(FinanceRules.today().withDayOfMonth(1).minusMonths(11));assertThat(range.to()).isEqualTo(FinanceRules.today());
  }
  @Test void weeksStartOnMondayAcrossYearBoundary(){
    assertThat(ChartRules.bucket(LocalDate.of(2025,1,1),Grouping.SEMANA)).isEqualTo(LocalDate.of(2024,12,30));
    assertThat(ChartRules.next(LocalDate.of(2024,12,30),Grouping.SEMANA)).isEqualTo(LocalDate.of(2025,1,6));
  }
  @Test void rejectsNullDuplicateOrTooManyFilters(){
    var i=input();i.metrics=List.of(Metric.DESPESAS_PAGAS,Metric.DESPESAS_PAGAS);assertThatThrownBy(()->ChartRules.normalize(i)).isInstanceOf(com.negociofacil.common.ApiException.class);
    i.metrics=List.of(Metric.DESPESAS_PAGAS);i.accountIds=Collections.nCopies(51,1L);assertThatThrownBy(()->ChartRules.normalize(i)).isInstanceOf(com.negociofacil.common.ApiException.class);
    i.accountIds=null;assertThatThrownBy(()->ChartRules.normalize(i)).isInstanceOf(com.negociofacil.common.ApiException.class);
  }
}
