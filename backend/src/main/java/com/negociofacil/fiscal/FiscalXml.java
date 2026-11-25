package com.negociofacil.fiscal;

import com.negociofacil.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class FiscalXml {
  static final int MAX_BYTES = 1_048_576;
  private static final String NS = "http://www.sped.fazenda.gov.br/nfse";

  enum Status {
    VALIDA(0), SUBSTITUIDA(1), CANCELADA(2), NAO_VERIFICADA(null);
    final Integer generatorCode;
    Status(Integer generatorCode) { this.generatorCode = generatorCode; }
  }

  record Note(String number, String accessKey, Status status, LocalDate issuedOn,
              LocalDate competence, String providerDocument, String providerName,
              String recipientDocument, String recipientName, String municipality,
              String description, String serviceCode, BigDecimal gross, BigDecimal net,
              Map<String, BigDecimal> amounts) {}

  static Note parse(byte[] bytes) {
    if (bytes.length > MAX_BYTES) throw new ApiException(413, "O XML deve ter até 1 MiB.");
    try {
      var factory = DocumentBuilderFactory.newNSInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setAttribute("jdk.xml.maxElementDepth", "64");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(new DefaultHandler());
      var xml = builder.parse(new ByteArrayInputStream(bytes));
      if (!"UTF-8".equalsIgnoreCase(xml.getInputEncoding())
          || (xml.getXmlEncoding() != null && !"UTF-8".equalsIgnoreCase(xml.getXmlEncoding()))) throw incompatible();
      Element root = xml.getDocumentElement();
      if (!NS.equals(root.getNamespaceURI()) || !"NFSe".equals(root.getLocalName())) throw incompatible();

      validateMunicipalityCodes(root);
      Element info = child(root, "infNFSe");
      String key = info == null ? "" : info.getAttribute("Id");
      String number = value(info, "nNFSe");
      Element dps = child(info, "DPS", "infDPS");
      Element issuer = child(info, "emit");
      String provider = document(issuer);
      String processingDate = value(info, "dhProc");
      if (!key.matches("NFS[0-9]{50}") || number == null || !number.matches("[0-9]{1,20}")
          || dps == null || provider == null || processingDate == null) throw incompatible();

      Element recipient = child(dps, "toma");
      Element service = child(dps, "serv");
      Element serviceCode = child(service, "cServ");
      Element totals = child(info, "valores");
      Element declared = child(dps, "valores");
      Map<String, BigDecimal> amounts = new LinkedHashMap<>();
      collect(amounts, totals, "vBC", "vISSQN", "vCalcBM", "vCalcDR", "vTotalRet");
      collect(amounts, child(declared, "vDescCondIncond"), "vDescCond", "vDescIncond");
      collect(amounts, child(declared, "vDedRed"), "vDR");
      Element taxes = child(declared, "trib");
      Element federal = child(taxes, "tribFed");
      collect(amounts, federal, "vRetIRRF", "vRetCSLL", "vRetCP");
      collect(amounts, child(federal, "piscofins"), "vPis", "vCofins");
      collect(amounts, child(taxes, "totTrib", "vTotTrib"), "vTotTribFed", "vTotTribEst", "vTotTribMun");
      Element taxReform = child(info, "IBSCBS", "totCIBS");
      collect(amounts, taxReform, "vTotNF");
      collect(amounts, child(taxReform, "gIBS"), "vIBSTot");
      collect(amounts, child(taxReform, "gIBS", "gIBSMunTot"), "vIBSMun");
      collect(amounts, child(taxReform, "gIBS", "gIBSUFTot"), "vIBSUF");
      collect(amounts, child(taxReform, "gCBS"), "vCBS");
      String competence = value(dps, "dCompet");
      return new Note(number, key.substring(3), status(info), OffsetDateTime.parse(processingDate).toLocalDate(),
          competence == null ? null : LocalDate.parse(competence), provider, value(issuer, "xNome"),
          document(recipient), value(recipient, "xNome"),
          first(value(info, "xLocPrestacao"), value(service, "locPrest", "cLocPrestacao")),
          value(serviceCode, "xDescServ"), first(value(serviceCode, "cTribNac"), value(serviceCode, "cTribMun")),
          money(value(declared, "vServPrest", "vServ")), money(value(totals, "vLiq")), amounts);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Configuração segura do XML indisponível", e);
    } catch (SAXException | IOException | DateTimeParseException | IllegalArgumentException | ArithmeticException e) {
      throw new ApiException(422, "O XML está malformado ou possui campos fiscais inválidos.");
    }
  }

  private static void validateMunicipalityCodes(Element root) {
    // The upstream renderer logs unrecognized locality codes. Forward only numeric codes.
    for (String name : new String[] {"cLocIncid", "cMun", "cLocalidadeIncid", "cLocPrestacao"}) {
      var entries = root.getElementsByTagNameNS("*", name);
      for (int i = 0; i < entries.getLength(); i++) {
        var element = (Element) entries.item(i);
        for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
          if (node instanceof Element) throw incompatible();
        }
        String code = element.getTextContent();
        if (!code.isEmpty() && !code.matches("[0-9]{7}")) throw incompatible();
      }
    }
  }

  private static Status status(Element info) {
    String code = value(info, "cStat");
    return switch (code == null ? "" : code) {
      case "100" -> Status.VALIDA;
      case "101" -> Status.CANCELADA;
      case "102" -> Status.SUBSTITUIDA;
      default -> Status.NAO_VERIFICADA;
    };
  }

  private static ApiException incompatible() {
    return new ApiException(422, "Use um XML de NFS-e do padrão nacional com os dados da nota e do emitente.");
  }

  private static String document(Element parent) {
    String cnpj = value(parent, "CNPJ");
    String cpf = value(parent, "CPF");
    if (cnpj == null && cpf == null) return null;
    if (cnpj == null && cpf.matches("[0-9]{11}")) return cpf;
    if (cpf == null && cnpj.matches("[0-9]{14}")) return cnpj;
    throw incompatible();
  }

  private static String first(String primary, String fallback) {
    return primary == null ? fallback : primary;
  }

  private static void collect(Map<String, BigDecimal> amounts, Element parent, String... fields) {
    for (String field : fields) {
      BigDecimal amount = money(value(parent, field));
      if (amount != null) amounts.put(field, amount);
    }
  }

  private static BigDecimal money(String value) {
    if (value == null) return null;
    if (!value.matches("[0-9]{1,14}(\\.[0-9]{1,2})?")) throw incompatible();
    return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
  }

  private static String value(Element parent, String... path) {
    Element element = child(parent, path);
    if (element == null) return null;
    for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element) throw incompatible();
    }
    String text = element.getTextContent().strip();
    return text.isEmpty() ? null : text;
  }

  private static Element child(Element parent, String... path) {
    for (String name : path) {
      if (parent == null) return null;
      Element found = null;
      for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node instanceof Element element && name.equals(element.getLocalName())) {
          if (!NS.equals(element.getNamespaceURI()) || found != null) throw incompatible();
          found = element;
        }
      }
      parent = found;
    }
    return parent;
  }
}
