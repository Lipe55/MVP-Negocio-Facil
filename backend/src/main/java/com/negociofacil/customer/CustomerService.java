package com.negociofacil.customer;

import com.negociofacil.common.*;
import com.negociofacil.quotation.QuotationRepository;
import com.negociofacil.security.CurrentUser;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {
  private final CustomerRepository customers;
  private final QuotationRepository quotes;

  public CustomerService(CustomerRepository customers, QuotationRepository quotes) {
    this.customers = customers;
    this.quotes = quotes;
  }

  public Customer owned(Long id) {
    return customers
        .findByIdAndCompanyId(id, CurrentUser.companyId())
        .orElseThrow(ApiException::missing);
  }

  public CustomerDtos.View get(Long id) {
    return CustomerDtos.view(owned(id));
  }

  public PageResult<CustomerDtos.View> list(String search, boolean archived, int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || search.length() > 160)
      throw new ApiException(400, "Paginação ou busca inválida.");
    String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    var result =
        customers.search(
            CurrentUser.companyId(),
            escaped,
            archived,
            PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
    return new PageResult<>(
        result.map(CustomerDtos::view).getContent(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        size);
  }

  public CustomerDtos.View create(CustomerDtos.Input input) {
    var c = new Customer();
    c.companyId = CurrentUser.companyId();
    apply(c, input);
    return CustomerDtos.view(customers.saveAndFlush(c));
  }

  public CustomerDtos.View update(Long id, CustomerDtos.Input input) {
    var c = owned(id);
    apply(c, input);
    customers.flush();
    return CustomerDtos.view(c);
  }

  public void delete(Long id) {
    var c = owned(id);
    if (quotes.existsByCustomerIdAndCompanyId(id, c.companyId))
      throw new ApiException(
          409, "Cliente possui orçamentos. Arquive-o para preservar o histórico.");
    customers.delete(c);
    customers.flush();
  }

  public CustomerDtos.View archive(Long id) {
    var c = owned(id);
    c.archivedAt = Instant.now();
    customers.flush();
    return CustomerDtos.view(c);
  }

  private void apply(Customer c, CustomerDtos.Input input) {
    c.name = input.name().trim();
    c.phone = input.phone();
    c.email = input.email();
    c.document = input.document();
    c.notes = input.notes();
  }
}
