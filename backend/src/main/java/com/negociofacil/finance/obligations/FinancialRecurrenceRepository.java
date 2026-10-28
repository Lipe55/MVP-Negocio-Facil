package com.negociofacil.finance.obligations;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialRecurrenceRepository extends JpaRepository<FinancialRecurrence,Long> {
  Optional<FinancialRecurrence> findByIdAndCompanyId(Long id,Long companyId);
  Optional<FinancialRecurrence> findByCompanyIdAndRequestId(Long companyId,UUID requestId);
}
