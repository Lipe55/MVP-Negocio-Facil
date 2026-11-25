package com.negociofacil.fiscal;

import java.nio.charset.StandardCharsets;

final class FiscalFixture {
  static final String PROVIDER = "12345678000100";

  static byte[] xml(int number, String extra) {
    return bytes("""
        <?xml version="1.0" encoding="UTF-8"?>
        <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.00">
          <infNFSe Id="NFS%s">
            <nNFSe>%d</nNFSe>
            <dhProc>2026-09-01T23:55:00-03:00</dhProc>
            <emit><CNPJ>%s</CNPJ><xNome>Prestador de teste</xNome></emit>
            %s
          </infNFSe>
        </NFSe>
        """.formatted("%050d".formatted(number), number, PROVIDER, extra));
  }

  static byte[] complete(int number) {
    return xml(number, """
        <cStat>101</cStat>
        <valores><vLiq>110.20</vLiq><vISSQN>2.25</vISSQN></valores>
        <DPS><infDPS>
          <dCompet>2026-08-31</dCompet>
          <tpAmb>2</tpAmb>
          <toma><xNome>Tomador de teste</xNome></toma>
          <serv><cServ><cTribNac>010101</cTribNac><xDescServ>Serviço sintético</xDescServ></cServ></serv>
          <valores><vServPrest><vServ>115.30</vServ></vServPrest>
            <vDescCondIncond><vDescIncond>5.10</vDescIncond></vDescCondIncond>
          </valores>
        </infDPS></DPS>
        """);
  }

  static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
