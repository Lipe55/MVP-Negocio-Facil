package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransferRepository extends JpaRepository<FinancialTransfer, Long> {
  java.util.Optional<FinancialTransfer> findByCompanyIdAndRequestId(Long companyId, java.util.UUID requestId);
  java.util.Optional<FinancialTransfer> findByIdAndCompanyId(Long id, Long companyId);
}
