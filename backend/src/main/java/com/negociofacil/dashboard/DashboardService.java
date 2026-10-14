package com.negociofacil.dashboard;

import com.negociofacil.customer.CustomerRepository;
import com.negociofacil.quotation.*;
import com.negociofacil.security.CurrentUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DashboardService {
  public record Summary(
      long customers,
      long quotations,
      long awaiting,
      long accepted,
      List<QuotationDtos.View> recent) {}

  private final CustomerRepository customers;
  private final QuotationRepository quotes;
  private final QuotationService service;
  private final EntityManager em;

  public DashboardService(
      CustomerRepository customers,
      QuotationRepository quotes,
      QuotationService service,
      EntityManager em) {
    this.customers = customers;
    this.quotes = quotes;
    this.service = service;
    this.em = em;
  }

  public Summary summary() {
    Long company = CurrentUser.companyId();
    quotes.expire(company, QuotationService.today());
    return new Summary(
        customers.countByCompanyIdAndArchivedAtIsNull(company),
        count(company, null),
        count(company, QuotationStatus.AGUARDANDO),
        count(company, QuotationStatus.ACEITO),
        service.list(null, null, null, null, 0, 5).content());
  }

  private long count(Long company, QuotationStatus status) {
    var query =
        em.createQuery(
                "select count(q) from Quotation q where q.companyId=:company"
                    + (status == null ? "" : " and q.status=:status"),
                Long.class)
            .setParameter("company", company);
    if (status != null) query.setParameter("status", status);
    return query.getSingleResult();
  }
}
