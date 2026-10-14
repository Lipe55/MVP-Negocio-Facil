package com.negociofacil;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.negociofacil.quotation.QuotationService;
import com.negociofacil.security.SessionUser;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/negocio_facil_test}",
      "spring.datasource.username=${DATABASE_USER:negocio_facil}",
      "spring.datasource.password=${DATABASE_PASSWORD}"
    })
@AutoConfigureMockMvc
class IntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  SessionUser owner, other;
  static int addressCounter;
  String testAddress;

  @BeforeEach
  void prepare() throws Exception {
    testAddress = "192.0.2." + (++addressCounter);
    assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
    jdbc.execute(
        "TRUNCATE"
            + " companies,users,customers,quotations,quotation_items,spring_session,spring_session_attributes"
            + " RESTART IDENTITY CASCADE");
    owner = register("owner@example.com");
    other = register("other@example.com");
  }

  private SessionUser register(String email) throws Exception {
    var result =
        mvc.perform(
                body(
                    post("/api/auth/register").with(csrf()),
                    Map.of(
                        "name",
                        "Responsável",
                        "email",
                        email,
                        "password",
                        "StrongPassword123!",
                        "companyName",
                        "Empresa " + email,
                        "companyPhone",
                        "11999999999")))
            .andExpect(status().isCreated());
    var data = data(result);
    return new SessionUser(data.get("id").asLong(), data.get("companyId").asLong(), email);
  }

  private ResultActions call(MockHttpServletRequestBuilder req, SessionUser user) throws Exception {
    return mvc.perform(
        req.with(
                authentication(
                    new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
            .with(csrf()));
  }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder req, Object body)
      throws Exception {
    return req.with(
            request -> {
              request.setRemoteAddr(testAddress);
              return request;
            })
        .contentType("application/json")
        .content(json.writeValueAsString(body));
  }

  private JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private long customer(SessionUser user) throws Exception {
    return data(call(
                body(
                    post("/api/customers"),
                    Map.of(
                        "name",
                        "Cliente Real",
                        "phone",
                        "11988888888",
                        "email",
                        "cliente@example.com",
                        "document",
                        "12345678901",
                        "notes",
                        "Privado")),
                user)
            .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  private Map<String, Object> input(long customer) {
    return Map.of(
        "customerId",
        customer,
        "validUntil",
        QuotationService.today().plusDays(7).toString(),
        "discount",
        "10.00",
        "notes",
        "Interno",
        "items",
        List.of(
            Map.of("description", "Serviço", "quantity", "2.5", "unitPrice", "100.10"),
            Map.of("description", "Material", "quantity", "1", "unitPrice", "50.00")));
  }

  private JsonNode quote(SessionUser user, long customer) throws Exception {
    return data(
        call(body(post("/api/quotations"), input(customer)), user).andExpect(status().isCreated()));
  }

  private JsonNode send(long id) throws Exception {
    return data(call(post("/api/quotations/" + id + "/send"), owner).andExpect(status().isOk()));
  }

  @Test
  void registersCompanyAndHashesPassword() {
    assertThat(jdbc.queryForObject("select count(*) from companies", Long.class)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select password_hash from users where email=?", String.class, owner.email()))
        .startsWith("$2")
        .doesNotContain("StrongPassword");
  }

  @Test
  void duplicateEmailIsRejectedWithoutCreatingCompany() throws Exception {
    mvc.perform(
            body(
                post("/api/auth/register").with(csrf()),
                Map.of(
                    "name",
                    "Nome",
                    "email",
                    owner.email(),
                    "password",
                    "StrongPassword123!",
                    "companyName",
                    "Outra",
                    "companyPhone",
                    "11999999999")))
        .andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("select count(*) from companies", Long.class)).isEqualTo(2);
  }

  @Test
  void validAndInvalidLoginAndSessionRecovery() throws Exception {
    var result =
        mvc.perform(
                body(
                    post("/api/auth/login").with(csrf()),
                    Map.of("email", owner.email(), "password", "StrongPassword123!")))
            .andExpect(status().isOk())
            .andReturn();
    Cookie cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isTrue();
    mvc.perform(get("/api/auth/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(owner.email()));
    mvc.perform(
            body(
                post("/api/auth/login").with(csrf()),
                Map.of("email", owner.email(), "password", "incorrect")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutInvalidatesPersistedSession() throws Exception {
    var result =
        mvc.perform(
                body(
                    post("/api/auth/login").with(csrf()),
                    Map.of("email", owner.email(), "password", "StrongPassword123!")))
            .andExpect(status().isOk())
            .andReturn();
    Cookie cookie = result.getResponse().getCookie("SESSION");
    mvc.perform(post("/api/auth/logout").cookie(cookie).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void unauthorizedAndCsrfRequestsFail() throws Exception {
    mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void customerCrudAndPagination() throws Exception {
    long id = customer(owner);
    call(get("/api/customers").param("search", "Real"), owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    call(
            body(put("/api/customers/" + id), Map.of("name", "Atualizado", "phone", "11988888888")),
            owner)
        .andExpect(status().isOk());
    call(delete("/api/customers/" + id), owner).andExpect(status().isNoContent());
  }

  @Test
  void companyIsolationBlocksEveryObjectOperation() throws Exception {
    long c = customer(owner);
    long q = quote(owner, c).get("id").asLong();
    call(get("/api/customers/" + c), other).andExpect(status().isNotFound());
    call(body(put("/api/customers/" + c), Map.of("name", "Ataque", "phone", "11988888888")), other)
        .andExpect(status().isNotFound());
    call(delete("/api/customers/" + c), other).andExpect(status().isNotFound());
    call(post("/api/customers/" + c + "/archive"), other).andExpect(status().isNotFound());
    call(get("/api/quotations/" + q), other).andExpect(status().isNotFound());
    call(body(put("/api/quotations/" + q), input(c)), other).andExpect(status().isNotFound());
    call(post("/api/quotations/" + q + "/send"), other).andExpect(status().isNotFound());
    call(body(post("/api/quotations"), input(c)), other).andExpect(status().isNotFound());
    call(get("/api/quotations"), other).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void backendRecalculatesMoneyAndIgnoresBrowserTotal() throws Exception {
    var payload = new HashMap<>(input(customer(owner)));
    payload.put("total", "0.01");
    var q =
        data(call(body(post("/api/quotations"), payload), owner).andExpect(status().isCreated()));
    assertThat(q.get("subtotal").asText()).isEqualTo("300.25");
    assertThat(q.get("total").asText()).isEqualTo("290.25");
  }

  @Test
  void negativeInputsAndExcessDiscountAreRejected() throws Exception {
    long c = customer(owner);
    for (String value : List.of("-1", "999999")) {
      var payload = new HashMap<>(input(c));
      payload.put("discount", value);
      call(body(post("/api/quotations"), payload), owner).andExpect(status().isBadRequest());
    }
    var payload = new HashMap<>(input(c));
    for (var invalid :
        List.of(
            Map.of("description", "Item", "quantity", "-1", "unitPrice", "100"),
            Map.of("description", "Item", "quantity", "1", "unitPrice", "-1"))) {
      payload.put("items", List.of(invalid));
      call(body(post("/api/quotations"), payload), owner).andExpect(status().isBadRequest());
    }
  }

  @Test
  void emptyOrNullItemsAndPastValidityAreRejected() throws Exception {
    long c = customer(owner);
    var payload = new HashMap<>(input(c));
    payload.put("discount", "0");
    payload.put("items", List.of());
    call(body(post("/api/quotations"), payload), owner).andExpect(status().isBadRequest());
    payload.put("items", Arrays.asList((Object) null));
    call(body(post("/api/quotations"), payload), owner).andExpect(status().isBadRequest());
    payload.put("items", input(c).get("items"));
    payload.put("validUntil", QuotationService.today().minusDays(1).toString());
    call(body(post("/api/quotations"), payload), owner).andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("select count(*) from quotations", Long.class)).isZero();
  }

  @Test
  void sendAndPublicViewDoNotLeakPrivateFields() throws Exception {
    var q = send(quote(owner, customer(owner)).get("id").asLong());
    String token = q.get("publicToken").asText();
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    var view = data(mvc.perform(get("/api/public/quotations/" + token)).andExpect(status().isOk()));
    assertThat(view.has("id")).isFalse();
    assertThat(view.has("customer")).isFalse();
    assertThat(view.has("notes")).isFalse();
    assertThat(view.toString()).doesNotContain("12345678901", "cliente@example.com", "Privado");
    assertThat(view.get("items").get(0).has("id")).isFalse();
    call(post("/api/quotations/" + q.get("id").asLong() + "/send"), owner)
        .andExpect(status().isConflict());
  }

  @Test
  void acceptanceIsFinalAndEditingIsBlocked() throws Exception {
    long c = customer(owner);
    var q = send(quote(owner, c).get("id").asLong());
    String token = q.get("publicToken").asText();
    mvc.perform(post("/api/public/quotations/" + token + "/accept").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACEITO"))
        .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    mvc.perform(post("/api/public/quotations/" + token + "/reject").with(csrf()))
        .andExpect(status().isConflict());
    call(body(put("/api/quotations/" + q.get("id").asLong()), input(c)), owner)
        .andExpect(status().isConflict());
    call(get("/api/dashboard/summary"), owner).andExpect(jsonPath("$.accepted").value(1));
  }

  @Test
  void rejectionIsFinal() throws Exception {
    var q = send(quote(owner, customer(owner)).get("id").asLong());
    String token = q.get("publicToken").asText();
    mvc.perform(post("/api/public/quotations/" + token + "/reject").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECUSADO"));
    mvc.perform(post("/api/public/quotations/" + token + "/accept").with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  void expiredQuoteCannotReceiveResponse() throws Exception {
    var q = send(quote(owner, customer(owner)).get("id").asLong());
    jdbc.update(
        "update quotations set valid_until=? where id=?",
        QuotationService.today().minusDays(1),
        q.get("id").asLong());
    String token = q.get("publicToken").asText();
    mvc.perform(post("/api/public/quotations/" + token + "/accept").with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/public/quotations/" + token))
        .andExpect(jsonPath("$.status").value("EXPIRADO"));
  }

  @Test
  void customerWithHistoryMustBeArchived() throws Exception {
    long c = customer(owner);
    quote(owner, c);
    call(delete("/api/customers/" + c), owner).andExpect(status().isConflict());
    call(post("/api/customers/" + c + "/archive"), owner).andExpect(status().isOk());
    call(get("/api/customers"), owner).andExpect(jsonPath("$.totalElements").value(0));
    call(body(post("/api/quotations"), input(c)), owner).andExpect(status().isBadRequest());
  }

  @Test
  void concurrentNumbersHaveNoCollisions() throws Exception {
    long c = customer(owner);
    try (var executor = Executors.newFixedThreadPool(6)) {
      var futures = new ArrayList<Future<Long>>();
      for (int i = 0; i < 12; i++)
        futures.add(executor.submit(() -> quote(owner, c).get("number").asLong()));
      var numbers = new HashSet<Long>();
      for (var future : futures) numbers.add(future.get(30, TimeUnit.SECONDS));
      assertThat(numbers).hasSize(12).contains(1L, 12L);
    }
  }

  @Test
  void concurrentPublicResponsesHaveExactlyOneWinner() throws Exception {
    var q = send(quote(owner, customer(owner)).get("id").asLong());
    String token = q.get("publicToken").asText();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Future<Integer>>();
      for (String action : List.of("accept", "reject"))
        tasks.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return mvc.perform(
                          post("/api/public/quotations/" + token + "/" + action).with(csrf()))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void companySettingsAndPasswordChange() throws Exception {
    call(
            body(
                put("/api/company"),
                Map.of(
                    "name",
                    "Novo nome",
                    "phone",
                    "11988888888",
                    "document",
                    "",
                    "responsibleName",
                    "Novo responsável")),
            owner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Novo nome"));
    call(
            body(
                post("/api/auth/password"),
                Map.of("currentPassword", "wrong", "newPassword", "NewStrongPassword123!")),
            owner)
        .andExpect(status().isBadRequest());
    call(
            body(
                post("/api/auth/password"),
                Map.of(
                    "currentPassword",
                    "StrongPassword123!",
                    "newPassword",
                    "NewStrongPassword123!")),
            owner)
        .andExpect(status().isNoContent());
    mvc.perform(
            body(
                post("/api/auth/login").with(csrf()),
                Map.of("email", owner.email(), "password", "NewStrongPassword123!")))
        .andExpect(status().isOk());
  }

  @Test
  void extremePaginationIsAClientError() throws Exception {
    call(get("/api/quotations?page=2147483647&size=100"), owner)
        .andExpect(status().isBadRequest());
    call(get("/api/customers?page=2147483647&size=100"), owner)
        .andExpect(status().isBadRequest());
  }

  @Test
  void longEmailCanPersistAndRecoverSession() throws Exception {
    String email = "a".repeat(60) + "@" + "b".repeat(50) + ".example.com";
    register(email);
    var result = mvc.perform(body(post("/api/auth/login").with(csrf()),
        Map.of("email", email, "password", "StrongPassword123!")))
        .andExpect(status().isOk()).andReturn();
    var cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    mvc.perform(get("/api/auth/me").cookie(cookie))
        .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
  }

  @Test
  void sentProposalPreservesPartiesAndOnlyPublishesCommercialTerms() throws Exception {
    long c = customer(owner);
    var payload = new HashMap<>(input(c));
    payload.put("terms", "Pagamento em 30 dias.");
    var draft = data(call(body(post("/api/quotations"), payload), owner)
        .andExpect(status().isCreated()));
    var sent = send(draft.get("id").asLong());
    String token = sent.get("publicToken").asText();
    mvc.perform(post("/api/public/quotations/" + token + "/accept").with(csrf()))
        .andExpect(status().isOk());
    call(body(put("/api/customers/" + c),
        Map.of("name", "Cliente renomeado", "phone", "11988888888")), owner)
        .andExpect(status().isOk());
    call(body(put("/api/company"), Map.of("name", "Empresa renomeada", "phone",
        "11988888888", "responsibleName", "Responsável")), owner).andExpect(status().isOk());
    mvc.perform(get("/api/public/quotations/" + token)).andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value(sent.get("companyName").asText()))
        .andExpect(jsonPath("$.customerName").value("Cliente Real"))
        .andExpect(jsonPath("$.terms").value("Pagamento em 30 dias."))
        .andExpect(jsonPath("$.notes").doesNotExist());
    call(get("/api/quotations/" + sent.get("id").asLong()), owner)
        .andExpect(jsonPath("$.customerName").value("Cliente Real"));
  }

  @Test
  void revocationIsOwnedFinalAndPreservesAcceptedHistory() throws Exception {
    var sent = send(quote(owner, customer(owner)).get("id").asLong());
    long id = sent.get("id").asLong();
    String token = sent.get("publicToken").asText();
    call(post("/api/quotations/" + id + "/revoke-public"), other)
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/public/quotations/" + token + "/accept").with(csrf()))
        .andExpect(status().isOk());
    call(post("/api/quotations/" + id + "/revoke-public"), owner)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACEITO"))
        .andExpect(jsonPath("$.publicRevokedAt").isNotEmpty())
        .andExpect(jsonPath("$.publicToken").isEmpty());
    call(post("/api/quotations/" + id + "/revoke-public"), owner)
        .andExpect(status().isOk());
    mvc.perform(get("/api/public/quotations/" + token)).andExpect(status().isNotFound());
    mvc.perform(post("/api/public/quotations/" + token + "/reject").with(csrf()))
        .andExpect(status().isNotFound());
    call(get("/api/quotations/" + id), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACEITO"))
        .andExpect(jsonPath("$.respondedAt").isNotEmpty());
  }

  @Test
  void passwordChangeRejectsOldCredentialEvenIfSessionIsSavedAfterRevocation() throws Exception {
    var login = mvc.perform(body(post("/api/auth/login").with(csrf()),
        Map.of("email", owner.email(), "password", "StrongPassword123!")))
        .andExpect(status().isOk()).andReturn();
    var cookie = login.getResponse().getCookie("SESSION");
    call(body(post("/api/auth/password"), Map.of("currentPassword", "StrongPassword123!",
        "newPassword", "NewStrongPassword123!")), owner).andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    // Models a concurrent old-password login which writes its principal too late.
    call(get("/api/auth/me"), owner).andExpect(status().isUnauthorized());
    var updated = new SessionUser(owner.userId(), owner.companyId(), owner.email(), 2);
    call(get("/api/auth/me"), updated).andExpect(status().isOk());
  }

  @Test
  void companyEditsAndNumberAllocationDoNotResetCounter() throws Exception {
    long c = customer(owner);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var creation = executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 10; i++) quote(owner, c);
        return true;
      });
      var settings = executor.submit(() -> {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 10; i++)
          call(body(put("/api/company"), Map.of("name", "Empresa " + i,
              "phone", "11988888888", "responsibleName", "Responsável")), owner)
              .andExpect(status().isOk());
        return true;
      });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(creation.get(30, TimeUnit.SECONDS)).isTrue();
      assertThat(settings.get(30, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(jdbc.queryForObject("select next_quote_number from companies where id=?",
        Long.class, owner.companyId())).isEqualTo(11L);
    assertThat(quote(owner, c).get("number").asLong()).isEqualTo(11L);
  }

}
