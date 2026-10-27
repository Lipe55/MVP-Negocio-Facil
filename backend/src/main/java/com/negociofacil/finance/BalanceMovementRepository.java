package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceMovementRepository extends JpaRepository<BalanceMovement, Long> {
  java.util.Optional<BalanceMovement> findByCompanyIdAndSettlementId(Long companyId, Long settlementId);
  java.util.List<BalanceMovement> findByCompanyIdAndTransferIdOrderById(Long companyId, Long transferId);
  boolean existsByCompanyIdAndAccountId(Long companyId, Long accountId);
}
