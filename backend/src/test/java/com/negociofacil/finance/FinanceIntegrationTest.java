package com.negociofacil.finance;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.negociofacil.security.SessionUser;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
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
@ContextConfiguration(initializers=FinanceIntegrationTest.DatabaseGuard.class)
@AutoConfigureMockMvc
class FinanceIntegrationTest {
  // Run before Flyway or any datasource initialization, not merely before TRUNCATE.
  public static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext context) {
      String url=context.getEnvironment().getProperty("spring.datasource.url","");
      if(!url.matches("jdbc:postgresql://[^/]+/negocio_finance_test(?:\\?.*)?"))
        throw new IllegalStateException("Finance tests require an isolated negocio_finance_test database.");
    }
  }
  @Autowired javax.sql.DataSource dataSource;
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
  private SessionUser owner,other;
  private static final AtomicInteger counter=new AtomicInteger();
  @BeforeEach void prepare() throws Exception {
    assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("negocio_finance_test");
    jdbc.execute("TRUNCATE companies CASCADE");
    owner=register("finance-owner@example.com"); other=register("finance-other@example.com");
  }
  private SessionUser register(String email) throws Exception {
    var result=mvc.perform(body(post("/api/auth/register").with(csrf()),Map.of("name","Responsável","email",email,
        "password","SyntheticFinance123!","companyName","Empresa de teste","companyPhone","11999999999")))
        .andExpect(status().isCreated());
    var data=data(result); return new SessionUser(data.get("id").asLong(),data.get("companyId").asLong(),email);
  }
  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
    return request.contentType("application/json").content(json.writeValueAsString(value))
        .with(req->{req.setRemoteAddr("192.0.2."+counter.incrementAndGet());return req;});
  }
  private ResultActions call(MockHttpServletRequestBuilder request,SessionUser user) throws Exception {
    var principal = new UsernamePasswordAuthenticationToken(user,null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    return mvc.perform(request.with(authentication(principal)).with(csrf()));
  }
  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }
  private JsonNode main(SessionUser user) throws Exception {
    var rows=data(call(get("/api/finance/accounts"),user).andExpect(status().isOk()));
    for(var a:rows)if(a.get("primaryAccount").asBoolean())return a;
    throw new AssertionError("Missing primary account");
  }
  private long category(SessionUser user,String kind) throws Exception {
    var rows=data(call(get("/api/finance/categories"),user).andExpect(status().isOk()));
    for(var c:rows)if(c.get("kind").asText().equals(kind))return c.get("id").asLong();
    throw new AssertionError("Missing category");
  }
  private Map<String,Object> input(SessionUser user,String kind,String amount,boolean settled) throws Exception {
    var result=new HashMap<String,Object>();result.put("requestId",UUID.randomUUID().toString());
    result.put("kind",kind);result.put("description","Lançamento real de teste");result.put("amount",amount);
    result.put("settled",settled);result.put("accountId",main(user).get("id").asLong());
    if(!kind.equals("RETIRADA"))result.put("categoryId",category(user,kind));
    return result;
  }
  private JsonNode create(SessionUser user,String kind,String amount,boolean settled) throws Exception {
    return data(call(body(post("/api/finance/entries"),input(user,kind,amount,settled)),user)
        .andExpect(status().isCreated()));
  }
  private BigDecimal balance(SessionUser user) throws Exception {return new BigDecimal(main(user).get("balance").asText());}
  private JsonNode secondAccount() throws Exception {
    return data(call(body(post("/api/finance/accounts"),Map.of("name","Dinheiro","type","DINHEIRO",
        "openingBalance","0.00","openingDate",FinanceRules.today().toString(),"active",true)),owner).andExpect(status().isCreated()));
  }
  @Test void bootstrapsMainAndCategoriesOnlyOnce() throws Exception {
    assertThat(data(call(get("/api/finance/accounts"),owner)).size()).isEqualTo(1);
    assertThat(data(call(get("/api/finance/categories"),owner)).size()).isEqualTo(5);
    assertThat(main(owner).get("name").asText()).isEqualTo("Conta principal");
    assertThat(balance(owner)).isEqualByComparingTo("0.00");
  }
  @Test void receivedIncomeIncreasesBalanceAndPaidExpenseDecreasesIt() throws Exception {
    create(owner,"RECEITA","100.01",true);assertThat(balance(owner)).isEqualByComparingTo("100.01");
    create(owner,"DESPESA","20.02",true);assertThat(balance(owner)).isEqualByComparingTo("79.99");
  }
  @Test void pendingIncomeAndExpenseNeverCreateCashMovements() throws Exception {
    create(owner,"RECEITA","200",false);create(owner,"DESPESA","300",false);
    assertThat(balance(owner)).isEqualByComparingTo("0.00");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isZero();
  }
  @Test void withdrawalHasOwnClassificationAndReducesCash() throws Exception {
    create(owner,"RECEITA","100",true);create(owner,"RETIRADA","30",true);
    assertThat(balance(owner)).isEqualByComparingTo("70");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=? and kind='PAGAMENTO'",Long.class,owner.companyId())).isZero();
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=? and kind='RETIRADA'",Long.class,owner.companyId())).isEqualTo(1);
  }
  @Test void editingSettledEntryReversesOriginalAndRecalculatesBalance() throws Exception {
    var e=create(owner,"RECEITA","100",true);var update=input(owner,"RECEITA","150.01",true);
    update.put("version",e.get("version").asLong());
    var edited=data(call(body(put("/api/finance/entries/"+e.get("id").asLong()),update),owner).andExpect(status().isOk()));
    assertThat(balance(owner)).isEqualByComparingTo("150.01");
    assertThat(edited.get("version").asLong()).isGreaterThan(e.get("version").asLong());
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isEqualTo(3);
    call(body(put("/api/finance/entries/"+e.get("id").asLong()),update),owner).andExpect(status().isConflict());
    assertThat(data(call(get("/api/finance/history/LANCAMENTO/"+e.get("id").asLong()),owner)).size()).isEqualTo(2);
  }
  @Test void paidToPendingReversesCashAndCanBeSettledAgain() throws Exception {
    var e=create(owner,"DESPESA","30",true);var update=input(owner,"DESPESA","30",false);
    update.put("version",e.get("version").asLong());
    var pending=data(call(body(put("/api/finance/entries/"+e.get("id").asLong()),update),owner).andExpect(status().isOk()));
    assertThat(balance(owner)).isEqualByComparingTo("0");
    call(body(post("/api/finance/entries/"+e.get("id").asLong()+"/settle"),Map.of("requestId",UUID.randomUUID(),"version",pending.get("version").asLong(),"settlementDate",FinanceRules.today().toString())),owner).andExpect(status().isOk());
    assertThat(balance(owner)).isEqualByComparingTo("-30");
  }
  @Test void cancellationIsIdempotentAndNeverDeletesHistory() throws Exception {
    var e=create(owner,"DESPESA","12.34",true);long id=e.get("id").asLong();
    for(int i=0;i<2;i++)call(body(post("/api/finance/entries/"+id+"/cancel"),Map.of("version",e.get("version").asLong())),owner).andExpect(status().isOk());
    assertThat(balance(owner)).isEqualByComparingTo("0");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isEqualTo(2);
    assertThat(data(call(get("/api/finance/history/LANCAMENTO/"+id),owner)).size()).isEqualTo(2);
  }
  @Test void creationIsIdempotentAndConflictingReuseIsRejected() throws Exception {
    var input=input(owner,"RECEITA","25",true);
    for(int i=0;i<2;i++)call(body(post("/api/finance/entries"),input),owner).andExpect(status().isCreated());
    assertThat(balance(owner)).isEqualByComparingTo("25");
    input.put("amount","26");call(body(post("/api/finance/entries"),input),owner).andExpect(status().isConflict());
    assertThat(balance(owner)).isEqualByComparingTo("25");
  }
  @Test void transferAndReversalPreserveConsolidatedBalance() throws Exception {
    create(owner,"RECEITA","100",true);long from=main(owner).get("id").asLong(),to=secondAccount().get("id").asLong();
    var payload=Map.of("requestId",UUID.randomUUID(),"sourceAccountId",from,"targetAccountId",to,"amount","40.01","transferDate",FinanceRules.today().toString());
    var t=data(call(body(post("/api/finance/transfers"),payload),owner).andExpect(status().isCreated()));
    call(body(post("/api/finance/transfers"),payload),owner).andExpect(status().isCreated());
    assertThat(balance(owner)).isEqualByComparingTo("59.99");
    var rows=data(call(get("/api/finance/accounts"),owner));
    BigDecimal sum=BigDecimal.ZERO;for(var a:rows)sum=sum.add(new BigDecimal(a.get("balance").asText()));
    assertThat(sum).isEqualByComparingTo("100");
    call(body(post("/api/finance/transfers/"+t.get("id").asLong()+"/cancel"),Map.of("version",t.get("version").asLong())),owner).andExpect(status().isOk());
    assertThat(balance(owner)).isEqualByComparingTo("100");
  }
  @Test void openingBalanceIsNotOperationalIncomeAndBecomesImmutableAfterPosting() throws Exception {
    var a=main(owner);long id=a.get("id").asLong();
    var payload=new HashMap<String,Object>(Map.of("name","Conta principal","type","BANCO","openingBalance","500.00","openingDate",FinanceRules.today().minusDays(10).toString(),"active",true,"version",a.get("version").asLong()));
    var updated=data(call(body(put("/api/finance/accounts/"+id),payload),owner).andExpect(status().isOk()));
    assertThat(balance(owner)).isEqualByComparingTo("500");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isZero();
    create(owner,"RECEITA","10",true);payload.put("openingBalance","600");payload.put("version",updated.get("version").asLong());
    call(body(put("/api/finance/accounts/"+id),payload),owner).andExpect(status().isConflict());
    assertThat(balance(owner)).isEqualByComparingTo("510");
  }
  @Test void rejectsInvalidAmountDatesAndCategoriesWithoutPosting() throws Exception {
    var payload=input(owner,"RECEITA","0",true);
    call(body(post("/api/finance/entries"),payload),owner).andExpect(status().isBadRequest());
    payload.put("amount","1.001");call(body(post("/api/finance/entries"),payload),owner).andExpect(status().isBadRequest());
    payload.put("amount","10");payload.put("settlementDate",FinanceRules.today().plusDays(1).toString());
    call(body(post("/api/finance/entries"),payload),owner).andExpect(status().isBadRequest());
    payload.remove("settlementDate");payload.put("categoryId",category(owner,"DESPESA"));
    call(body(post("/api/finance/entries"),payload),owner).andExpect(status().isBadRequest());
    assertThat(balance(owner)).isEqualByComparingTo("0");
  }
  @Test void tenantCannotReadEditSettleCancelOrAssociateForeignRecords() throws Exception {
    var e=create(owner,"RECEITA","20",false);long id=e.get("id").asLong();
    call(get("/api/finance/entries/"+id),other).andExpect(status().isNotFound());
    var update=input(other,"RECEITA","20",true);update.put("version",e.get("version").asLong());
    call(body(put("/api/finance/entries/"+id),update),other).andExpect(status().isNotFound());
    call(body(post("/api/finance/entries/"+id+"/settle"),Map.of("requestId",UUID.randomUUID(),"version",0,"settlementDate",FinanceRules.today().toString())),other).andExpect(status().isNotFound());
    call(body(post("/api/finance/entries/"+id+"/cancel"),Map.of("version",0)),other).andExpect(status().isNotFound());
    call(get("/api/finance/history/LANCAMENTO/"+id),other).andExpect(status().isNotFound());
    long account=main(owner).get("id").asLong();
    call(get("/api/finance/accounts/"+account+"/statement"),other).andExpect(status().isNotFound());
    update.put("accountId",account);call(body(post("/api/finance/entries"),update),other).andExpect(status().isNotFound());
    update.put("accountId",main(other).get("id").asLong());update.put("categoryId",category(owner,"RECEITA"));
    call(body(post("/api/finance/entries"),update),other).andExpect(status().isNotFound());
    assertThat(balance(owner)).isEqualByComparingTo("0");assertThat(balance(other)).isEqualByComparingTo("0");
  }
  @Test void concurrentSettlementsHaveOneWinnerAndOneCashMovement() throws Exception {
    var e=create(owner,"RECEITA","123.45",false);long id=e.get("id").asLong();
    var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
    try(var executor=Executors.newFixedThreadPool(2)) {
      var futures=new ArrayList<Future<Integer>>();
      for(int i=0;i<2;i++)futures.add(executor.submit(()->{
        var request=body(post("/api/finance/entries/"+id+"/settle"),Map.of("requestId",UUID.randomUUID(),"version",e.get("version").asLong(),"settlementDate",FinanceRules.today().toString()));
        ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();
        return call(request,owner).andReturn().getResponse().getStatus();
      }));
      assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
      assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
    }
    assertThat(balance(owner)).isEqualByComparingTo("123.45");
    assertThat(jdbc.queryForObject("select count(*) from financial_settlements where company_id=? and reversed_at is null",Long.class,owner.companyId())).isEqualTo(1);
  }
  @Test void statementIncludesRunningBalanceAndExcludesPendingItems() throws Exception {
    create(owner,"RECEITA","100",true);create(owner,"DESPESA","25",true);create(owner,"RECEITA","500",false);
    var statement=data(call(get("/api/finance/accounts/"+main(owner).get("id").asLong()+"/statement"),owner).andExpect(status().isOk()));
    assertThat(statement.get("movements").get("totalElements").asInt()).isEqualTo(2);
    assertThat(statement.get("balanceAtEnd").asText()).isEqualTo("75.00");
    assertThat(statement.get("movements").get("content").get(0).get("runningBalance").asText()).isEqualTo("75.00");
  }
  @Test void authenticationAndCsrfRemainRequired() throws Exception {
    mvc.perform(get("/api/finance/accounts")).andExpect(status().isUnauthorized());
    mvc.perform(body(post("/api/finance/entries"),input(owner,"RECEITA","1",true))
        .with(authentication(new UsernamePasswordAuthenticationToken(owner,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))))
        .andExpect(status().isForbidden());
  }
  @Test void upgradeBackfillsExistingCompaniesWithoutTurningOpeningBalanceIntoRevenue() {
    String schema="finance_upgrade_"+UUID.randomUUID().toString().replace("-","");
    try {
      org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .target("2").load().migrate();
      Long companyId=jdbc.queryForObject("insert into "+schema+".companies(name,phone,created_at,updated_at) values ('Empresa anterior','11999999999',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id",Long.class);
      org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .load().migrate();
      assertThat(jdbc.queryForObject("select count(*) from "+schema+".financial_accounts where company_id=? and primary_account",Long.class,companyId)).isEqualTo(1);
      assertThat(jdbc.queryForObject("select count(*) from "+schema+".financial_categories where company_id=?",Long.class,companyId)).isEqualTo(5);
      assertThat(jdbc.queryForObject("select count(*) from "+schema+".financial_movements",Long.class)).isZero();
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }
  @Test void repeatedSettlementKeyNeverChargesTwice() throws Exception {
    var e=create(owner,"RECEITA","50",false);long id=e.get("id").asLong();
    var payload=Map.of("requestId",UUID.randomUUID(),"version",e.get("version").asLong(),"settlementDate",FinanceRules.today().toString());
    for(int i=0;i<2;i++)call(body(post("/api/finance/entries/"+id+"/settle"),payload),owner).andExpect(status().isOk());
    assertThat(balance(owner)).isEqualByComparingTo("50");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isEqualTo(1);
  }
  @Test void failedTransferReversalRollsBackBothAccounts() throws Exception {
    create(owner,"RECEITA","100",true);long from=main(owner).get("id").asLong();var target=secondAccount();long to=target.get("id").asLong();
    var t=data(call(body(post("/api/finance/transfers"),Map.of("requestId",UUID.randomUUID(),"sourceAccountId",from,"targetAccountId",to,"amount","50","transferDate",FinanceRules.today().toString())),owner).andExpect(status().isCreated()));
    var withdrawal=input(owner,"RETIRADA","50",true);withdrawal.put("accountId",to);
    call(body(post("/api/finance/entries"),withdrawal),owner).andExpect(status().isCreated());
    call(body(put("/api/finance/accounts/"+to),Map.of("name","Dinheiro","type","DINHEIRO","openingBalance","0","openingDate",FinanceRules.today().toString(),"active",false,"version",target.get("version").asLong())),owner).andExpect(status().isOk());
    Long before=jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId());
    call(body(post("/api/finance/transfers/"+t.get("id").asLong()+"/cancel"),Map.of("version",t.get("version").asLong())),owner).andExpect(status().isConflict());
    assertThat(balance(owner)).isEqualByComparingTo("50");
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Long.class,owner.companyId())).isEqualTo(before);
  }
  @Test void openingAndDateFiltersCarryForwardTheCorrectStatementBalance() throws Exception {
    var a=main(owner);long id=a.get("id").asLong();var yesterday=FinanceRules.today().minusDays(1);
    call(body(put("/api/finance/accounts/"+id),Map.of("name","Conta principal","type","BANCO","openingBalance","10","openingDate",yesterday.toString(),"active",true,"version",a.get("version").asLong())),owner).andExpect(status().isOk());
    var income=input(owner,"RECEITA","100",true);income.put("settlementDate",yesterday.toString());
    call(body(post("/api/finance/entries"),income),owner).andExpect(status().isCreated());
    create(owner,"DESPESA","25",true);
    var statement=data(call(get("/api/finance/accounts/"+id+"/statement").param("from",FinanceRules.today().toString()),owner).andExpect(status().isOk()));
    assertThat(statement.get("balanceBeforePeriod").asText()).isEqualTo("110.00");
    assertThat(statement.get("periodChange").asText()).isEqualTo("-25.00");
    assertThat(statement.get("balanceAtEnd").asText()).isEqualTo("85.00");
    assertThat(statement.get("movements").get("content").get(0).get("runningBalance").asText()).isEqualTo("85.00");
  }
  @Test void transferAndAccountChangesRejectOtherCompanies() throws Exception {
    long foreign=main(other).get("id").asLong();var ownerAccount=main(owner);
    call(body(post("/api/finance/transfers"),Map.of("requestId",UUID.randomUUID(),"sourceAccountId",ownerAccount.get("id").asLong(),"targetAccountId",foreign,"amount","1","transferDate",FinanceRules.today().toString())),owner).andExpect(status().isNotFound());
    call(body(put("/api/finance/accounts/"+foreign),Map.of("name","Outra conta","type","BANCO","openingBalance","10","openingDate",FinanceRules.today().toString(),"active",true,"version",0)),owner).andExpect(status().isNotFound());
    call(get("/api/finance/entries").param("accountId",String.valueOf(foreign)),owner).andExpect(status().isNotFound());
    assertThat(balance(owner)).isEqualByComparingTo("0");assertThat(balance(other)).isEqualByComparingTo("0");
  }
  @Test void immutableLedgerRejectsRewritingPostedMovement() throws Exception {
    create(owner,"RECEITA","10",true);
    assertThatThrownBy(()->jdbc.update("update financial_movements set amount=100 where company_id=?",owner.companyId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(balance(owner)).isEqualByComparingTo("10");
  }

  @Test void cannotDeactivateAnAccountByChangingItsOpeningBalanceToANonzeroAmount() throws Exception {
    var a=secondAccount();long id=a.get("id").asLong();
    call(body(put("/api/finance/accounts/"+id),Map.of("name","Dinheiro","type","DINHEIRO",
        "openingBalance","10.00","openingDate",FinanceRules.today().toString(),"active",false,
        "version",a.get("version").asLong())),owner).andExpect(status().isConflict());
    var rows=data(call(get("/api/finance/accounts"),owner));
    for(var row:rows)if(row.get("id").asLong()==id){
      assertThat(row.get("active").asBoolean()).isTrue();
      assertThat(row.get("balance").asText()).isEqualTo("0.00");
    }
  }

}
