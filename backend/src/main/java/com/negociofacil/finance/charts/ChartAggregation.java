package com.negociofacil.finance.charts;

import com.negociofacil.common.ApiException;
import com.negociofacil.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;
import static com.negociofacil.finance.charts.ChartTypes.*;

/** Only enum-selected SQL fragments are interpolated. Every value/tenant/filter is bound. */
@Component
public class ChartAggregation {
  private final NamedParameterJdbcTemplate jdbc;
  public ChartAggregation(org.springframework.jdbc.core.JdbcTemplate jdbc) {this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
  private static final BigDecimal ZERO=new BigDecimal("0.00");
  private String number(BigDecimal value) {return value.setScale(2).toPlainString();}
  private MapSqlParameterSource parameters(ChartDtos.Config c,ChartDtos.Range range) {
    return new MapSqlParameterSource().addValue("company",CurrentUser.companyId()).addValue("from",range.from())
        .addValue("to",range.to()).addValue("accounts",c.accountIds()).addValue("categories",c.categoryIds());
  }
  public void validateOwnership(ChartDtos.Config c) {
    var p=new MapSqlParameterSource("company",CurrentUser.companyId());
    if(!c.accountIds().isEmpty()) {
      p.addValue("ids",c.accountIds());
      int count=jdbc.queryForObject("select count(*) from financial_accounts where company_id=:company and id in (:ids)",p,Integer.class);
      if(count!=c.accountIds().size()) throw ApiException.missing();
    }
    if(!c.categoryIds().isEmpty()) {
      p.addValue("ids",c.categoryIds());
      var kinds=jdbc.query("select kind from financial_categories where company_id=:company and id in (:ids)",p,(rs,row)->rs.getString(1));
      if(kinds.size()!=c.categoryIds().size())throw ApiException.missing();
      for(String kind:kinds)if((kind.equals("DESPESA")&&!c.metrics().contains(Metric.DESPESAS_PAGAS)) ||
          (kind.equals("RECEITA")&&!c.metrics().contains(Metric.RECEITAS_RECEBIDAS)))
        throw ChartRules.invalid("A categoria selecionada não corresponde às métricas do gráfico.");
    }
  }
  public ChartDtos.Data data(ChartDtos.Config c) {
    validateOwnership(c);
    var range=ChartRules.range(c);
    return c.metrics().contains(Metric.SALDO_DISPONIVEL)?balance(c,range):flows(c,range);
  }
  private String dateBucket(String column,Grouping grouping) {
    String unit=switch(grouping) {case DIA->"day";case SEMANA->"week";case MES->"month";case ANO->"year";default->throw new IllegalArgumentException();};
    return "date_trunc('"+unit+"',"+column+")::date::text";
  }
  private String label(String key,Grouping grouping) {
    var date=LocalDate.parse(key);var br=Locale.forLanguageTag("pt-BR");
    return switch(grouping) {
      case DIA -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy",br));
      case SEMANA -> "Semana de "+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy",br));
      case MES -> date.format(DateTimeFormatter.ofPattern("MMM/yyyy",br));
      case ANO -> Integer.toString(date.getYear());
      default -> key;
    };
  }
  private List<String> timeKeys(ChartDtos.Config c,ChartDtos.Range range) {
    var keys=new ArrayList<String>();
    for(LocalDate at=ChartRules.bucket(range.from(),c.grouping());!at.isAfter(range.to());at=ChartRules.next(at,c.grouping()))keys.add(at.toString());
    return keys;
  }
  private ChartDtos.Data flows(ChartDtos.Config c,ChartDtos.Range range) {
    var p=parameters(c,range);
    String key,description;
    if(temporal(c.grouping())) {key=dateBucket("m.effective_date",c.grouping());description=key;}
    else if(c.grouping()==Grouping.CATEGORIA) {key="'category:'||e.category_id::text";description="cat.name";}
    else {key="'account:'||m.account_id::text";description="a.name";}
    String accountFilter=c.accountIds().isEmpty()?"":" and m.account_id in (:accounts)";
    String categoryFilter=c.categoryIds().isEmpty()?"":" and e.category_id in (:categories)";
    var kinds=new ArrayList<String>();
    if(c.metrics().contains(Metric.RECEITAS_RECEBIDAS)||c.metrics().contains(Metric.RESULTADO_CAIXA))kinds.add("RECEITA");
    if(c.metrics().contains(Metric.DESPESAS_PAGAS)||c.metrics().contains(Metric.RESULTADO_CAIXA))kinds.add("DESPESA");
    if(c.metrics().contains(Metric.RETIRADAS_PESSOAIS))kinds.add("RETIRADA");
    p.addValue("kinds",kinds);
    String sql="""
      select %s as bucket,%s as label,
        coalesce(sum(case when e.kind='RECEITA' then m.amount else 0 end),0) as income,
        coalesce(sum(case when e.kind='DESPESA' then -m.amount else 0 end),0) as expense,
        coalesce(sum(case when e.kind='RETIRADA' then -m.amount else 0 end),0) as withdrawal
      from financial_movements m
      left join financial_movements original on original.id=m.reversal_of_id and original.company_id=m.company_id
      join financial_settlements s on s.id=coalesce(m.settlement_id,original.settlement_id) and s.company_id=m.company_id
      join financial_entries e on e.id=s.entry_id and e.company_id=m.company_id
      join financial_accounts a on a.id=m.account_id and a.company_id=m.company_id
      left join financial_categories cat on cat.id=e.category_id and cat.company_id=m.company_id
      where m.company_id=:company and m.effective_date between :from and :to and e.kind in (:kinds)
      %s %s group by 1,2 order by 1 limit 371
      """.formatted(key,description,accountFilter,categoryFilter);
    var rows=jdbc.query(sql,p,(rs,i)->new FlowRow(rs.getString("bucket"),rs.getString("label"),rs.getBigDecimal("income"),rs.getBigDecimal("expense"),rs.getBigDecimal("withdrawal")));
    if(rows.size()>ChartRules.MAX_POINTS)throw ChartRules.invalid("Há mais de 370 grupos. Filtre contas ou categorias.");
    var mapped=new LinkedHashMap<String,FlowRow>();for(var row:rows)mapped.put(row.key(),row);
    boolean hasData=rows.stream().anyMatch(r->r.income().signum()!=0||r.expense().signum()!=0||r.withdrawal().signum()!=0);
    var keys=temporal(c.grouping())?timeKeys(c,range):rows.stream().map(FlowRow::key).toList();
    var points=new ArrayList<ChartDtos.Point>();
    for(String k:keys) {
      var r=mapped.getOrDefault(k,new FlowRow(k,k,ZERO,ZERO,ZERO));
      var values=new LinkedHashMap<Metric,String>();
      for(var metric:c.metrics())values.put(metric,number(switch(metric) {
        case RECEITAS_RECEBIDAS->r.income();case DESPESAS_PAGAS->r.expense();case RESULTADO_CAIXA->r.income().subtract(r.expense());
        case RETIRADAS_PESSOAIS->r.withdrawal();case SALDO_DISPONIVEL->throw new IllegalStateException();
      }));
      points.add(new ChartDtos.Point(k,temporal(c.grouping())?label(k,c.grouping()):r.label(),values));
    }
    return result(c,range,points,null,hasData);
  }
  private record FlowRow(String key,String label,BigDecimal income,BigDecimal expense,BigDecimal withdrawal) {}
  private ChartDtos.Data balance(ChartDtos.Config c,ChartDtos.Range range) {
    var p=parameters(c,range);
    String accountFilter=c.accountIds().isEmpty()?"":" and account_id in (:accounts)";
    String events="""
      with events as (
        select id as account_id,opening_date as effective_date,opening_balance as amount
          from financial_accounts where company_id=:company %s
        union all
        select account_id,effective_date,amount from financial_movements where company_id=:company %s
      )
      """.formatted(accountFilter.replace("account_id","id"),accountFilter);
    var state=jdbc.queryForObject(events+"select coalesce(sum(amount) filter(where effective_date<:from),0) as opening,coalesce(bool_or(amount<>0),false) as has_data from events where effective_date<=:to",p,
        (rs,i)->new OpeningState(rs.getBigDecimal("opening"),rs.getBoolean("has_data")));
    BigDecimal opening=state.balance();
    var points=new ArrayList<ChartDtos.Point>();
    // A real zero balance is still data: offsetting payments/receipts, transfers
    // or opening balances must not be confused with a company without history.
    boolean hasData=state.hasData();
    if(c.grouping()==Grouping.CONTA) {
      var rows=jdbc.query(events+"""
        select e.account_id,a.name,coalesce(sum(e.amount),0) as balance from events e
        join financial_accounts a on a.id=e.account_id and a.company_id=:company
        where e.effective_date<=:to group by e.account_id,a.name order by e.account_id limit 371
        """,p,(rs,i)->new Object[]{rs.getLong("account_id"),rs.getString("name"),rs.getBigDecimal("balance")});
      if(rows.size()>ChartRules.MAX_POINTS)throw ChartRules.invalid("Há mais de 370 contas. Selecione as contas que deseja analisar.");
      for(var row:rows) {
        var amount=(BigDecimal)row[2];hasData|=amount.signum()!=0;
        points.add(new ChartDtos.Point("account:"+row[0],(String)row[1],Map.of(Metric.SALDO_DISPONIVEL,number(amount))));
      }
    } else {
      String bucket=dateBucket("effective_date",c.grouping());
      var rows=jdbc.query(events+"select "+bucket+" as bucket,sum(amount) as delta from events where effective_date between :from and :to group by 1 order by 1",p,
          (rs,i)->Map.entry(rs.getString("bucket"),rs.getBigDecimal("delta")));
      var changes=new HashMap<String,BigDecimal>();for(var row:rows)changes.put(row.getKey(),row.getValue());
      BigDecimal running=opening;
      for(String key:timeKeys(c,range)) {
        var change=changes.getOrDefault(key,ZERO);running=running.add(change);hasData|=running.signum()!=0||change.signum()!=0;
        points.add(new ChartDtos.Point(key,label(key,c.grouping()),Map.of(Metric.SALDO_DISPONIVEL,number(running))));
      }
    }
    return result(c,range,points,number(opening),hasData);
  }
  private record OpeningState(BigDecimal balance,boolean hasData) {}
  private ChartDtos.Data result(ChartDtos.Config c,ChartDtos.Range range,List<ChartDtos.Point> points,String opening,boolean hasData) {
    var warnings=new ArrayList<String>();
    if(c.metrics().contains(Metric.RESULTADO_CAIXA))warnings.add("Resultado operacional de caixa não representa lucro contábil.");
    if(c.metrics().contains(Metric.SALDO_DISPONIVEL))warnings.add("Cada ponto mostra o saldo no fim do intervalo; saldos não são somados como receitas.");
    return new ChartDtos.Data(range.from(),range.to(),c.grouping(),c.metrics().stream().map(m->new ChartDtos.Series(m,ChartTypes.label(m))).toList(),points,opening,hasData,warnings);
  }
}
