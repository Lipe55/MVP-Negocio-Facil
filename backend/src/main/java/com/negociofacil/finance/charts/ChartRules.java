package com.negociofacil.finance.charts;

import com.negociofacil.common.ApiException;
import com.negociofacil.finance.FinanceRules;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.*;
import java.util.*;
import static com.negociofacil.finance.charts.ChartTypes.*;

public final class ChartRules {
  public static final int MAX_CHARTS=24, MAX_POINTS=370;
  private ChartRules() {}
  public static ChartDtos.Config normalize(ChartDtos.Input in) {
    if(in.title==null || in.title.isBlank() || in.title.trim().length()>120 || in.type==null || in.grouping==null || in.period==null
        || in.metrics==null || in.metrics.isEmpty() || in.metrics.size()>5 || in.metrics.stream().anyMatch(Objects::isNull))
      throw invalid("Confira o título, as métricas e as opções do gráfico.");
    if(new HashSet<>(in.metrics).size()!=in.metrics.size()) throw invalid("Selecione cada métrica apenas uma vez.");
    var accounts=ids(in.accountIds);var categories=ids(in.categoryIds);
    boolean balance=in.metrics.contains(Metric.SALDO_DISPONIVEL);
    if(balance && (in.metrics.size()!=1 || !categories.isEmpty() || in.grouping==Grouping.CATEGORIA))
      throw invalid("Saldo é uma posição no tempo: use-o sozinho, por período ou conta, sem categorias.");
    if(!categories.isEmpty() && in.metrics.stream().anyMatch(m->m!=Metric.RECEITAS_RECEBIDAS && m!=Metric.DESPESAS_PAGAS))
      throw invalid("O filtro de categorias está disponível para receitas recebidas e despesas pagas.");
    if(in.grouping==Grouping.CATEGORIA && (in.metrics.size()!=1 ||
        (in.metrics.getFirst()!=Metric.RECEITAS_RECEBIDAS && in.metrics.getFirst()!=Metric.DESPESAS_PAGAS)))
      throw invalid("Agrupe por categoria uma única métrica de receita ou despesa.");
    if((in.type==Type.PIZZA || in.type==Type.ROSCA) && (in.metrics.size()!=1 || balance ||
        in.metrics.contains(Metric.RESULTADO_CAIXA) || temporal(in.grouping)))
      throw invalid("Pizza e rosca comparam partes de um total: escolha uma receita, despesa ou retirada por categoria ou conta.");
    if(in.size==null || in.palette==null || in.showValues==null) throw invalid("Confira as opções visuais.");
    var result=new ChartDtos.Config(in.title.trim(),in.type,List.copyOf(in.metrics),in.grouping,in.period,
        in.period==Period.PERSONALIZADO?in.from:null,in.period==Period.PERSONALIZADO?in.to:null,
        categories,accounts,in.size,in.palette,in.showValues);
    range(result);return result;
  }
  private static List<Long> ids(List<Long> ids) {
    if(ids==null || ids.size()>50 || ids.stream().anyMatch(i->i==null || i<=0))
      throw invalid("Escolha até 50 contas ou categorias válidas por filtro.");
    return ids.stream().distinct().sorted().toList();
  }
  public static ChartDtos.Range range(ChartDtos.Config c) {
    LocalDate today=FinanceRules.today(),to=today;
    LocalDate from=switch(c.period()) {
      case ULTIMOS_7_DIAS -> today.minusDays(6);
      case ULTIMOS_30_DIAS -> today.minusDays(29);
      case MES_ATUAL -> today.withDayOfMonth(1);
      case ULTIMOS_3_MESES -> today.withDayOfMonth(1).minusMonths(2);
      case ULTIMOS_6_MESES -> today.withDayOfMonth(1).minusMonths(5);
      case ULTIMOS_12_MESES -> today.withDayOfMonth(1).minusMonths(11);
      case ANO_ATUAL -> today.withDayOfYear(1);
      case PERSONALIZADO -> c.from();
    };
    if(c.period()==Period.PERSONALIZADO)to=c.to();
    if(from==null || to==null || from.isAfter(to) || to.isAfter(today) || from.isBefore(to.minusYears(5)))
      throw invalid("Use um período de até cinco anos, com início anterior ao fim e sem datas futuras.");
    if(temporal(c.grouping())) {
      long buckets=switch(c.grouping()) {
        case DIA -> ChronoUnit.DAYS.between(from,to)+1;
        case SEMANA -> ChronoUnit.WEEKS.between(bucket(from,c.grouping()),bucket(to,c.grouping()))+1;
        case MES -> ChronoUnit.MONTHS.between(bucket(from,c.grouping()),bucket(to,c.grouping()))+1;
        case ANO -> ChronoUnit.YEARS.between(bucket(from,c.grouping()),bucket(to,c.grouping()))+1;
        default -> throw new IllegalStateException();
      };
      if(buckets>MAX_POINTS)throw invalid("Este agrupamento ultrapassa 370 pontos. Reduza o período ou agrupe por semana, mês ou ano.");
    }
    return new ChartDtos.Range(from,to);
  }
  public static LocalDate bucket(LocalDate date,Grouping grouping) {
    return switch(grouping) {
      case DIA -> date;
      case SEMANA -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MES -> date.withDayOfMonth(1);
      case ANO -> date.withDayOfYear(1);
      default -> throw new IllegalArgumentException("Non-temporal grouping");
    };
  }
  public static LocalDate next(LocalDate date,Grouping grouping) {
    return switch(grouping) {case DIA->date.plusDays(1);case SEMANA->date.plusWeeks(1);case MES->date.plusMonths(1);case ANO->date.plusYears(1);default->throw new IllegalArgumentException();};
  }
  public static ApiException invalid(String message) { return new ApiException(400,message); }
}
