package com.negociofacil.customer;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

  long countByCompanyIdAndArchivedAtIsNull(Long companyId);

  @Query(
      "select c from Customer c where c.companyId=:companyId and (:archived=true or c.archivedAt is"
          + " null) and lower(c.name) like lower(concat('%',:search,'%')) escape '\\'")
  Page<Customer> search(Long companyId, String search, boolean archived, Pageable pageable);
}
