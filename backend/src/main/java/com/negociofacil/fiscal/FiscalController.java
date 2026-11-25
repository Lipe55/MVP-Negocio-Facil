package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalDtos.*;

import com.negociofacil.common.PageResult;
import com.negociofacil.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fiscal")
public class FiscalController {
  private final FiscalService service;
  private final DanfseClient generator;

  FiscalController(FiscalService service, DanfseClient generator) {
    this.service = service;
    this.generator = generator;
  }

  @PostMapping(value = "/notas/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Detail upload(@RequestParam("file") MultipartFile file) { return service.importFile(file); }

  @GetMapping("/notas")
  public PageResult<Row> list(@ModelAttribute Filter filter,
      @RequestParam(defaultValue = "EMISSAO_RECENTE") Order order,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.list(filter, order, page, size);
  }

  @GetMapping("/resumo")
  public Summary summary(@ModelAttribute Filter filter) { return service.summary(filter); }

  @GetMapping("/notas/{id}")
  public Detail detail(@PathVariable long id) { return service.detail(id); }

  @PatchMapping("/notas/{id}/vinculos")
  public Detail links(@PathVariable long id, @RequestBody Links links) { return service.links(id, links); }

  @GetMapping("/vinculos/{kind}")
  public List<Option> options(@PathVariable String kind, @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) Long quotationId, @RequestParam(required = false) Long selected) {
    return service.options(kind, search, quotationId, selected);
  }

  @GetMapping("/gerador")
  public Map<String, Boolean> readiness() {
    CurrentUser.get();
    return Map.of("available", generator.ready());
  }

  @GetMapping("/notas/{id}/xml")
  public ResponseEntity<byte[]> xml(@PathVariable long id) {
    var document = service.document(id);
    service.audit(id, "DOWNLOAD_XML");
    return file(document.xml(), MediaType.APPLICATION_XML, "nfse-" + id + ".xml", true);
  }

  @GetMapping("/notas/{id}/danfse")
  public ResponseEntity<byte[]> pdf(@PathVariable long id,
      @RequestParam(defaultValue = "false") boolean download,
      @RequestParam(defaultValue = "true") boolean canhoto) {
    byte[] pdf = generator.generate(service.document(id), canhoto);
    service.audit(id, download ? "DOWNLOAD_PDF" : "VISUALIZACAO_PDF");
    return file(pdf, MediaType.APPLICATION_PDF, "danfse-" + id + ".pdf", download);
  }

  private static ResponseEntity<byte[]> file(byte[] bytes, MediaType type, String name, boolean download) {
    var disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(name).build();
    return ResponseEntity.ok().contentType(type).contentLength(bytes.length).cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(bytes);
  }
}
