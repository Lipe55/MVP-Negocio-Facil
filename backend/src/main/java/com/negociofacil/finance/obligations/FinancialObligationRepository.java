package com.negociofacil.finance.obligations;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialObligationRepository extends JpaRepository<FinancialObligation,Long> {
  Optional<FinancialObligation> findByIdAndCompanyId(Long id,Long companyId);
  Optional<FinancialObligation> findByCompanyIdAndRequestId(Long companyId,UUID requestId);
  List<FinancialObligation> findByCompanyIdAndRecurrenceIdOrderByOccurrenceNumber(Long companyId,Long recurrenceId);
}
