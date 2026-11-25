package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalDtos.*;

import com.negociofacil.common.ApiException;
import com.negociofacil.common.PageResult;
import com.negociofacil.security.CurrentUser;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FiscalService {
  private final FiscalRepository repository;

  FiscalService(FiscalRepository repository) { this.repository = repository; }

  @Transactional
  public Detail importFile(MultipartFile file) {
    var user = CurrentUser.get();
    if (file.getSize() > FiscalXml.MAX_BYTES) throw new ApiException(413, "O XML deve ter até 1 MiB.");
    byte[] xml;
    try (var stream = file.getInputStream()) {
      xml = stream.readNBytes(FiscalXml.MAX_BYTES + 1);
    } catch (IOException e) {
      throw new ApiException(400, "Não foi possível ler o arquivo enviado.");
    }
    var note = FiscalXml.parse(xml);
    String document = repository.companyDocument(user.companyId()).replaceAll("[. /-]", "");
    if (!document.isEmpty() && !document.equals(note.providerDocument())) {
      throw new ApiException(422, "O CPF ou CNPJ do prestador difere do documento cadastrado na empresa.");
    }
    long id = repository.insert(user.companyId(), user.userId(), note, xml, sha256(xml));
    repository.audit(id, user.userId(), "IMPORTACAO");
    return repository.detail(user.companyId(), id);
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public PageResult<Row> list(Filter filter, Order order, int page, int size) {
    validate(filter);
    if (page < 0 || size < 1 || size > 100) throw new ApiException(400, "Paginação inválida.");
    return repository.list(CurrentUser.companyId(), filter, order, page, size);
  }

  public Summary summary(Filter filter) {
    validate(filter);
    return repository.summary(CurrentUser.companyId(), filter);
  }

  public Detail detail(long id) { return repository.detail(CurrentUser.companyId(), id); }

  @Transactional
  public Detail links(long id, Links links) {
    var user = CurrentUser.get();
    if (links.quotationItemId() != null && links.quotationId() == null) {
      throw new ApiException(400, "Selecione o orçamento do serviço.");
    }
    try {
      repository.updateLinks(user.companyId(), id, links);
    } catch (DataIntegrityViolationException e) {
      throw ApiException.missing();
    }
    repository.audit(id, user.userId(), "VINCULOS");
    return repository.detail(user.companyId(), id);
  }

  FiscalRepository.Document document(long id) { return repository.document(CurrentUser.companyId(), id); }

  void audit(long id, String operation) { repository.audit(id, CurrentUser.get().userId(), operation); }

  List<Option> options(String kind, String search, Long quotation, Long selected) {
    if (search.length() > 200) throw new ApiException(400, "A busca deve ter até 200 caracteres.");
    return repository.options(CurrentUser.companyId(), kind, search.strip(), quotation, selected);
  }

  private static void validate(Filter filter) {
    checkDates(filter.from(), filter.to());
    checkDates(filter.competenceFrom(), filter.competenceTo());
    if (filter.search() != null && filter.search().length() > 200) {
      throw new ApiException(400, "A busca deve ter até 200 caracteres.");
    }
  }

  private static void checkDates(LocalDate from, LocalDate to) {
    if (from != null && to != null && to.isBefore(from)) {
      throw new ApiException(400, "A data final deve ser igual ou posterior à data inicial.");
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
