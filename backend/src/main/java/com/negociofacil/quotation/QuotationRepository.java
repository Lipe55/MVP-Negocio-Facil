package com.negociofacil.quotation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
  Optional<Quotation> findByIdAndCompanyId(Long id, Long companyId);

  boolean existsByCustomerIdAndCompanyId(Long customerId, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quotation q where q.id=:id and q.companyId=:companyId")
  Optional<Quotation> lockOwned(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quotation q where q.publicToken=:token")
  Optional<Quotation> lockPublic(String token);

  @Modifying
  @Query(
      "update Quotation q set q.status=com.negociofacil.quotation.QuotationStatus.EXPIRADO,"
          + " q.updatedAt=CURRENT_TIMESTAMP where q.companyId=:companyId and"
          + " q.status=com.negociofacil.quotation.QuotationStatus.AGUARDANDO and"
          + " q.validUntil<:today")
  int expire(Long companyId, java.time.LocalDate today);
}
