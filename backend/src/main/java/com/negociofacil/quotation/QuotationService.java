package com.negociofacil.quotation;

import com.negociofacil.common.*;
import com.negociofacil.company.*;
import com.negociofacil.customer.*;
import com.negociofacil.security.CurrentUser;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QuotationService {
  private final QuotationRepository quotes;
  private final CustomerRepository customers;
  private final CompanyRepository companies;
  private final EntityManager em;
  private final SecureRandom random = new SecureRandom();

  public QuotationService(
      QuotationRepository quotes,
      CustomerRepository customers,
      CompanyRepository companies,
      EntityManager em) {
    this.quotes = quotes;
    this.customers = customers;
    this.companies = companies;
    this.em = em;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
  }

  private Customer customer(Long id) {
    return customers
        .findByIdAndCompanyId(id, CurrentUser.companyId())
        .orElseThrow(ApiException::missing);
  }

  private Quotation owned(Long id) {
    return quotes.lockOwned(id, CurrentUser.companyId()).orElseThrow(ApiException::missing);
  }

  private void expire(Quotation q) {
    if (q.status == QuotationStatus.AGUARDANDO && q.validUntil.isBefore(today()))
      q.status = QuotationStatus.EXPIRADO;
  }

  public QuotationDtos.View get(Long id) {
    var q = owned(id);
    expire(q);
    return view(q);
  }

  public PageResult<QuotationDtos.View> list(
      Long customerId, QuotationStatus status, LocalDate from, LocalDate to, int page, int size) {
    if (page < 0 || size < 1 || size > 100
        || (long) page * size > Integer.MAX_VALUE
        || (from != null && to != null && from.isAfter(to)))
      throw new ApiException(400, "Filtros inválidos.");
    Long company = CurrentUser.companyId();
    quotes.expire(company, today());
    String where = " where q.companyId=:company";
    Map<String, Object> params = new HashMap<>();
    params.put("company", company);
    if (customerId != null) {
      where += " and q.customerId=:customer";
      params.put("customer", customerId);
    }
    if (status != null) {
      where += " and q.status=:status";
      params.put("status", status);
    }
    if (from != null) {
      where += " and q.createdAt>=:from";
      params.put("from", from.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant());
    }
    if (to != null) {
      where += " and q.createdAt<:to";
      params.put("to", to.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant());
    }
    var query =
        em.createQuery(
            "select q from Quotation q" + where + " order by q.createdAt desc,q.id desc",
            Quotation.class);
    var count = em.createQuery("select count(q) from Quotation q" + where, Long.class);
    params.forEach(
        (k, v) -> {
          query.setParameter(k, v);
          count.setParameter(k, v);
        });
    long total = count.getSingleResult();
    var rows =
        query.setFirstResult(Math.multiplyExact(page, size)).setMaxResults(size).getResultList();
    return new PageResult<>(
        rows.stream().map(this::view).toList(),
        total,
        (int) Math.ceil((double) total / size),
        page,
        size);
  }

  public QuotationDtos.View create(QuotationDtos.Input input) {
    var c = customer(input.customerId());
    if (c.archivedAt != null) throw new ApiException(400, "Selecione um cliente ativo.");
    validateDate(input.validUntil());
    var company = companies.lockById(CurrentUser.companyId()).orElseThrow(ApiException::missing);
    var q = new Quotation();
    q.companyId = company.id;
    q.number = company.nextQuoteNumber++;
    q.customerId = c.id;
    q.validUntil = input.validUntil();
    q.notes = input.notes();
    q.terms = input.terms();
    QuotationCalculator.apply(q, input);
    quotes.saveAndFlush(q);
    return view(q);
  }

  public QuotationDtos.View update(Long id, QuotationDtos.Input input) {
    var q = owned(id);
    if (q.status != QuotationStatus.RASCUNHO)
      throw new ApiException(409, "Somente rascunhos podem ser editados.");
    var c = customer(input.customerId());
    if (c.archivedAt != null) throw new ApiException(400, "Selecione um cliente ativo.");
    validateDate(input.validUntil());
    q.customerId = c.id;
    q.validUntil = input.validUntil();
    q.notes = input.notes();
    q.terms = input.terms();
    QuotationCalculator.apply(q, input);
    quotes.flush();
    return view(q);
  }

  private void validateDate(LocalDate date) {
    if (date.isBefore(today())) throw new ApiException(400, "Validade deve ser a partir de hoje.");
  }

  public QuotationDtos.View send(Long id) {
    var q = owned(id);
    if (q.status != QuotationStatus.RASCUNHO) throw new ApiException(409, "Orçamento já enviado.");
    validateDate(q.validUntil);
    if (q.items.isEmpty())
      throw new ApiException(400, "Adicione ao menos um item antes de enviar.");
    var c = customer(q.customerId);
    var company = companies.findById(q.companyId).orElseThrow(ApiException::missing);
    q.companyNameSnapshot = company.name;
    q.customerNameSnapshot = c.name;
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    q.publicToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    q.status = QuotationStatus.AGUARDANDO;
    q.sentAt = Instant.now();
    quotes.flush();
    return view(q);
  }

  public QuotationDtos.View revokePublic(Long id) {
    var q = owned(id);
    if (q.status == QuotationStatus.RASCUNHO)
      throw new ApiException(409, "Orçamento ainda não enviado.");
    if (q.publicRevokedAt == null) q.publicRevokedAt = Instant.now();
    quotes.flush();
    return view(q);
  }

  private Quotation publicQuote(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw ApiException.missing();
    var q = quotes.lockPublic(token).orElseThrow(ApiException::missing);
    if (q.publicRevokedAt != null) throw ApiException.missing();
    return q;
  }

  public QuotationDtos.PublicView publicGet(String token) {
    var q = publicQuote(token);
    expire(q);
    return publicView(q);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public QuotationDtos.PublicView respond(String token, boolean accepted) {
    var q = publicQuote(token);
    expire(q);
    if (q.status != QuotationStatus.AGUARDANDO)
      throw new ApiException(
          409,
          q.status == QuotationStatus.EXPIRADO
              ? "Este orçamento expirou."
              : "Este orçamento já recebeu uma resposta.");
    q.status = accepted ? QuotationStatus.ACEITO : QuotationStatus.RECUSADO;
    q.respondedAt = Instant.now();
    quotes.flush();
    return publicView(q);
  }

  public QuotationDtos.View view(Quotation q) {
    var c =
        customers
            .findByIdAndCompanyId(q.customerId, q.companyId)
            .orElseThrow(ApiException::missing);
    return new QuotationDtos.View(
        q.id,
        q.number,
        q.status,
        CustomerDtos.view(c),
        q.validUntil,
        q.notes,
        q.subtotal,
        q.discount,
        q.total,
        items(q),
        q.publicRevokedAt == null ? q.publicToken : null,
        q.sentAt,
        q.respondedAt,
        q.createdAt,
        q.updatedAt,
        q.companyNameSnapshot != null ? q.companyNameSnapshot
            : companies.findById(q.companyId).orElseThrow(ApiException::missing).name,
        q.customerNameSnapshot != null ? q.customerNameSnapshot : c.name,
        q.terms,
        q.publicRevokedAt);
  }

  private List<QuotationDtos.ItemView> items(Quotation q) {
    return q.items.stream()
        .map(i -> new QuotationDtos.ItemView(i.id, i.description, i.quantity, i.unitPrice, i.total))
        .toList();
  }

  private QuotationDtos.PublicView publicView(Quotation q) {
    return new QuotationDtos.PublicView(
        q.companyNameSnapshot,
        q.number,
        q.customerNameSnapshot,
        q.status,
        q.validUntil,
        q.subtotal,
        q.discount,
        q.total,
        q.items.stream()
            .map(
                i ->
                    new QuotationDtos.PublicItemView(
                        i.description, i.quantity, i.unitPrice, i.total))
            .toList(),
        q.respondedAt,
        q.terms);
  }
}
