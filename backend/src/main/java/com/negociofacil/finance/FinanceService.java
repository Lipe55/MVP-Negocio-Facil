package com.negociofacil.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociofacil.common.*;
import com.negociofacil.company.CompanyRepository;
import com.negociofacil.security.CurrentUser;
import com.negociofacil.finance.obligations.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.negociofacil.finance.FinanceTypes.*;

@Service
@Transactional
public class FinanceService {
  private final CompanyRepository companies;
  private final FinancialAccountRepository accounts;
  private final FinancialCategoryRepository categories;
  private final FinancialEntryRepository entries;
  private final FinancialSettlementRepository settlements;
  private final BalanceMovementRepository movements;
  private final FinancialTransferRepository transfers;
  private final FinancialAuditRepository audits;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final FinancialObligationRepository obligations;
  private final FinancialRecurrenceRepository recurrences;

  public FinanceService(CompanyRepository companies, FinancialAccountRepository accounts,
      FinancialCategoryRepository categories, FinancialEntryRepository entries,
      FinancialSettlementRepository settlements, BalanceMovementRepository movements,
      FinancialTransferRepository transfers, FinancialAuditRepository audits,
      JdbcTemplate jdbc, ObjectMapper json,FinancialObligationRepository obligations,FinancialRecurrenceRepository recurrences) {
    this.companies=companies; this.accounts=accounts; this.categories=categories;
    this.entries=entries; this.settlements=settlements; this.movements=movements;
    this.transfers=transfers; this.audits=audits; this.jdbc=jdbc; this.json=json;
    this.obligations=obligations;this.recurrences=recurrences;
  }

  private Long company() { return CurrentUser.companyId(); }
  // One lock order for all money mutations, including transfers and reversals.
  private void lockCompany() {
    companies.lockById(company()).orElseThrow(ApiException::missing);
  }
  private FinancialAccount account(Long id) {
    return accounts.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
  }
  private FinancialAccount activeAccount(Long id) {
    var account = id == null
        ? accounts.findByCompanyIdAndPrimaryAccountTrue(company()).orElseThrow(ApiException::missing)
        : account(id);
    if (!account.active) throw new ApiException(409,"Reative a conta antes de movimentá-la.");
    return account;
  }
  private FinancialEntry entry(Long id) {
    return entries.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
  }
  private BigDecimal balance(FinancialAccount account) {
    return account.openingBalance.add(jdbc.queryForObject(
        "select coalesce(sum(amount),0) from financial_movements where company_id=? and account_id=?",
        BigDecimal.class, company(), account.id)).setScale(2);
  }
  private FinanceDtos.AccountView accountView(FinancialAccount a) {
    return new FinanceDtos.AccountView(a.id,a.name,a.type,a.primaryAccount,a.active,
        a.openingBalance,a.openingDate,balance(a),a.version);
  }
  private FinanceDtos.EntryView entryView(FinancialEntry e) {
    var a=account(e.accountId);
    var category=e.categoryId==null ? null : categories.findByIdAndCompanyId(e.categoryId,company())
        .orElseThrow(ApiException::missing);
    var history=settlements.findByCompanyIdAndEntryIdOrderById(company(),e.id);
    var active=history.stream().filter(s->s.reversedAt==null).toList();var settlement=active.isEmpty()?null:active.getLast();
    var parent=e.obligationId==null?null:obligations.findByIdAndCompanyId(e.obligationId,company()).orElseThrow(ApiException::missing);
    BigDecimal paid=active.stream().map(s->s.amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
    BigDecimal remaining=e.status==Status.CANCELADO?new BigDecimal("0.00"):e.amount.subtract(paid);
    boolean overdue=e.status!=Status.CANCELADO && remaining.signum()>0 && e.dueDate!=null && e.dueDate.isBefore(FinanceRules.today());
    PaymentStatus paymentStatus=e.status==Status.CANCELADO?PaymentStatus.CANCELADO:remaining.signum()==0?PaymentStatus.QUITADO:
        overdue?PaymentStatus.VENCIDO:paid.signum()>0?PaymentStatus.PARCIAL:PaymentStatus.PENDENTE;
    return new FinanceDtos.EntryView(e.id,e.kind,e.status,a.id,a.name,e.categoryId,
        category==null?null:category.name,e.description,e.amount,e.entryDate,
        settlement==null?null:settlement.settlementDate,e.notes,e.version,e.createdAt,
        e.obligationId,e.installmentNumber,parent==null?null:parent.installmentCount,e.dueDate,paid,remaining,paymentStatus,overdue,
        parent==null?null:parent.recurrenceId,parent==null?null:parent.occurrenceNumber,
        parent!=null && !Set.of("QUICK","LEGACY").contains(parent.source),!history.isEmpty());
  }
  private List<FinancialSettlement> activeSettlements(FinancialEntry e) {
    return settlements.findByCompanyIdAndEntryIdAndReversedAtIsNullOrderById(company(),e.id);
  }
  private BigDecimal paid(FinancialEntry e) {
    return activeSettlements(e).stream().map(s->s.amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }
  private void refreshStatus(FinancialEntry e) {
    var amount=paid(e);e.status=amount.signum()==0?Status.PENDENTE:amount.compareTo(e.amount)==0?Status.LIQUIDADO:Status.PARCIAL;
    e.updatedAt=Instant.now();entries.flush();
  }
  private void touchParent(FinancialEntry e,BigDecimal beforeAmount,String action,Object before,Object after) {
    if(e.obligationId==null)return;
    var p=obligations.findByIdAndCompanyId(e.obligationId,company()).orElseThrow(ApiException::missing);
    p.totalAmount=p.totalAmount.subtract(beforeAmount).add(e.amount);
    if(p.totalAmount.precision()-p.totalAmount.scale()>14)throw new ApiException(400,"O total da obrigação ultrapassa o limite monetário.");
    if(p.installmentCount==1)p.description=e.description;
    p.updatedAt=Instant.now();obligations.flush();
    audit("OBRIGACAO",p.id,action,before,after);
    if(p.recurrenceId!=null) {
      var recurrence=recurrences.findByIdAndCompanyId(p.recurrenceId,company()).orElseThrow(ApiException::missing);
      recurrence.updatedAt=Instant.now();recurrences.flush();
    }
  }
  private FinanceDtos.TransferView transferView(FinancialTransfer t) {
    return new FinanceDtos.TransferView(t.id,t.sourceAccountId,t.targetAccountId,t.amount,
        t.transferDate,t.description,t.canceledAt,t.version);
  }
  private String serialize(Object value) {
    try { return json.writeValueAsString(value); }
    catch(JsonProcessingException e) { throw new IllegalStateException("Falha ao registrar histórico.",e); }
  }
  private String hash(Object value) {
    try {
      // Keep idempotency compatible with V4 payloads when callers omit the new
      // optional fields. ObjectNode retains the original record field order and
      // the application's monetary/date serialization used by the old hashes.
      Object canonical=value;
      if(value instanceof FinanceDtos.EntryInput input && input.dueDate()==null) {
        com.fasterxml.jackson.databind.node.ObjectNode tree=json.valueToTree(value);tree.remove("dueDate");canonical=tree;
      } else if(value instanceof FinanceDtos.SettlementInput input && input.amount()==null) {
        com.fasterxml.jackson.databind.node.ObjectNode tree=json.valueToTree(value);tree.remove("amount");canonical=tree;
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(serialize(canonical).getBytes(StandardCharsets.UTF_8)));
    } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private void sameRequest(String expected, String actual) {
    if (!expected.equals(actual))
      throw new ApiException(409,"Identificador de operação já utilizado com outros dados.");
  }
  private void audit(String resource, Long id, String action, Object before, Object after) {
    var a=new FinancialAudit(); a.companyId=company(); a.actorId=CurrentUser.get().userId();
    a.resource=resource; a.resourceId=id; a.action=action;
    a.beforeData=before==null?null:serialize(before); a.afterData=serialize(after);
    audits.saveAndFlush(a);
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public List<FinanceDtos.AccountView> accounts() {
    return accounts.findByCompanyIdOrderById(company()).stream().map(this::accountView).toList();
  }
  public FinanceDtos.AccountView createAccount(FinanceDtos.AccountInput input) {
    lockCompany();
    if (!input.active()) throw new ApiException(400,"Crie a conta como ativa.");
    var a=new FinancialAccount(); a.companyId=company();
    applyAccount(a,input); accounts.saveAndFlush(a);
    var result=accountView(a); audit("CONTA",a.id,"CRIACAO",null,result); return result;
  }
  public FinanceDtos.AccountView updateAccount(Long id, FinanceDtos.AccountInput input) {
    lockCompany(); var a=account(id); FinanceRules.version(input.version(),a.version);
    var before=accountView(a);
    if (a.primaryAccount && !input.active()) throw new ApiException(409,"A Conta principal deve permanecer ativa.");
    boolean hasMovements=movements.existsByCompanyIdAndAccountId(company(),id);
    if (hasMovements && (a.openingBalance.compareTo(input.openingBalance())!=0
        || !a.openingDate.equals(input.openingDate())))
      throw new ApiException(409,"O saldo inicial e sua data não podem mudar após movimentações. Use lançamentos de ajuste.");
    BigDecimal proposedBalance=balance(a).subtract(a.openingBalance).add(input.openingBalance());
    if (!input.active() && (proposedBalance.signum()!=0
        || entries.existsByCompanyIdAndAccountIdAndStatusIn(company(),id,List.of(Status.PENDENTE,Status.PARCIAL))))
      throw new ApiException(409,"A conta deve ter saldo zero e nenhum lançamento pendente para ser desativada.");
    applyAccount(a,input); accounts.flush();
    var result=accountView(a); audit("CONTA",id,"EDICAO",before,result); return result;
  }
  private void applyAccount(FinancialAccount a, FinanceDtos.AccountInput input) {
    if (input.openingDate().isAfter(FinanceRules.today()))
      throw new ApiException(400,"A data do saldo inicial não pode ser futura.");
    a.name=input.name().trim(); a.type=input.type(); a.active=input.active();
    a.openingBalance=input.openingBalance().setScale(2); a.openingDate=input.openingDate();
  }

  @Transactional(readOnly=true)
  public List<FinanceDtos.CategoryView> categories() {
    return categories.findByCompanyIdOrderByKindAscNameAsc(company()).stream()
        .map(c->new FinanceDtos.CategoryView(c.id,c.name,c.kind)).toList();
  }
  public FinanceDtos.CategoryView createCategory(FinanceDtos.CategoryInput input) {
    lockCompany();
    if (input.kind()==Kind.RETIRADA) throw new ApiException(400,"Retiradas são separadas das categorias operacionais.");
    var c=new FinancialCategory(); c.companyId=company(); c.name=input.name().trim(); c.kind=input.kind();
    categories.saveAndFlush(c); var result=new FinanceDtos.CategoryView(c.id,c.name,c.kind);
    audit("CATEGORIA",c.id,"CRIACAO",null,result); return result;
  }
  private void validateCategory(Kind kind,Long id) {
    if (kind==Kind.RETIRADA) {
      if(id!=null) throw new ApiException(400,"Retirada não usa categoria operacional.");
    } else {
      if(id==null) throw new ApiException(400,"Selecione uma categoria.");
      var c=categories.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
      if(c.kind!=kind) throw new ApiException(400,"Categoria incompatível com o lançamento.");
    }
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public PageResult<FinanceDtos.EntryView> entries(Kind kind,Status status,Long accountId,Long categoryId,
      LocalDate from,LocalDate to,String search,int page,int size) {
    FinanceRules.page(page,size);
    if (search.length()>160 || (from!=null && to!=null && from.isAfter(to)))
      throw new ApiException(400,"Filtros inválidos.");
    if(accountId!=null) account(accountId);
    if(categoryId!=null) categories.findByIdAndCompanyId(categoryId,company()).orElseThrow(ApiException::missing);
    var result=entries.findAll((root,query,cb)->{
      var predicates=new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("companyId"),company()));
      if(kind!=null) predicates.add(cb.equal(root.get("kind"),kind));
      if(status!=null) predicates.add(cb.equal(root.get("status"),status));
      if(accountId!=null) predicates.add(cb.equal(root.get("accountId"),accountId));
      if(categoryId!=null) predicates.add(cb.equal(root.get("categoryId"),categoryId));
      if(from!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"),from));
      if(to!=null) predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"),to));
      if(!search.isBlank()) {
        String escaped=search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        predicates.add(cb.like(cb.lower(root.get("description")),"%"+escaped+"%",'\\'));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    },PageRequest.of(page,size,Sort.by(Sort.Order.desc("entryDate"),Sort.Order.desc("id"))));
    return new PageResult<>(result.stream().map(this::entryView).toList(),result.getTotalElements(),result.getTotalPages(),page,size);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public FinanceDtos.EntryView getEntry(Long id) { return entryView(entry(id)); }

  public FinanceDtos.EntryView createEntry(FinanceDtos.EntryInput input) {
    lockCompany(); String hash=hash(input);
    var previous=entries.findByCompanyIdAndRequestId(company(),input.requestId()).orElse(null);
    if(previous!=null) { sameRequest(previous.requestHash,hash); return entryView(previous); }
    var a=activeAccount(input.accountId()); validateCategory(input.kind(),input.categoryId());
    validateSettlementInput(input,a);
    var e=new FinancialEntry(); e.companyId=company(); e.kind=input.kind();
    e.requestId=input.requestId(); e.requestHash=hash; applyEntry(e,input,a.id);
    if(e.kind!=Kind.RETIRADA) {
      if(obligations.findByCompanyIdAndRequestId(company(),input.requestId()).isPresent())
        throw new ApiException(409,"Identificador já utilizado em outra obrigação.");
      var p=new FinancialObligation();p.companyId=company();p.kind=e.kind;p.description=e.description;p.totalAmount=e.amount;
      p.installmentCount=1;p.source="QUICK";p.requestId=input.requestId();p.requestHash=hash;
      obligations.saveAndFlush(p);e.obligationId=p.id;e.installmentNumber=1;
    }
    entries.saveAndFlush(e);
    if(input.settled()) settle(e,e.amount,input.settlementDate()==null?FinanceRules.today():input.settlementDate(),UUID.randomUUID(),"CRIACAO");
    entries.flush(); var result=entryView(e); audit("LANCAMENTO",e.id,"CRIACAO",null,result);
    if(e.obligationId!=null)audit("OBRIGACAO",e.obligationId,"CRIACAO",null,result);
    return result;
  }
  private void validateSettlementInput(FinanceDtos.EntryInput input,FinancialAccount account) {
    if(input.settled()) FinanceRules.effectiveDate(input.settlementDate()==null?FinanceRules.today():input.settlementDate(),account.openingDate);
    else if(input.settlementDate()!=null) throw new ApiException(400,"Lançamento pendente não tem data de liquidação.");
  }
  private void applyEntry(FinancialEntry e,FinanceDtos.EntryInput input,Long accountId) {
    ObligationRules.date(input.entryDate());ObligationRules.date(input.dueDate());
    e.accountId=accountId; e.categoryId=input.categoryId(); e.description=input.description().trim();
    e.amount=input.amount().setScale(2); e.entryDate=input.entryDate()==null?FinanceRules.today():input.entryDate();
    e.notes=input.notes(); e.status=input.settled()?Status.LIQUIDADO:Status.PENDENTE;
    e.dueDate=input.dueDate();
  }
  public FinanceDtos.EntryView updateEntry(Long id,FinanceDtos.EntryInput input) {
    lockCompany(); var e=entry(id); FinanceRules.version(input.version(),e.version);
    if(e.status==Status.CANCELADO) throw new ApiException(409,"Lançamento cancelado não pode ser editado.");
    if(e.kind!=input.kind()) throw new ApiException(400,"A natureza do lançamento não pode ser alterada.");
    var parent=e.obligationId==null?null:obligations.findByIdAndCompanyId(e.obligationId,company()).orElseThrow(ApiException::missing);
    if(parent!=null && (parent.installmentCount>1 || parent.recurrenceId!=null))
      throw new ApiException(409,"Edite esta parcela em Contas a pagar ou Contas a receber.");
    var a=activeAccount(input.accountId()); validateCategory(input.kind(),input.categoryId()); validateSettlementInput(input,a);
    var before=entryView(e);var beforeAmount=e.amount;
    boolean legacyFullEdit=e.status==Status.LIQUIDADO && (parent==null || Set.of("QUICK","LEGACY").contains(parent.source));
    boolean preservePayments=before.hasSettlementHistory() && !legacyFullEdit;
    if(preservePayments) {
      if(e.amount.compareTo(input.amount())!=0 || !e.accountId.equals(a.id) || !Objects.equals(e.categoryId,input.categoryId())
          || input.settled()!=(e.status==Status.LIQUIDADO)
          || (input.settlementDate()!=null && !Objects.equals(input.settlementDate(),before.settlementDate())))
        throw new ApiException(409,"Um lançamento com pagamentos preserva valor, conta, categoria e liquidações. Use as ações de pagamento ou estorno.");
      ObligationRules.date(input.entryDate());ObligationRules.date(input.dueDate());
      e.description=input.description().trim();e.notes=input.notes();e.entryDate=input.entryDate()==null?FinanceRules.today():input.entryDate();e.dueDate=input.dueDate();
      e.updatedAt=Instant.now();entries.flush();var result=entryView(e);audit("LANCAMENTO",id,"EDICAO",before,result);
      touchParent(e,beforeAmount,"EDICAO_PARCELA",before,result);return result;
    }
    reverseSettlement(e,"Estorno por edição");
    applyEntry(e,input,a.id);
    if(input.settled()) settle(e,e.amount,input.settlementDate()==null?FinanceRules.today():input.settlementDate(),UUID.randomUUID(),"EDICAO");
    e.updatedAt=Instant.now(); entries.flush(); var result=entryView(e); audit("LANCAMENTO",id,"EDICAO",before,result);
    touchParent(e,beforeAmount,"EDICAO_PARCELA",before,result);return result;
  }
  public FinanceDtos.EntryView settleEntry(Long id,FinanceDtos.SettlementInput input) {
    lockCompany(); var e=entry(id); String hash=hash(input);
    var previous=settlements.findByCompanyIdAndRequestId(company(),input.requestId()).orElse(null);
    if(previous!=null) {
      if(!previous.entryId.equals(id)) throw new ApiException(409,"Operação já usada em outro lançamento.");
      sameRequest(previous.requestHash,hash); return entryView(e);
    }
    FinanceRules.version(input.version(),e.version);
    if(e.status!=Status.PENDENTE && e.status!=Status.PARCIAL) throw new ApiException(409,"Este lançamento não tem saldo pendente.");
    FinanceRules.effectiveDate(input.settlementDate(),activeAccount(e.accountId).openingDate);
    BigDecimal remaining=e.amount.subtract(paid(e)),amount=input.amount()==null?remaining:input.amount().setScale(2);
    if(amount.signum()<=0 || amount.compareTo(remaining)>0)throw new ApiException(400,"O pagamento deve ser positivo e não pode ultrapassar o saldo pendente.");
    if(e.kind==Kind.RETIRADA && amount.compareTo(remaining)!=0)
      throw new ApiException(400,"Retiradas pessoais devem ser liquidadas integralmente. Pagamentos parciais estão disponíveis para contas a pagar e receber.");
    var before=entryView(e); settle(e,amount,input.settlementDate(),input.requestId(),hash);refreshStatus(e);
    var result=entryView(e); audit("LANCAMENTO",id,"LIQUIDACAO",before,result);
    touchParent(e,e.amount,"LIQUIDACAO",before,result);return result;
  }
  private void settle(FinancialEntry e,BigDecimal amount,LocalDate date,UUID key,String hash) {
    var s=new FinancialSettlement(); s.companyId=company(); s.entryId=e.id; s.accountId=e.accountId;
    s.amount=amount; s.settlementDate=date; s.requestId=key; s.requestHash=hash;
    settlements.saveAndFlush(s);
    var m=new BalanceMovement(); m.companyId=company(); m.accountId=e.accountId; m.settlementId=s.id;
    m.kind=FinanceRules.movementKind(e.kind); m.amount=FinanceRules.signed(e.kind,amount);
    m.effectiveDate=date; m.description=e.description; movements.saveAndFlush(m);
  }
  private void reverseMovement(BalanceMovement original,String reason) {
    activeAccount(original.accountId);
    var m=new BalanceMovement(); m.companyId=company(); m.accountId=original.accountId;
    m.reversalOfId=original.id; m.kind=MovementKind.ESTORNO; m.amount=original.amount.negate();
    m.effectiveDate=original.effectiveDate; m.description=reason+": "+original.description;
    movements.saveAndFlush(m);
  }
  private void reverseSettlement(FinancialEntry e,String reason) {
    for(var s:activeSettlements(e))reverseOneSettlement(s,reason);
  }
  private void reverseOneSettlement(FinancialSettlement s,String reason) {
    var original=movements.findByCompanyIdAndSettlementId(company(),s.id).orElseThrow(ApiException::missing);
    reverseMovement(original,reason); s.reversedAt=Instant.now(); settlements.flush();
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public List<FinanceDtos.SettlementView> settlements(Long id) {
    entry(id);
    return settlements.findByCompanyIdAndEntryIdOrderById(company(),id).stream()
        .map(s->new FinanceDtos.SettlementView(s.id,s.amount,s.settlementDate,s.reversedAt,s.createdAt)).toList();
  }
  public FinanceDtos.EntryView reverseSettlement(Long id,Long settlementId,FinanceDtos.ReverseSettlementInput input) {
    lockCompany();var e=entry(id);var s=settlements.findByIdAndCompanyIdAndEntryId(settlementId,company(),id).orElseThrow(ApiException::missing);
    if(e.status==Status.CANCELADO)throw new ApiException(409,"Lançamento cancelado não admite alterações de pagamentos.");
    if(s.reversedAt!=null)return entryView(e);
    FinanceRules.version(input.version(),e.version);var before=entryView(e);
    reverseOneSettlement(s,"Estorno individual");refreshStatus(e);var result=entryView(e);
    var details=new LinkedHashMap<String,Object>();details.put("reason",input.reason().trim());details.put("settlementId",s.id);details.put("entry",result);
    audit("LANCAMENTO",id,"ESTORNO_LIQUIDACAO",before,details);touchParent(e,e.amount,"ESTORNO_LIQUIDACAO",before,details);return result;
  }
  public FinanceDtos.EntryView cancelEntry(Long id,FinanceDtos.VersionInput input) {
    lockCompany(); var e=entry(id);
    if(e.status==Status.CANCELADO) return entryView(e);
    FinanceRules.version(input.version(),e.version); var before=entryView(e);
    reverseSettlement(e,"Estorno por cancelamento"); e.status=Status.CANCELADO; entries.flush();
    var result=entryView(e); audit("LANCAMENTO",id,"CANCELAMENTO",before,result);
    touchParent(e,e.amount,"CANCELAMENTO_PARCELA",before,result);return result;
  }

  public FinanceDtos.TransferView transfer(FinanceDtos.TransferInput input) {
    lockCompany(); String hash=hash(input);
    var previous=transfers.findByCompanyIdAndRequestId(company(),input.requestId()).orElse(null);
    if(previous!=null) { sameRequest(previous.requestHash,hash); return transferView(previous); }
    var source=activeAccount(input.sourceAccountId()); var target=activeAccount(input.targetAccountId());
    if(source.id.equals(target.id)) throw new ApiException(400,"Selecione contas diferentes.");
    FinanceRules.effectiveDate(input.transferDate(),source.openingDate);
    FinanceRules.effectiveDate(input.transferDate(),target.openingDate);
    var t=new FinancialTransfer(); t.companyId=company(); t.sourceAccountId=source.id; t.targetAccountId=target.id;
    t.amount=input.amount().setScale(2); t.transferDate=input.transferDate();
    t.description=input.description()==null || input.description().isBlank()?"Transferência entre contas":input.description().trim();
    t.requestId=input.requestId(); t.requestHash=hash; transfers.saveAndFlush(t);
    for(boolean incoming:new boolean[]{false,true}) {
      var m=new BalanceMovement(); m.companyId=company(); m.accountId=incoming?target.id:source.id;
      m.transferId=t.id; m.kind=incoming?MovementKind.TRANSFERENCIA_ENTRADA:MovementKind.TRANSFERENCIA_SAIDA;
      m.amount=incoming?t.amount:t.amount.negate(); m.effectiveDate=t.transferDate; m.description=t.description;
      movements.saveAndFlush(m);
    }
    var result=transferView(t); audit("TRANSFERENCIA",t.id,"CRIACAO",null,result); return result;
  }
  public FinanceDtos.TransferView cancelTransfer(Long id,FinanceDtos.VersionInput input) {
    lockCompany(); var t=transfers.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
    if(t.canceledAt!=null) return transferView(t);
    FinanceRules.version(input.version(),t.version); var before=transferView(t);
    for(var m:movements.findByCompanyIdAndTransferIdOrderById(company(),id)) reverseMovement(m,"Estorno de transferência");
    t.canceledAt=Instant.now(); transfers.flush(); var result=transferView(t);
    audit("TRANSFERENCIA",id,"CANCELAMENTO",before,result); return result;
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public FinanceDtos.Statement statement(Long id,LocalDate from,LocalDate to,int page,int size) {
    FinanceRules.page(page,size); var a=account(id);
    LocalDate start=from==null?a.openingDate:from;
    LocalDate end=to==null?FinanceRules.today():to;
    if(start.isBefore(a.openingDate) || start.isAfter(end))
      throw new ApiException(400,"O período deve começar a partir da referência do saldo inicial.");
    BigDecimal before=a.openingBalance.add(jdbc.queryForObject(
        "select coalesce(sum(amount),0) from financial_movements where company_id=? and account_id=? and effective_date<?",
        BigDecimal.class,company(),id,start));
    BigDecimal change=jdbc.queryForObject(
        "select coalesce(sum(amount),0) from financial_movements where company_id=? and account_id=? and effective_date between ? and ?",
        BigDecimal.class,company(),id,start,end);
    Long total=jdbc.queryForObject(
        "select count(*) from financial_movements where company_id=? and account_id=? and effective_date between ? and ?",
        Long.class,company(),id,start,end);
    var rows=jdbc.query("""
        select * from (
          select m.*, ? + sum(m.amount) over(order by m.effective_date,m.id rows unbounded preceding) as running_balance,
          coalesce(s.entry_id,os.entry_id) as linked_entry_id,
          coalesce(m.transfer_id,original.transfer_id) as linked_transfer_id, t.version as transfer_version,
          exists(select 1 from financial_movements r where r.reversal_of_id=m.id and r.company_id=m.company_id) as reversed
          from financial_movements m
          left join financial_settlements s on s.id=m.settlement_id and s.company_id=m.company_id
          left join financial_movements original on original.id=m.reversal_of_id and original.company_id=m.company_id
          left join financial_settlements os on os.id=original.settlement_id and os.company_id=m.company_id
          left join financial_transfers t on t.id=coalesce(m.transfer_id,original.transfer_id) and t.company_id=m.company_id
          where m.company_id=? and m.account_id=?
        ) ledger where effective_date between ? and ?
        order by effective_date desc,id desc limit ? offset ?
        """, (rs,row)->new FinanceDtos.MovementView(rs.getLong("id"),rs.getObject("effective_date",LocalDate.class),
          MovementKind.valueOf(rs.getString("kind")),rs.getString("description"),rs.getBigDecimal("amount"),
          rs.getBigDecimal("running_balance"),rs.getObject("linked_entry_id",Long.class),
          rs.getObject("linked_transfer_id",Long.class),rs.getObject("transfer_version",Long.class),rs.getObject("reversal_of_id",Long.class),
          rs.getBoolean("reversed"),rs.getTimestamp("created_at").toInstant()),
        a.openingBalance,company(),id,start,end,size,Math.multiplyExact(page,size));
    return new FinanceDtos.Statement(accountView(a),before.setScale(2),change.setScale(2),before.add(change).setScale(2),
        new PageResult<>(rows,total,(int)Math.ceil((double)total/size),page,size));
  }
  @Transactional(readOnly=true)
  public List<FinanceDtos.AuditView> history(String resource,Long id) {
    switch(resource) {
      case "CONTA" -> account(id);
      case "LANCAMENTO" -> entry(id);
      case "TRANSFERENCIA" -> transfers.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
      case "OBRIGACAO" -> obligations.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
      case "RECORRENCIA" -> recurrences.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
      default -> throw new ApiException(400,"Recurso de histórico inválido.");
    }
    return audits.findByCompanyIdAndResourceAndResourceIdOrderById(company(),resource,id).stream()
        .map(a->new FinanceDtos.AuditView(a.id,a.actorId,a.action,a.beforeData,a.afterData,a.createdAt)).toList();
  }
}
