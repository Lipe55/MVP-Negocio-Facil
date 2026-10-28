package com.negociofacil.finance.charts;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.negociofacil.finance.FinanceRules;
import com.negociofacil.security.SessionUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties={
    "spring.datasource.url=${FINANCE_TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/negocio_finance_test}",
    "spring.datasource.username=${FINANCE_TEST_DATABASE_USER:postgres}",
    "spring.datasource.password=${FINANCE_TEST_DATABASE_PASSWORD:unused}"})
@ContextConfiguration(initializers=ChartIntegrationTest.DatabaseGuard.class)
@AutoConfigureMockMvc
class ChartIntegrationTest {
  public static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext context) {
      String url=context.getEnvironment().getProperty("spring.datasource.url","");
      if(!url.matches("jdbc:postgresql://[^/]+/negocio_finance_test(?:\\?.*)?"))
        throw new IllegalStateException("Chart tests require an isolated negocio_finance_test database.");
    }
  }
  private static final String ROOT="/api/finance/charts";
  private static final AtomicInteger counter=new AtomicInteger();
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
  SessionUser owner,other;
  @BeforeEach void prepare() throws Exception {
    assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("negocio_finance_test");
    jdbc.execute("TRUNCATE companies CASCADE");
    owner=register("charts-owner@example.com");other=register("charts-other@example.com");
    var main=main(owner);
    call(body(put("/api/finance/accounts/"+main.get("id").asLong()),Map.of("name","Conta principal","type","BANCO",
        "openingBalance","0.00","openingDate",today().minusYears(4).toString(),"active",true,"version",main.get("version").asLong())),owner).andExpect(status().isOk());
  }
  private LocalDate today(){return FinanceRules.today();}
  private SessionUser register(String email) throws Exception {
    var result=mvc.perform(body(post("/api/auth/register").with(csrf()),Map.of("name","Responsável","email",email,
        "password","SyntheticCharts123!","companyName","Empresa de teste","companyPhone","11999999999")))
        .andExpect(status().isCreated());
    var n=data(result);return new SessionUser(n.get("id").asLong(),n.get("companyId").asLong(),email);
  }
  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object v)throws Exception {
    return r.contentType("application/json").content(json.writeValueAsString(v)).with(req->{req.setRemoteAddr("192.0.2."+counter.incrementAndGet());return req;});
  }
  private ResultActions call(MockHttpServletRequestBuilder r,SessionUser u)throws Exception {
    return mvc.perform(r.with(authentication(new UsernamePasswordAuthenticationToken(u,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))).with(csrf()));
  }
  private JsonNode data(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsString());}
  private JsonNode main(SessionUser u)throws Exception {
    var rows=data(call(get("/api/finance/accounts"),u).andExpect(status().isOk()));
    for(var n:rows)if(n.get("primaryAccount").asBoolean())return n;throw new AssertionError();
  }
  private long category(SessionUser u,String name)throws Exception {
    var rows=data(call(get("/api/finance/categories"),u));
    for(var n:rows)if(n.get("name").asText().equals(name))return n.get("id").asLong();throw new AssertionError(name);
  }
  private JsonNode second(String name,String opening,LocalDate date)throws Exception {
    return data(call(body(post("/api/finance/accounts"),Map.of("name",name,"type","DINHEIRO","openingBalance",opening,
        "openingDate",date.toString(),"active",true)),owner).andExpect(status().isCreated()));
  }
  private Map<String,Object> config(String...metrics) {
    var c=new HashMap<String,Object>();c.put("title","Meu gráfico");c.put("type","BARRAS");c.put("metrics",List.of(metrics));
    c.put("grouping","MES");c.put("period","ULTIMOS_12_MESES");c.put("accountIds",List.of());c.put("categoryIds",List.of());
    c.put("size","MEDIO");c.put("palette","PADRAO");c.put("showValues",true);return c;
  }
  private JsonNode createChart(Map<String,Object> c)throws Exception{return data(call(body(post(ROOT),c),owner).andExpect(status().isCreated()));}
  private JsonNode preview(Map<String,Object> c)throws Exception{return data(call(body(post(ROOT+"/preview"),c),owner).andExpect(status().isOk()));}
  private JsonNode panel(SessionUser u)throws Exception{return data(call(get(ROOT),u).andExpect(status().isOk()));}
  private BigDecimal total(JsonNode d,String metric) {
    BigDecimal sum=BigDecimal.ZERO;for(var p:d.get("points"))sum=sum.add(new BigDecimal(p.get("values").get(metric).asText()));return sum;
  }
  private Map<String,Object> entry(String kind,String amount,boolean paid,long account,Long category,LocalDate date) {
    var v=new HashMap<String,Object>();v.put("requestId",UUID.randomUUID().toString());v.put("kind",kind);v.put("amount",amount);
    v.put("settled",paid);v.put("accountId",account);v.put("description","Movimento real de teste");v.put("categoryId",category);
    v.put("entryDate",date.toString());if(paid)v.put("settlementDate",date.toString());return v;
  }
  private JsonNode postEntry(Map<String,Object> e)throws Exception{return data(call(body(post("/api/finance/entries"),e),owner).andExpect(status().isCreated()));}
  @Test void initializesDefaultsOnceAndKeepsAnEmptyPanelAfterDeletion()throws Exception {
    var first=panel(owner);var second=panel(owner);
    // PostgreSQL persists timestamps at microsecond precision; compare the
    // configuration/identity rather than Java's transient creation nanoseconds.
    var comparableFirst=first.deepCopy();var comparableSecond=second.deepCopy();
    for(var c:comparableFirst.get("charts"))((com.fasterxml.jackson.databind.node.ObjectNode)c).remove(List.of("createdAt","updatedAt"));
    for(var c:comparableSecond.get("charts"))((com.fasterxml.jackson.databind.node.ObjectNode)c).remove(List.of("createdAt","updatedAt"));
    assertThat(comparableFirst).isEqualTo(comparableSecond);
    assertThat(first.get("charts").size()).isEqualTo(5);
    for(int i=0;i<5;i++) {
      var c=panel(owner).get("charts").get(0);
      call(delete(ROOT+"/"+c.get("id").asLong()).param("version",c.get("version").asText()),owner).andExpect(status().isNoContent());
    }
    assertThat(panel(owner).get("charts").isEmpty()).isTrue();
    assertThat(panel(other).get("charts").size()).isEqualTo(5);
  }
  @Test void savesEditsDuplicatesAndRecoversIndependentPreferencesAcrossSessions()throws Exception {
    var input=config("DESPESAS_PAGAS");input.put("title","Meus gastos com combustível");input.put("categoryIds",List.of(category(owner,"Transporte")));
    input.put("palette","ACESSIVEL");input.put("size","GRANDE");var chart=createChart(input);long id=chart.get("id").asLong();
    assertThat(chart.get("title").asText()).isEqualTo("Meus gastos com combustível");
    assertThat(chart.get("metrics").get(0).asText()).isEqualTo("DESPESAS_PAGAS");
    var copy=data(call(body(post(ROOT+"/"+id+"/duplicate"),Map.of("version",chart.get("version").asLong())),owner).andExpect(status().isCreated()));
    assertThat(copy.get("id")).isNotEqualTo(chart.get("id"));assertThat(copy.get("categoryIds")).isEqualTo(chart.get("categoryIds"));
    input.put("title","Transporte atualizado");input.put("type","LINHAS");input.put("period","ULTIMOS_6_MESES");input.put("grouping","SEMANA");input.put("version",chart.get("version").asLong());
    var edited=data(call(body(put(ROOT+"/"+id),input),owner).andExpect(status().isOk()));
    assertThat(edited.get("type").asText()).isEqualTo("LINHAS");assertThat(edited.get("version").asLong()).isGreaterThan(chart.get("version").asLong());
    call(body(put(ROOT+"/"+id),input),owner).andExpect(status().isConflict());
    var newSession=new SessionUser(owner.userId(),owner.companyId(),owner.email());
    assertThat(data(call(get(ROOT+"/"+id),newSession)).get("title").asText()).isEqualTo("Transporte atualizado");
    assertThat(data(call(get(ROOT+"/"+copy.get("id").asLong()),newSession)).get("type").asText()).isEqualTo("BARRAS");
    var login=mvc.perform(body(post("/api/auth/login").with(csrf()),Map.of("email",owner.email(),"password","SyntheticCharts123!"))).andExpect(status().isOk()).andReturn();
    var cookie=login.getResponse().getCookie("SESSION");assertThat(cookie).isNotNull();
    mvc.perform(post("/api/auth/logout").cookie(cookie).with(csrf())).andExpect(status().isNoContent());
    var again=mvc.perform(body(post("/api/auth/login").with(csrf()),Map.of("email",owner.email(),"password","SyntheticCharts123!"))).andExpect(status().isOk()).andReturn();
    mvc.perform(get(ROOT+"/"+id).cookie(again.getResponse().getCookie("SESSION"))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Transporte atualizado"));
  }
  @Test void reordersAndRestoresOnlyPreferencesAndRejectsStalePanel()throws Exception {
    postEntry(entry("DESPESA","15.20",true,main(owner).get("id").asLong(),category(owner,"Materiais"),today()));
    var p=panel(owner);List<Long> ids=new ArrayList<>();for(var c:p.get("charts"))ids.add(c.get("id").asLong());Collections.reverse(ids);
    var ordered=data(call(body(put(ROOT+"/order"),Map.of("ids",ids,"version",p.get("version").asLong())),owner).andExpect(status().isOk()));
    assertThat(ordered.get("charts").get(0).get("id").asLong()).isEqualTo(ids.getFirst());
    assertThat(panel(owner).get("charts").get(0).get("id").asLong()).isEqualTo(ids.getFirst());
    call(body(post(ROOT+"/restore"),Map.of("version",p.get("version").asLong())),owner).andExpect(status().isConflict());
    var restored=data(call(body(post(ROOT+"/restore"),Map.of("version",ordered.get("version").asLong())),owner).andExpect(status().isOk()));
    assertThat(restored.get("charts").size()).isEqualTo(5);assertThat(restored.get("charts").get(0).get("title").asText()).isEqualTo("Receitas versus despesas");
    assertThat(jdbc.queryForObject("select count(*) from financial_entries where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
  }
  @Test void userInSameCompanyCannotAccessAnothersCharts()throws Exception {
    Long userId=jdbc.queryForObject("insert into users(company_id,name,email,password_hash,role,created_at,updated_at) select company_id,'Outro','same-company@example.com',password_hash,role,current_timestamp,current_timestamp from users where id=? returning id",Long.class,owner.userId());
    var colleague=new SessionUser(userId,owner.companyId(),"same-company@example.com");
    var chart=createChart(config("RECEITAS_RECEBIDAS"));long id=chart.get("id").asLong();
    assertThat(panel(colleague).get("charts").size()).isEqualTo(5);
    assertNoAccess(colleague,id,chart.get("version").asLong());
  }
  private void assertNoAccess(SessionUser u,long id,long version)throws Exception {
    call(get(ROOT+"/"+id),u).andExpect(status().isNotFound());call(get(ROOT+"/"+id+"/data"),u).andExpect(status().isNotFound());
    var input=config("DESPESAS_PAGAS");input.put("version",version);
    call(body(put(ROOT+"/"+id),input),u).andExpect(status().isNotFound());
    call(body(post(ROOT+"/"+id+"/duplicate"),Map.of("version",version)),u).andExpect(status().isNotFound());
    call(delete(ROOT+"/"+id).param("version",Long.toString(version)),u).andExpect(status().isNotFound());
  }
  @Test void isolatesChartsAndFilterIdsBetweenCompanies()throws Exception {
    var chart=createChart(config("DESPESAS_PAGAS"));assertNoAccess(other,chart.get("id").asLong(),chart.get("version").asLong());
    var c=config("DESPESAS_PAGAS");c.put("accountIds",List.of(main(other).get("id").asLong()));
    call(body(post(ROOT+"/preview"),c),owner).andExpect(status().isNotFound());
    c.put("accountIds",List.of());c.put("categoryIds",List.of(category(other,"Transporte")));
    call(body(post(ROOT),c),owner).andExpect(status().isNotFound());
    var ownPanel=panel(owner);var foreign=panel(other).get("charts").get(0).get("id").asLong();
    call(body(put(ROOT+"/order"),Map.of("ids",List.of(foreign),"version",ownPanel.get("version").asLong())),owner).andExpect(status().isBadRequest());
  }
  @Test void fuelExampleUsesExistingCategoriesPaidAmountsAndMultipleFilters()throws Exception {
    long main=main(owner).get("id").asLong(),transport=category(owner,"Transporte"),materials=category(owner,"Materiais");
    long cash=second("Dinheiro","0",today()).get("id").asLong();
    postEntry(entry("DESPESA","40.15",true,main,transport,today()));postEntry(entry("DESPESA","10.05",true,main,transport,today()));
    postEntry(entry("DESPESA","800",false,main,transport,today()));postEntry(entry("DESPESA","12.01",true,main,materials,today()));
    postEntry(entry("DESPESA","70",true,cash,transport,today()));
    var c=config("DESPESAS_PAGAS");c.put("title","Meus gastos com combustível");c.put("categoryIds",List.of(transport));c.put("accountIds",List.of(main));
    var result=preview(c);assertThat(result.get("points").size()).isEqualTo(12);assertThat(total(result,"DESPESAS_PAGAS")).isEqualByComparingTo("50.20");
    var chart=createChart(c);assertThat(data(call(get(ROOT+"/"+chart.get("id").asLong()+"/data"),owner))).isEqualTo(result);
    postEntry(entry("DESPESA","0.80",true,main,transport,today()));
    assertThat(total(data(call(get(ROOT+"/"+chart.get("id").asLong()+"/data"),owner)),"DESPESAS_PAGAS")).isEqualByComparingTo("51.00");
    c.put("categoryIds",List.of(transport,materials));assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("63.01");
    c.put("accountIds",List.of(main,cash));assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("133.01");
    assertThat(jdbc.queryForObject("select count(*) from financial_categories where company_id=? and name='Combustível'",Integer.class,owner.companyId())).isZero();
  }
  @Test void comparesActualIncomeExpenseAndSeparatesTransferAndWithdrawal()throws Exception {
    long account=main(owner).get("id").asLong(),target=second("Segunda","0",today()).get("id").asLong();
    postEntry(entry("RECEITA","1000",true,account,category(owner,"Prestação de serviços"),today()));
    postEntry(entry("DESPESA","300",true,account,category(owner,"Materiais"),today()));
    postEntry(entry("RETIRADA","100",true,account,null,today()));postEntry(entry("RECEITA","9000",false,account,category(owner,"Prestação de serviços"),today()));
    call(body(post("/api/finance/transfers"),Map.of("requestId",UUID.randomUUID().toString(),"sourceAccountId",account,"targetAccountId",target,"amount","200","transferDate",today().toString())),owner).andExpect(status().isCreated());
    var c=config("RECEITAS_RECEBIDAS","DESPESAS_PAGAS");c.put("period","ULTIMOS_6_MESES");var d=preview(c);
    assertThat(d.get("series").size()).isEqualTo(2);assertThat(d.get("points").size()).isEqualTo(6);
    assertThat(total(d,"RECEITAS_RECEBIDAS")).isEqualByComparingTo("1000");assertThat(total(d,"DESPESAS_PAGAS")).isEqualByComparingTo("300");
    c.put("type","LINHAS");assertThat(preview(c)).isEqualTo(d);
    assertThat(total(preview(config("RESULTADO_CAIXA")),"RESULTADO_CAIXA")).isEqualByComparingTo("700");
    assertThat(total(preview(config("RETIRADAS_PESSOAIS")),"RETIRADAS_PESSOAIS")).isEqualByComparingTo("100");
  }
  @Test void editAndCancellationRecalculateNetCashAcrossCategoriesAndAccounts()throws Exception {
    long main=main(owner).get("id").asLong(),transport=category(owner,"Transporte"),materials=category(owner,"Materiais");
    var paid=postEntry(entry("DESPESA","45.67",true,main,transport,today()));
    var update=entry("DESPESA","55.01",true,main,materials,today());update.put("version",paid.get("version").asLong());
    var changed=data(call(body(put("/api/finance/entries/"+paid.get("id").asLong()),update),owner).andExpect(status().isOk()));
    var c=config("DESPESAS_PAGAS");c.put("categoryIds",List.of(transport));assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("0");
    c.put("categoryIds",List.of(materials));assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("55.01");
    call(body(post("/api/finance/entries/"+paid.get("id").asLong()+"/cancel"),Map.of("version",changed.get("version").asLong())),owner).andExpect(status().isOk());
    assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("0");
  }
  @Test void balanceUsesOpeningBeforePeriodAndOpeningDuringPeriodWithoutSummingDailyStocks()throws Exception {
    var a=main(owner);long account=a.get("id").asLong();
    call(body(put("/api/finance/accounts/"+account),Map.of("name","Conta principal","type","BANCO","openingBalance","500","openingDate",today().minusDays(40).toString(),"active",true,"version",a.get("version").asLong())),owner).andExpect(status().isOk());
    long target=second("Nova conta","50",today()).get("id").asLong();
    postEntry(entry("RECEITA","100",true,account,category(owner,"Prestação de serviços"),today()));
    postEntry(entry("DESPESA","20",true,account,category(owner,"Materiais"),today()));
    call(body(post("/api/finance/transfers"),Map.of("requestId",UUID.randomUUID().toString(),"sourceAccountId",account,"targetAccountId",target,"amount","30","transferDate",today().toString())),owner).andExpect(status().isCreated());
    var c=config("SALDO_DISPONIVEL");c.put("grouping","DIA");c.put("period","ULTIMOS_7_DIAS");var d=preview(c);
    assertThat(d.get("openingBalance").asText()).isEqualTo("500.00");assertThat(d.get("points").get(0).get("values").get("SALDO_DISPONIVEL").asText()).isEqualTo("500.00");
    assertThat(d.get("points").get(6).get("values").get("SALDO_DISPONIVEL").asText()).isEqualTo("630.00");
    c.put("accountIds",List.of(account));d=preview(c);assertThat(d.get("points").get(6).get("values").get("SALDO_DISPONIVEL").asText()).isEqualTo("550.00");
    c.put("accountIds",List.of());c.put("grouping","CONTA");d=preview(c);assertThat(total(d,"SALDO_DISPONIVEL")).isEqualByComparingTo("630");
  }
  @Test void usesEffectiveDateAndFillsCalendarDayWeekMonthAndYearBuckets()throws Exception {
    long main=main(owner).get("id").asLong(),cat=category(owner,"Materiais");LocalDate old=today().minusMonths(2).withDayOfMonth(1);
    var input=entry("DESPESA","13.33",true,main,cat,old);input.put("entryDate",today().toString());postEntry(input);
    var c=config("DESPESAS_PAGAS");c.put("period","ULTIMOS_3_MESES");assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("13.33");
    c.put("period","ULTIMOS_7_DIAS");c.put("grouping","DIA");var recent=preview(c);assertThat(recent.get("points").size()).isEqualTo(7);assertThat(recent.get("hasData").asBoolean()).isFalse();
    c.put("period","PERSONALIZADO");c.put("from",old.toString());c.put("to",today().toString());c.put("grouping","SEMANA");
    var weeks=preview(c);assertThat(total(weeks,"DESPESAS_PAGAS")).isEqualByComparingTo("13.33");
    c.put("grouping","ANO");assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("13.33");
    c.put("grouping","CATEGORIA");c.put("type","ROSCA");var categories=preview(c);assertThat(categories.get("points").get(0).get("label").asText()).isEqualTo("Materiais");
    c.put("type","PIZZA");assertThat(preview(c)).isEqualTo(categories);
    c.put("type","AREA");c.put("grouping","CONTA");assertThat(total(preview(c),"DESPESAS_PAGAS")).isEqualByComparingTo("13.33");
  }
  @Test void validatesMetricsVisualCombinationsFiltersAndQueryLimits()throws Exception {
    var invalid=new ArrayList<Map<String,Object>>();
    var c=config("SALDO_DISPONIVEL","RECEITAS_RECEBIDAS");invalid.add(c);
    c=config("RESULTADO_CAIXA");c.put("type","ROSCA");c.put("grouping","CONTA");invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("type","PIZZA");invalid.add(c);
    c=config("RECEITAS_RECEBIDAS","DESPESAS_PAGAS");c.put("grouping","CATEGORIA");invalid.add(c);
    c=config("RETIRADAS_PESSOAIS");c.put("categoryIds",List.of(category(owner,"Materiais")));invalid.add(c);
    c=config("RECEITAS_RECEBIDAS");c.put("categoryIds",List.of(category(owner,"Materiais")));invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("grouping","DIA");c.put("period","PERSONALIZADO");c.put("from",today().minusYears(2).toString());c.put("to",today().toString());invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("period","PERSONALIZADO");c.put("from",today().toString());c.put("to",today().plusDays(1).toString());invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("period","PERSONALIZADO");c.put("from",today().minusYears(6).toString());c.put("to",today().toString());invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("customerIds",List.of(1));invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("serviceIds",List.of(1));invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("subcategoryIds",List.of(1));invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("status","PENDENTE");invalid.add(c);
    c=config("CONTAS_VENCIDAS");invalid.add(c);
    c=config("DESPESAS_PAGAS");c.put("grouping","MES;DROP TABLE companies");invalid.add(c);
    for(var value:invalid)call(body(post(ROOT+"/preview"),value),owner).andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("select count(*) from companies",Integer.class)).isEqualTo(2);
  }
  @Test void catalogIdentifiesUnsupportedCapabilitiesAndAnonymousWritesNeedAuthenticationAndCsrf()throws Exception {
    var catalog=data(call(get(ROOT+"/catalog"),owner).andExpect(status().isOk()));
    assertThat(catalog.get("maxCharts").asInt()).isEqualTo(24);
    int available=0;for(var m:catalog.get("metrics")){if(m.get("available").asBoolean())available++;else assertThat(m.get("reason").asText()).isNotBlank();}
    assertThat(available).isEqualTo(5);assertThat(catalog.get("unavailableFilters").size()).isEqualTo(4);
    mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
    mvc.perform(body(post(ROOT),config("DESPESAS_PAGAS")).with(authentication(new UsernamePasswordAuthenticationToken(owner,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))).andExpect(status().isForbidden());
  }
  @Test void emptyDataHasNoInventedAmounts()throws Exception {
    var d=preview(config("RECEITAS_RECEBIDAS","DESPESAS_PAGAS"));assertThat(d.get("hasData").asBoolean()).isFalse();
    assertThat(total(d,"RECEITAS_RECEBIDAS")).isEqualByComparingTo("0");assertThat(total(d,"DESPESAS_PAGAS")).isEqualByComparingTo("0");
    var c=config("DESPESAS_PAGAS");c.put("grouping","CATEGORIA");assertThat(preview(c).get("points").isEmpty()).isTrue();
  }
  @Test void realZeroBalanceRemainsVisibleWithOffsettingHistoryAndInternalTransfers()throws Exception {
    var c=config("SALDO_DISPONIVEL");c.put("grouping","DIA");c.put("period","ULTIMOS_7_DIAS");
    assertThat(preview(c).get("hasData").asBoolean()).isFalse();
    long main=main(owner).get("id").asLong();
    postEntry(entry("RECEITA","100",true,main,category(owner,"Prestação de serviços"),today().minusDays(10)));
    postEntry(entry("DESPESA","100",true,main,category(owner,"Materiais"),today().minusDays(10)));
    var zero=preview(c);assertThat(zero.get("hasData").asBoolean()).isTrue();assertThat(total(zero,"SALDO_DISPONIVEL")).isEqualByComparingTo("0");
    postEntry(entry("RECEITA","25",true,main,category(owner,"Prestação de serviços"),today()));
    postEntry(entry("DESPESA","25",true,main,category(owner,"Materiais"),today()));
    zero=preview(c);assertThat(zero.get("hasData").asBoolean()).isTrue();assertThat(total(zero,"SALDO_DISPONIVEL")).isEqualByComparingTo("0");
    long target=second("Segunda","0",today()).get("id").asLong();
    call(body(post("/api/finance/transfers"),Map.of("requestId",UUID.randomUUID().toString(),"sourceAccountId",main,"targetAccountId",target,"amount","10","transferDate",today().toString())),owner).andExpect(status().isCreated());
    zero=preview(c);assertThat(zero.get("hasData").asBoolean()).isTrue();assertThat(total(zero,"SALDO_DISPONIVEL")).isEqualByComparingTo("0");
    c.put("accountIds",List.of(main));var selected=preview(c);
    assertThat(selected.get("points").get(6).get("values").get("SALDO_DISPONIVEL").asText()).isEqualTo("-10.00");
    c.put("accountIds",List.of(target));selected=preview(c);assertThat(selected.get("points").get(6).get("values").get("SALDO_DISPONIVEL").asText()).isEqualTo("10.00");
  }
  @Test void limitsPanelSizeAndRejectsDuplicateOrIncompleteOrder()throws Exception {
    var p=panel(owner);
    for(int i=0;i<19;i++)createChart(config("DESPESAS_PAGAS"));
    call(body(post(ROOT),config("DESPESAS_PAGAS")),owner).andExpect(status().isConflict());
    p=panel(owner);assertThat(p.get("charts").size()).isEqualTo(24);
    var first=p.get("charts").get(0);
    call(body(post(ROOT+"/"+first.get("id").asLong()+"/duplicate"),Map.of("version",first.get("version").asLong())),owner).andExpect(status().isConflict());
    call(body(put(ROOT+"/order"),Map.of("version",p.get("version").asLong(),"ids",Collections.nCopies(24,first.get("id").asLong()))),owner).andExpect(status().isBadRequest());
    call(body(put(ROOT+"/order"),Map.of("version",p.get("version").asLong(),"ids",List.of())),owner).andExpect(status().isBadRequest());
    assertThat(panel(owner).get("charts").size()).isEqualTo(24);
  }
}
