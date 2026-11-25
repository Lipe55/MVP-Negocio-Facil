package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.negociofacil.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FiscalXmlTest {
  @Test
  void extractsNationalInvoiceWithoutRoundingMoneyOrShiftingItsLocalDate() {
    var note = FiscalXml.parse(complete(1));
    assertThat(note.issuedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(note.competence()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(note.providerDocument()).isEqualTo(PROVIDER);
    assertThat(note.gross()).isEqualTo(new BigDecimal("115.30"));
    assertThat(note.net()).isEqualTo(new BigDecimal("110.20"));
    assertThat(note.amounts()).containsEntry("vISSQN", new BigDecimal("2.25"))
        .containsEntry("vDescIncond", new BigDecimal("5.10"));
    assertThat(note.status()).isEqualTo(FiscalXml.Status.CANCELADA);
  }

  @Test
  void acceptsAbsentOptionalFields() {
    var note = FiscalXml.parse(xml(1, "<DPS><infDPS/></DPS>"));
    assertThat(note.competence()).isNull();
    assertThat(note.recipientName()).isNull();
    assertThat(note.gross()).isNull();
    assertThat(note.status()).isEqualTo(FiscalXml.Status.NAO_VERIFICADA);
    assertThat(note.amounts()).isEmpty();
  }

  @Test
  void rejectsMalformedAndIncompatibleInput() {
    check(bytes("<NFSe>"), 422);
    check(bytes("<CompNfse/>"), 422);
    check(xml(2, "<DPS><infDPS/></DPS><nNFSe>2</nNFSe>"), 422);
    check(xml(2, "<DPS><infDPS/></DPS><valores><vLiq>1e99</vLiq></valores>"), 422);
  }

  @Test
  void rejectsExternalEntitiesAndLargeFiles() {
    check(bytes("<!DOCTYPE NFSe [<!ENTITY outside SYSTEM 'file:///synthetic'>]><NFSe>&outside;</NFSe>"), 422);
    check(new byte[FiscalXml.MAX_BYTES + 1], 413);
  }

  private void check(byte[] content, int status) {
    assertThatExceptionOfType(ApiException.class).isThrownBy(() -> FiscalXml.parse(content))
        .satisfies(error -> assertThat(error.status).isEqualTo(status));
  }
}
