package com.negociofacil.finance.obligations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociofacil.common.*;
import com.negociofacil.company.CompanyRepository;
import com.negociofacil.finance.*;
import com.negociofacil.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.negociofacil.finance.FinanceTypes.*;

@Service @Transactional
public class ObligationService {
  private final CompanyRepository companies;
  private final FinancialObligationRepository obligations;
  private final FinancialRecurrenceRepository recurrences;
  private final FinancialEntryRepository entries;
  private final FinancialSettlementRepository settlements;
  private final FinancialAccountRepository accounts;
  private final FinancialCategoryRepository categories;
  private final FinancialAuditRepository audits;
  private final FinanceService finance;
  private final ObjectMapper json;
  public ObligationService(CompanyRepository companies,FinancialObligationRepository obligations,FinancialRecurrenceRepository recurrences,
      FinancialEntryRepository entries,FinancialSettlementRepository settlements,FinancialAccountRepository accounts,
      FinancialCategoryRepository categories,FinancialAuditRepository audits,FinanceService finance,ObjectMapper json) {
    this.companies=companies;this.obligations=obligations;this.recurrences=recurrences;this.entries=entries;this.settlements=settlements;
    this.accounts=accounts;this.categories=categories;this.audits=audits;this.finance=finance;this.json=json;
  }
  private Long company(){return CurrentUser.companyId();}
  private void lock(){companies.lockById(company()).orElseThrow(ApiException::missing);}
  private FinancialObligation obligation(Long id){return obligations.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);}
  private FinancialRecurrence recurrence(Long id){return recurrences.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);}
  private FinancialEntry installment(Long id) {
    var e=entries.findByIdAndCompanyId(id,company()).orElseThrow(ApiException::missing);
    if(e.obligationId==null || e.kind==Kind.RETIRADA)throw ApiException.missing();return e;
  }
  private FinancialAccount references(Kind kind,Long accountId,Long categoryId,boolean requireActive) {
    if(kind==null || kind==Kind.RETIRADA)throw new ApiException(400,"Escolha uma conta a pagar ou a receber.");
    var a=accountId==null?accounts.findByCompanyIdAndPrimaryAccountTrue(company()).orElseThrow(ApiException::missing)
        :accounts.findByIdAndCompanyId(accountId,company()).orElseThrow(ApiException::missing);
    if(requireActive && !a.active)throw new ApiException(409,"Reative a conta antes de utilizá-la.");
    if(categoryId==null)throw new ApiException(400,"Selecione uma categoria.");
    var category=categories.findByIdAndCompanyId(categoryId,company()).orElseThrow(ApiException::missing);
    if(category.kind!=kind)throw new ApiException(400,"Categoria incompatível com a obrigação.");return a;
  }
  private String encode(Object value) {
    try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException("Falha no histórico financeiro.",ex);}
  }
  private String hash(Object value) {
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}
    catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
  }
  private void same(String expected,String actual){if(!expected.equals(actual))throw new ApiException(409,"Identificador de operação já utilizado com outros dados.");}
  private UUID key(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
  private void audit(String resource,Long id,String action,Object before,Object after) {
    var a=new FinancialAudit();a.companyId=company();a.actorId=CurrentUser.get().userId();a.resource=resource;a.resourceId=id;
    a.action=action;a.beforeData=before==null?null:encode(before);a.afterData=encode(after);audits.saveAndFlush(a);
  }
  private ObligationDtos.InstallmentView view(FinancialEntry e) {
    var v=finance.getEntry(e.id);
    return new ObligationDtos.InstallmentView(v.id(),v.obligationId(),v.kind(),v.description(),v.amount(),v.paidAmount(),v.remainingAmount(),
        v.dueDate(),v.entryDate(),v.accountId(),v.accountName(),v.categoryId(),v.categoryName(),v.paymentStatus(),v.overdue(),v.status(),v.version(),
        v.installmentNumber(),v.installmentCount(),v.recurrenceId(),v.occurrenceNumber(),v.notes(),v.hasSettlementHistory());
  }
  private ObligationDtos.Detail detail(FinancialObligation p) {
    return new ObligationDtos.Detail(p.id,p.kind,p.description,p.totalAmount,p.installmentCount,p.recurrenceId,p.occurrenceNumber,p.version,
        entries.findByCompanyIdAndObligationIdOrderByInstallmentNumber(company(),p.id).stream().map(this::view).toList());
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public ObligationDtos.Detail get(Long id){return detail(obligation(id));}
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public ObligationDtos.InstallmentView getInstallment(Long id){return view(installment(id));}
  private FinancialObligation parent(Kind kind,String description,BigDecimal amount,int count,String source,Long recurrenceId,Integer occurrenceNumber,UUID requestId,String requestHash) {
    var p=new FinancialObligation();p.companyId=company();p.kind=kind;p.description=description.trim();p.totalAmount=amount.setScale(2);
    p.installmentCount=count;p.source=source;p.recurrenceId=recurrenceId;p.occurrenceNumber=occurrenceNumber;p.requestId=requestId;p.requestHash=requestHash;
    return obligations.saveAndFlush(p);
  }
  private FinancialEntry add(FinancialObligation p,int number,BigDecimal amount,Long accountId,Long categoryId,LocalDate entryDate,LocalDate dueDate,String notes) {
    var e=new FinancialEntry();e.companyId=company();e.obligationId=p.id;e.installmentNumber=number;e.kind=p.kind;e.status=Status.PENDENTE;
    e.description=p.description;e.amount=amount;e.accountId=accountId;e.categoryId=categoryId;e.entryDate=entryDate;e.dueDate=dueDate;e.notes=notes;
    e.requestId=key("obligation:"+p.id+":installment:"+number);e.requestHash=p.requestHash;entries.saveAndFlush(e);
    audit("LANCAMENTO",e.id,"CRIACAO",null,view(e));return e;
  }
  public ObligationDtos.Detail create(ObligationDtos.CreateInput input) {
    lock();String hash=hash(input);var previous=obligations.findByCompanyIdAndRequestId(company(),input.requestId()).orElse(null);
    if(previous!=null){same(previous.requestHash,hash);return detail(previous);}
    var a=references(input.kind(),input.accountId(),input.categoryId(),true);
    ObligationRules.date(input.dueDate());ObligationRules.date(input.entryDate());
    var amounts=ObligationRules.split(input.amount(),input.installments());
    if(Boolean.TRUE.equals(input.settled()) && input.installments()!=1)throw new ApiException(400,"Cadastre parcelas pendentes e registre cada pagamento na parcela correspondente.");
    if(!Boolean.TRUE.equals(input.settled()) && input.settlementDate()!=null)throw new ApiException(400,"Uma obrigação pendente não possui liquidação.");
    LocalDate date=input.entryDate()==null?FinanceRules.today():input.entryDate();
    var p=parent(input.kind(),input.description(),input.amount(),input.installments(),"OBLIGATION",null,null,input.requestId(),hash);
    FinancialEntry single=null;
    for(int i=0;i<amounts.size();i++)single=add(p,i+1,amounts.get(i),a.id,input.categoryId(),date,ObligationRules.monthly(input.dueDate(),i),input.notes());
    audit("OBRIGACAO",p.id,"CRIACAO",null,detail(p));
    if(Boolean.TRUE.equals(input.settled()))finance.settleEntry(single.id,new FinanceDtos.SettlementInput(UUID.randomUUID(),single.version,
        input.settlementDate()==null?FinanceRules.today():input.settlementDate(),null));
    return detail(p);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public PageResult<ObligationDtos.InstallmentView> list(Kind kind,PaymentStatus status,Long accountId,Long categoryId,String search,
      LocalDate from,LocalDate to,int page,int size) {
    if(kind==null || kind==Kind.RETIRADA)throw new ApiException(400,"Informe RECEITA ou DESPESA.");
    FinanceRules.page(page,size);
    if(search==null || search.length()>160 || (from!=null && to!=null && from.isAfter(to)))throw new ApiException(400,"Filtros inválidos.");
    if(accountId!=null)accounts.findByIdAndCompanyId(accountId,company()).orElseThrow(ApiException::missing);
    if(categoryId!=null){var c=categories.findByIdAndCompanyId(categoryId,company()).orElseThrow(ApiException::missing);if(c.kind!=kind)throw new ApiException(400,"Categoria incompatível com o filtro.");}
    var result=entries.findAll((root,query,cb)->{
      var predicates=new ArrayList<Predicate>();predicates.add(cb.equal(root.get("companyId"),company()));
      predicates.add(cb.equal(root.get("kind"),kind));predicates.add(cb.isNotNull(root.get("obligationId")));
      if(accountId!=null)predicates.add(cb.equal(root.get("accountId"),accountId));
      if(categoryId!=null)predicates.add(cb.equal(root.get("categoryId"),categoryId));
      if(from!=null)predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"),from));if(to!=null)predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"),to));
      if(status!=null)switch(status) {
        case VENCIDO -> {predicates.add(root.get("status").in(Status.PENDENTE,Status.PARCIAL));predicates.add(cb.lessThan(root.get("dueDate"),FinanceRules.today()));}
        case QUITADO -> predicates.add(cb.equal(root.get("status"),Status.LIQUIDADO));
        case PARCIAL -> predicates.add(cb.equal(root.get("status"),Status.PARCIAL));
        case CANCELADO -> predicates.add(cb.equal(root.get("status"),Status.CANCELADO));
        case PENDENTE -> predicates.add(cb.equal(root.get("status"),Status.PENDENTE));
      }
      if(!search.isBlank()) {
        String escaped=search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        predicates.add(cb.like(cb.lower(root.get("description")),"%"+escaped+"%",'\\'));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    },PageRequest.of(page,size,Sort.by(Sort.Order.asc("dueDate"),Sort.Order.asc("id"))));
    return new PageResult<>(result.stream().map(this::view).toList(),result.getTotalElements(),result.getTotalPages(),page,size);
  }
  public ObligationDtos.InstallmentView edit(Long id,ObligationDtos.EditInput input) {
    lock();var e=installment(id);FinanceRules.version(input.version(),e.version);
    if(e.status==Status.CANCELADO)throw new ApiException(409,"Parcela cancelada não pode ser editada.");
    var a=references(e.kind,input.accountId(),input.categoryId(),false);
    boolean hasHistory=!settlements.findByCompanyIdAndEntryIdOrderById(company(),id).isEmpty();
    boolean financialChange=e.amount.compareTo(input.amount())!=0 || !e.accountId.equals(a.id) || !e.categoryId.equals(input.categoryId());
    if(hasHistory && financialChange)throw new ApiException(409,"Uma parcela com histórico de pagamentos preserva valor, conta e categoria. Edite os demais dados ou cancele e cadastre outra obrigação.");
    if(!a.active && financialChange)throw new ApiException(409,"Reative a conta antes de utilizá-la.");
    ObligationRules.date(input.entryDate());ObligationRules.date(input.dueDate());
    var before=view(e);BigDecimal previous=e.amount;var p=obligation(e.obligationId);
    BigDecimal total=p.totalAmount.subtract(previous).add(input.amount());
    if(total.precision()-total.scale()>14)throw new ApiException(400,"O total da obrigação ultrapassa o limite monetário.");
    e.description=input.description().trim();e.amount=input.amount().setScale(2);e.accountId=a.id;e.categoryId=input.categoryId();
    e.entryDate=input.entryDate();e.dueDate=input.dueDate();e.notes=input.notes();e.updatedAt=Instant.now();entries.flush();
    p.totalAmount=total.setScale(2);if(p.installmentCount==1)p.description=e.description;p.updatedAt=Instant.now();obligations.flush();
    if(p.recurrenceId!=null){var r=recurrence(p.recurrenceId);r.updatedAt=Instant.now();recurrences.flush();}
    var result=view(e);audit("LANCAMENTO",id,"EDICAO_PARCELA",before,result);audit("OBRIGACAO",p.id,"EDICAO_PARCELA",before,result);return result;
  }
  private ObligationDtos.RecurrenceView recurrenceView(FinancialRecurrence r) {
    return new ObligationDtos.RecurrenceView(r.id,r.description,r.amount,r.categoryId,r.accountId,r.firstDueDate,r.occurrences,r.active,r.version);
  }
  @Transactional(readOnly=true) public ObligationDtos.RecurrenceView getRecurrence(Long id){return recurrenceView(recurrence(id));}
  public ObligationDtos.RecurrenceView createRecurrence(ObligationDtos.RecurrenceInput input) {
    lock();String hash=hash(input);var previous=recurrences.findByCompanyIdAndRequestId(company(),input.requestId()).orElse(null);
    if(previous!=null){same(previous.requestHash,hash);return recurrenceView(previous);}
    var a=references(Kind.DESPESA,input.accountId(),input.categoryId(),true);ObligationRules.date(input.firstDueDate());
    ObligationRules.date(ObligationRules.monthly(input.firstDueDate(),input.occurrences()-1));
    var r=new FinancialRecurrence();r.companyId=company();r.description=input.description().trim();r.amount=input.amount().setScale(2);
    r.categoryId=input.categoryId();r.accountId=a.id;r.firstDueDate=input.firstDueDate();r.occurrences=input.occurrences();r.notes=input.notes();
    r.requestId=input.requestId();r.requestHash=hash;recurrences.saveAndFlush(r);
    for(int i=0;i<input.occurrences();i++) {
      var due=ObligationRules.monthly(input.firstDueDate(),i);
      var p=parent(Kind.DESPESA,r.description,r.amount,1,"RECURRENCE",r.id,i+1,key("recurrence:"+r.id+":occurrence:"+(i+1)),hash);
      add(p,1,r.amount,a.id,r.categoryId,due,due,r.notes);audit("OBRIGACAO",p.id,"CRIACAO",null,detail(p));
    }
    var result=recurrenceView(r);audit("RECORRENCIA",r.id,"CRIACAO",null,result);return result;
  }
  private List<FinancialObligation> futureParents(FinancialRecurrence r,Long fromObligationId) {
    var from=obligation(fromObligationId);
    if(!Objects.equals(from.recurrenceId,r.id))throw ApiException.missing();
    return obligations.findByCompanyIdAndRecurrenceIdOrderByOccurrenceNumber(company(),r.id).stream()
        .filter(p->p.occurrenceNumber>=from.occurrenceNumber).toList();
  }
  private boolean futureUnpaid(FinancialEntry e) {
    return e.status==Status.PENDENTE && e.dueDate!=null && !e.dueDate.isBefore(FinanceRules.today())
        && settlements.findByCompanyIdAndEntryIdOrderById(company(),e.id).isEmpty();
  }
  public ObligationDtos.FutureResult updateFuture(Long id,ObligationDtos.FutureInput input) {
    lock();var r=recurrence(id);FinanceRules.version(input.version(),r.version);
    var a=references(Kind.DESPESA,input.accountId(),input.categoryId(),true);var before=recurrenceView(r);
    int updated=0,skipped=0;
    for(var p:futureParents(r,input.fromObligationId())) {
      var e=entries.findByCompanyIdAndObligationIdOrderByInstallmentNumber(company(),p.id).getFirst();
      if(!futureUnpaid(e)){skipped++;continue;}
      // Keep occurrence identity/order and its original month even if a single
      // occurrence's date was manually moved; changing day does not reorder it.
      YearMonth month=YearMonth.from(ObligationRules.monthly(r.firstDueDate,p.occurrenceNumber-1));
      LocalDate due=month.atDay(Math.min(input.dueDay(),month.lengthOfMonth()));
      if(due.isBefore(FinanceRules.today())){skipped++;continue;}
      edit(e.id,new ObligationDtos.EditInput(e.version,input.description(),input.amount(),a.id,input.categoryId(),e.entryDate,due,input.notes()));updated++;
    }
    if(updated>0){r.description=input.description().trim();r.amount=input.amount().setScale(2);r.accountId=a.id;r.categoryId=input.categoryId();r.notes=input.notes();r.updatedAt=Instant.now();recurrences.flush();}
    var result=new ObligationDtos.FutureResult(updated,skipped);var after=new LinkedHashMap<String,Object>();after.put("recurrence",recurrenceView(r));after.put("result",result);after.put("fromObligationId",input.fromObligationId());
    audit("RECORRENCIA",r.id,"EDICAO_FUTURAS",before,after);return result;
  }
  public ObligationDtos.FutureResult cancelFuture(Long id,ObligationDtos.CancelFutureInput input) {
    lock();var r=recurrence(id);FinanceRules.version(input.version(),r.version);var before=recurrenceView(r);int updated=0,skipped=0;
    for(var p:futureParents(r,input.fromObligationId())) {
      var e=entries.findByCompanyIdAndObligationIdOrderByInstallmentNumber(company(),p.id).getFirst();
      if(!futureUnpaid(e)){skipped++;continue;}
      finance.cancelEntry(e.id,new FinanceDtos.VersionInput(e.version));updated++;
    }
    r.active=false;r.updatedAt=Instant.now();recurrences.flush();var result=new ObligationDtos.FutureResult(updated,skipped);
    var after=new LinkedHashMap<String,Object>();after.put("recurrence",recurrenceView(r));after.put("result",result);after.put("fromObligationId",input.fromObligationId());
    audit("RECORRENCIA",r.id,"CANCELAMENTO_FUTURAS",before,after);return result;
  }
}
