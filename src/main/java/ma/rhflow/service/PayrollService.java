package ma.rhflow.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Expense;
import ma.rhflow.domain.entity.PayrollCredit;
import ma.rhflow.domain.entity.PayrollItem;
import ma.rhflow.domain.repository.ExpenseRepository;
import ma.rhflow.domain.repository.PayrollItemRepository;
import ma.rhflow.domain.entity.PayrollPeriod;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.PayrollCreditRepository;
import ma.rhflow.domain.repository.PayrollPeriodRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.tenant.TenantContext;
import ma.rhflow.util.JsonMaps;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollPeriodRepository periodRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final PayrollCreditRepository creditRepository;
    private final TenantRepository tenantRepository;
    private final PayrollEngine engine;
    private final PayrollAbsenceLinker absenceLinker;
    private final PayrollItemRepository itemRepository;
    private final ExpenseRepository expenseRepository;
    private final PayrollRuleService ruleService;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final CatalogService catalogService;
    private final MailService mailService;
    private final AttendanceHoursService attendanceHoursService;

    public Page<Map<String, Object>> listPeriods(int page, int size, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        return periodRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "year", "month")))
                .map(this::toPeriod);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPeriod(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        PayrollPeriod p = periodRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        Map<String, Object> m = toPeriod(p);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Payslip s : payslipRepository.findByPeriodId(id)) {
            lines.add(catalogService.toPayslip(s));
        }
        m.put("lines", lines);
        m.put("payslips", lines);
        m.put("credits", creditRepository.findByFromPeriodId(id).stream().map(this::toCredit).toList());
        return m;
    }

    @Transactional
    public Map<String, Object> createPeriod(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        Integer year = JsonMaps.integer(body, "year");
        Integer month = JsonMaps.integer(body, "month");
        if (year == null || month == null) {
            throw BusinessException.badRequest("Année et mois requis");
        }
        periodRepository.findByYearAndMonth(year, month).ifPresent(p -> {
            throw BusinessException.conflict("Période déjà ouverte");
        });
        PayrollRules rules = ruleService.rulesFor(LocalDate.of(year, month, 1));
        PayrollPeriod p = new PayrollPeriod();
        p.setTenantId(TenantContext.requireTenantId());
        p.setYear(year);
        p.setMonth(month);
        p.setStatus("DRAFT");
        p.setRuleSetId(rules.id());
        periodRepository.save(p);
        auditService.log(actor, "CREATE", "payroll-period", p.getId(), year + "-" + month);
        return toPeriod(p);
    }

    @Transactional
    public Map<String, Object> calculate(Long id, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        if ("LOCKED".equals(period.getStatus())) {
            throw BusinessException.badRequest("Période verrouillée");
        }
        for (PayrollCredit c : creditRepository.findByAppliedPeriodId(id)) {
            c.setAppliedPeriodId(null);
            creditRepository.save(c);
        }
        payslipRepository.deleteByPeriodId(id);
        LocalDate ref = LocalDate.of(period.getYear(), period.getMonth(), 1);
        PayrollRules rules = period.getRuleSetId() != null
                ? ruleService.rulesById(period.getRuleSetId())
                : ruleService.rulesFor(ref);
        period.setRuleSetId(rules.id());
        Tenant tenant = tenantRepository.findById(period.getTenantId()).orElse(null);
        PayrollEngine.CompanyBenefits benefits = tenant == null
                ? PayrollEngine.CompanyBenefits.none()
                : new PayrollEngine.CompanyBenefits(
                        n(tenant.getCimrEmployeeRate()),
                        n(tenant.getCimrEmployerRate()),
                        n(tenant.getMutualEmployeeRate()),
                        n(tenant.getMutualEmployerRate()));
        List<PayrollItem> monthItems = itemRepository.findByYearAndMonth(period.getYear(), period.getMonth());
        List<Expense> expenses = expenseRepository.findAll();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Employee e : employeeRepository.findAll()) {
            if (!"ACTIVE".equals(e.getStatus()) && !"ON_LEAVE".equals(e.getStatus())) {
                continue;
            }
            Contract contract = activeContract(e.getId(), ref);
            if (contract == null) {
                continue;
            }
            BigDecimal credit = unappliedCredits(e.getId()).stream()
                    .map(c -> n(c.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal monthly = n(contract.getBaseSalary()).add(n(contract.getBaseSalary())
                    .multiply(engine.seniorityRate(e.getHireDate(), ref)));
            Linked linked = linked(e, monthly, period.getYear(), period.getMonth(), monthItems, expenses);
            AttendanceHoursService.Overtime ot = attendanceHoursService.overtime(e.getId(), period.getYear(), period.getMonth());
            BigDecimal loan = attendanceHoursService.loanInstallment(e.getId());
            PayrollEngine.Inputs baseInputs = inputsOf(body, credit);
            int declared = Math.max(0, baseInputs.declaredDays() - linked.unpaidDays());
            Payslip slip = engine.calculate(
                    e,
                    contract,
                    period.getYear(),
                    period.getMonth(),
                    rules,
                    withLinked(baseInputs, linked, declared, ot, loan),
                    benefits);
            slip.setLoanDeduction(loan);
            slip.setUnpaidDays(linked.unpaidDays());
            slip.setPaidLeaveDays(linked.paidLeaveDays());
            slip.setIndemnities(linked.indemnities().add(n(baseInputs.nontaxableBonuses())));
            slip.setBonuses(n(slip.getBonuses()).subtract(slip.getIndemnities()).max(BigDecimal.ZERO));
            slip.setLinkNote(linked.note());
            slip.setSnapshotCnss(e.getCnssNumber());
            slip.setSnapshotRib(e.getRib());
            slip.setTenantId(period.getTenantId());
            slip.setPeriodId(period.getId());
            payslipRepository.save(slip);
            for (PayrollCredit c : unappliedCredits(e.getId())) {
                c.setAppliedPeriodId(period.getId());
                creditRepository.save(c);
            }
            totalCost = totalCost.add(slip.getEmployerCost());
        }
        period.setEmployerCost(totalCost);
        period.setStatus("CALCULATED");
        periodRepository.save(period);
        auditService.log(actor, "CALCULATE", "payroll-period", id, rules.code());
        return getPeriod(id, actor);
    }

    @Transactional
    public Map<String, Object> approve(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        if (!"CALCULATED".equals(period.getStatus()) && !"DRAFT".equals(period.getStatus())) {
            throw BusinessException.badRequest("La période doit être calculée avant approbation");
        }
        period.setStatus("APPROVED");
        periodRepository.save(period);
        auditService.log(actor, "APPROVE", "payroll-period", id, null);
        return toPeriod(period);
    }

    @Transactional
    public Map<String, Object> markPaid(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        if (!"APPROVED".equals(period.getStatus()) && !"CALCULATED".equals(period.getStatus())) {
            throw BusinessException.badRequest("Approuvez la période avant de la marquer payée");
        }
        period.setStatus("PAID");
        periodRepository.save(period);
        for (Payslip slip : payslipRepository.findByPeriodId(id)) {
            attendanceHoursService.applyLoanPayments(slip.getEmployeeId(), n(slip.getLoanDeduction()).signum() == 0
                    ? attendanceHoursService.loanInstallment(slip.getEmployeeId())
                    : n(slip.getLoanDeduction()));
        }
        auditService.log(actor, "PAID", "payroll-period", id, null);
        return toPeriod(period);
    }

    @Transactional
    public Map<String, Object> lock(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        if ("LOCKED".equals(period.getStatus())) {
            throw BusinessException.badRequest("Déjà verrouillée");
        }
        period.setStatus("LOCKED");
        periodRepository.save(period);
        int sent = 0;
        for (Payslip slip : payslipRepository.findByPeriodId(id)) {
            Employee employee = employeeRepository.findById(slip.getEmployeeId()).orElse(null);
            if (employee == null || employee.getEmail() == null || employee.getEmail().isBlank()) {
                continue;
            }
            byte[] pdf = pdf(slip.getId(), actor);
            boolean delivered = mailService.sendPdf(
                    employee.getEmail(),
                    "Bulletin de paie " + slip.getMonth() + "/" + slip.getYear(),
                    "Votre bulletin de paie est en pièce jointe. Il est aussi disponible dans votre espace.",
                    "bulletin-" + slip.getYear() + "-" + slip.getMonth() + ".pdf",
                    pdf);
            if (delivered) {
                slip.setEmailedAt(Instant.now());
                payslipRepository.save(slip);
                sent++;
            }
        }
        auditService.log(actor, "LOCK", "payroll-period", id, "bulletins " + sent);
        Map<String, Object> result = toPeriod(period);
        result.put("bulletinsSent", sent);
        result.put("mailEnabled", mailService.enabled());
        return result;
    }

    @Transactional
    public Map<String, Object> creditNote(Long id, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        if (!"LOCKED".equals(period.getStatus())) {
            throw BusinessException.badRequest("L’avoir s’applique uniquement à un mois verrouillé");
        }
        Long employeeId = JsonMaps.lng(body, "employeeId");
        BigDecimal amount = n(JsonMaps.dec(body, "amount"));
        if (employeeId == null || amount.signum() <= 0) {
            throw BusinessException.badRequest("Employé et montant de l’avoir requis");
        }
        PayrollCredit credit = new PayrollCredit();
        credit.setTenantId(period.getTenantId());
        credit.setFromPeriodId(period.getId());
        credit.setEmployeeId(employeeId);
        credit.setAmount(amount);
        credit.setReason(JsonMaps.str(body, "reason"));
        creditRepository.save(credit);
        auditService.log(actor, "CREDIT", "payroll-period", id, "avoir " + amount);
        return toCredit(credit);
    }

    public Map<String, Object> elements(Long periodId, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> BusinessException.notFound("Période introuvable"));
        LocalDate ref = LocalDate.of(period.getYear(), period.getMonth(), 1);
        List<PayrollItem> monthItems = itemRepository.findByYearAndMonth(period.getYear(), period.getMonth());
        List<Expense> expenses = expenseRepository.findAll();
        List<Map<String, Object>> people = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
            if (!"ACTIVE".equals(employee.getStatus()) && !"ON_LEAVE".equals(employee.getStatus())) {
                continue;
            }
            Contract contract = activeContract(employee.getId(), ref);
            if (contract == null) {
                continue;
            }
            BigDecimal monthly = n(contract.getBaseSalary()).add(n(contract.getBaseSalary())
                    .multiply(engine.seniorityRate(employee.getHireDate(), ref)));
            Linked linked = linked(employee, monthly, period.getYear(), period.getMonth(), monthItems, expenses);
            if (linked.note() == null || linked.note().isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employee.getId());
            row.put("employee", JsonMaps.employeeRef(employee));
            row.put("unpaidDays", linked.unpaidDays());
            row.put("paidLeaveDays", linked.paidLeaveDays());
            row.put("unpaidAmount", linked.unpaidAmount());
            row.put("prime", linked.primeCnss().add(linked.primeIr()));
            row.put("indemnities", linked.indemnities());
            row.put("note", linked.note());
            people.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", monthItems.stream().map(this::toItem).toList());
        out.put("people", people);
        return out;
    }

    @Transactional
    public Map<String, Object> addItem(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        Long employeeId = JsonMaps.lng(body, "employeeId");
        Integer year = JsonMaps.integer(body, "year");
        Integer month = JsonMaps.integer(body, "month");
        String kind = JsonMaps.str(body, "kind");
        String label = JsonMaps.str(body, "label");
        BigDecimal amount = n(JsonMaps.dec(body, "amount"));
        if (employeeId == null || year == null || month == null || kind == null || label == null || label.isBlank()) {
            throw BusinessException.badRequest("Salarié, période, type et libellé requis");
        }
        kind = kind.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PRIME_CNSS", "PRIME_IR", "INDEMNITE", "ABSENCE").contains(kind)) {
            throw BusinessException.badRequest("Type d’élément inconnu");
        }
        if (amount.signum() <= 0) {
            throw BusinessException.badRequest("Montant requis");
        }
        employeeRepository.findById(employeeId).orElseThrow(() -> BusinessException.notFound("Employé introuvable"));
        PayrollItem item = new PayrollItem();
        item.setTenantId(TenantContext.requireTenantId());
        item.setEmployeeId(employeeId);
        item.setYear(year);
        item.setMonth(month);
        item.setKind(kind);
        item.setLabel(label.trim());
        item.setAmount(amount);
        Integer days = JsonMaps.integer(body, "days");
        item.setDays(days == null ? 0 : days);
        itemRepository.save(item);
        auditService.log(actor, "CREATE", "payroll-item", item.getId(), kind);
        return toItem(item);
    }

    @Transactional
    public Map<String, Object> deleteItem(Long id, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        PayrollItem item = itemRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Élément introuvable"));
        itemRepository.delete(item);
        auditService.log(actor, "DELETE", "payroll-item", id, item.getKind());
        return Map.of("id", id);
    }

    private Map<String, Object> toItem(PayrollItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("employeeId", item.getEmployeeId());
        row.put("employee", JsonMaps.employeeRef(employeeRepository.findById(item.getEmployeeId()).orElse(null)));
        row.put("year", item.getYear());
        row.put("month", item.getMonth());
        row.put("kind", item.getKind());
        row.put("label", item.getLabel());
        row.put("amount", item.getAmount());
        row.put("days", item.getDays());
        return row;
    }

    public byte[] pdf(Long payslipId, UserPrincipal actor) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> BusinessException.notFound("Bulletin introuvable"));
        boolean own = actor.getEmployeeId() != null && actor.getEmployeeId().equals(p.getEmployeeId());
        if (!permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)
                && !(own && permissionService.has(actor, PermissionCodes.PAYSLIPS_VIEW_OWN))) {
            throw BusinessException.forbidden("Accès bulletin refusé");
        }
        Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            doc.add(new Paragraph("Bulletin de paie — RHFlow Maroc", h));
            doc.add(new Paragraph((e == null ? "" : e.getFirstName() + " " + e.getLastName())
                    + " — " + p.getMonth() + "/" + p.getYear()));
            if (e != null || p.getSnapshotCnss() != null || p.getSnapshotRib() != null) {
                String cnss = p.getSnapshotCnss() != null ? p.getSnapshotCnss() : (e == null ? null : e.getCnssNumber());
                String rib = p.getSnapshotRib() != null ? p.getSnapshotRib() : (e == null ? null : e.getRib());
                doc.add(new Paragraph("CNSS : " + (cnss == null || cnss.isBlank() ? "—" : cnss)
                        + " · RIB : " + (rib == null || rib.isBlank() ? "—" : rib)));
            }
            doc.add(new Paragraph("Jours déclarés : " + (p.getDeclaredDays() == null ? 26 : p.getDeclaredDays())
                    + " · Congés payés repris : " + (p.getPaidLeaveDays() == null ? 0 : p.getPaidLeaveDays())
                    + " · Absences non payées : " + (p.getUnpaidDays() == null ? 0 : p.getUnpaidDays())));
            if (p.getLinkNote() != null && !p.getLinkNote().isBlank()) {
                doc.add(new Paragraph("Éléments liés : " + p.getLinkNote()));
            }
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            add(t, "Salaire de base + ancienneté", p.getBaseSalary());
            add(t, "Heures supplémentaires", p.getOvertimeAmount());
            add(t, "Primes", p.getBonuses());
            add(t, "Indemnités", p.getIndemnities());
            add(t, "Absences non payées", p.getUnpaidAbsence());
            add(t, "Avances", p.getAdvances());
            add(t, "Saisies", p.getGarnishments());
            add(t, "Avoir", p.getCreditNote());
            add(t, "CIMR salarié", p.getCimrEmployee());
            add(t, "Mutuelle salarié", p.getMutualEmployee());
            add(t, "Brut", p.getGross());
            add(t, "CNSS salarié", p.getCnss());
            add(t, "AMO salarié", p.getAmo());
            add(t, "IR", p.getIr());
            add(t, "Net à payer", p.getNet());
            add(t, "Part patronale allocations familiales", p.getEmployerAf());
            add(t, "Part patronale CNSS", p.getEmployerCnss());
            add(t, "Part patronale AMO", p.getEmployerAmo());
            add(t, "Taxe de formation professionnelle", p.getEmployerTfp());
            add(t, "Coût employeur", p.getEmployerCost());
            doc.add(t);
            if (p.isSmigAlert()) {
                doc.add(new Paragraph("Alerte SMIG : brut horaire sous le SMIG en vigueur."));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public Map<String, Object> recordTransfer(Long payslipId, Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.PAYROLL_EDIT);
        Payslip slip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> BusinessException.notFound("Bulletin introuvable"));
        String status = JsonMaps.str(body, "status");
        if (status == null || status.isBlank()) {
            status = "PAID";
        }
        slip.setTransferStatus(status.toUpperCase(java.util.Locale.ROOT));
        if ("PENDING".equalsIgnoreCase(status)) {
            slip.setTransferDate(null);
            slip.setTransferReference(null);
        } else {
            LocalDate paidOn = JsonMaps.date(body, "paidOn");
            slip.setTransferDate(paidOn == null ? LocalDate.now() : paidOn);
            String reference = JsonMaps.str(body, "reference");
            slip.setTransferReference(reference == null || reference.isBlank() ? "VIR-" + slip.getYear() + "-" + slip.getMonth() + "-" + slip.getEmployeeId() : reference);
        }
        payslipRepository.save(slip);
        auditService.log(actor, "TRANSFER", "payslip", slip.getId(), slip.getTransferReference());
        return catalogService.toPayslip(slip);
    }

    public byte[] virementPdf(Long payslipId, UserPrincipal actor) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> BusinessException.notFound("Bulletin introuvable"));
        if (!permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)) {
            throw BusinessException.forbidden("Accès virement refusé");
        }
        Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
        Tenant company = tenantRepository.findById(TenantContext.requireTenantId()).orElse(null);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            doc.add(new Paragraph("Ordre de virement de salaire", h));
            if (company != null) {
                doc.add(new Paragraph(company.getName() == null ? "" : company.getName()));
            }
            doc.add(new Paragraph("Période : " + p.getMonth() + "/" + p.getYear()));
            doc.add(new Paragraph("Bénéficiaire : " + (e == null ? "—" : e.getFirstName() + " " + e.getLastName())));
            String rib = p.getSnapshotRib() != null && !p.getSnapshotRib().isBlank()
                    ? p.getSnapshotRib()
                    : (e == null ? null : e.getRib());
            doc.add(new Paragraph("RIB : " + (rib == null || rib.isBlank() ? "—" : rib)));
            doc.add(new Paragraph("Net à virer : " + (p.getNet() == null ? "0" : p.getNet().toPlainString()) + " MAD"));
            if (p.getIndemnities() != null && p.getIndemnities().signum() != 0) {
                doc.add(new Paragraph("Dont indemnités : " + p.getIndemnities().toPlainString() + " MAD"));
            }
            if (p.getLinkNote() != null && !p.getLinkNote().isBlank()) {
                doc.add(new Paragraph("Composé de : " + p.getLinkNote()));
            }
            doc.add(new Paragraph("Référence : " + (p.getTransferReference() == null ? "—" : p.getTransferReference())));
            doc.add(new Paragraph("Statut : " + (p.getTransferStatus() == null ? "PENDING" : p.getTransferStatus())
                    + (p.getTransferDate() == null ? "" : " · " + p.getTransferDate())));
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void add(PdfPTable t, String label, BigDecimal v) {
        t.addCell(label);
        t.addCell(v == null ? "0" : v.toPlainString());
    }

    private Contract activeContract(Long employeeId, LocalDate ref) {
        return contractRepository.findByEmployeeIdAndStatus(employeeId, "ACTIVE").stream()
                .filter(c -> c.getStartDate() == null || !c.getStartDate().isAfter(ref.withDayOfMonth(ref.lengthOfMonth())))
                .filter(c -> c.getEndDate() == null || !c.getEndDate().isBefore(ref))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> toPeriod(PayrollPeriod p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("year", p.getYear());
        m.put("month", p.getMonth());
        m.put("status", p.getStatus());
        m.put("employerCost", p.getEmployerCost());
        m.put("ruleSetId", p.getRuleSetId());
        return m;
    }

    private Map<String, Object> toCredit(PayrollCredit c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("employeeId", c.getEmployeeId());
        m.put("amount", c.getAmount());
        m.put("reason", c.getReason());
        m.put("fromPeriodId", c.getFromPeriodId());
        m.put("appliedPeriodId", c.getAppliedPeriodId());
        return m;
    }

    private List<PayrollCredit> unappliedCredits(Long employeeId) {
        return creditRepository.findByEmployeeIdAndAppliedPeriodIdIsNull(employeeId);
    }

    private PayrollEngine.Inputs withLinked(
            PayrollEngine.Inputs in,
            Linked linked,
            int declaredDays,
            AttendanceHoursService.Overtime ot,
            BigDecimal loan) {
        AttendanceHoursService.Overtime hours = ot == null ? AttendanceHoursService.Overtime.none() : ot;
        return new PayrollEngine.Inputs(
                n(in.taxableCnssBonuses()).add(linked.primeCnss()),
                n(in.taxableNoCnssBonuses()).add(linked.primeIr()),
                n(in.nontaxableBonuses()).add(linked.indemnities()),
                n(in.overtimeHoursDay()).add(hours.day()),
                n(in.overtimeHoursNight()).add(hours.night()),
                n(in.overtimeHoursRest()).add(hours.rest()),
                in.overtimeHoursNoComp(),
                n(in.unpaidAbsence()).add(linked.unpaidAmount()),
                in.advances(),
                n(in.garnishments()).add(n(loan)),
                in.creditNote(),
                declaredDays);
    }

    private Linked linked(Employee employee, BigDecimal monthly, int year, int month, List<PayrollItem> items, List<Expense> expenses) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        BigDecimal unpaid = BigDecimal.ZERO;
        BigDecimal primeCnss = BigDecimal.ZERO;
        BigDecimal primeIr = BigDecimal.ZERO;
        BigDecimal indemnity = BigDecimal.ZERO;
        int unpaidDays = 0;
        int paidDays = 0;
        StringBuilder note = new StringBuilder();
        for (PayrollAbsenceLinker.Link link : absenceLinker.links(employee.getId(), year, month, monthly)) {
            appendNote(note, link.label(), link.days(), link.amount());
            if ("LEAVE_PAID".equals(link.kind())) {
                paidDays += link.days();
            } else {
                unpaidDays += link.days();
                unpaid = unpaid.add(n(link.amount()));
            }
        }
        for (PayrollItem item : items) {
            if (!employee.getId().equals(item.getEmployeeId())) {
                continue;
            }
            BigDecimal amount = n(item.getAmount());
            appendNote(note, item.getLabel(), item.getDays() == null ? 0 : item.getDays(), amount);
            switch (item.getKind() == null ? "" : item.getKind()) {
                case "PRIME_CNSS" -> primeCnss = primeCnss.add(amount);
                case "PRIME_IR" -> primeIr = primeIr.add(amount);
                case "ABSENCE" -> {
                    unpaid = unpaid.add(amount);
                    unpaidDays += item.getDays() == null ? 0 : item.getDays();
                }
                default -> indemnity = indemnity.add(amount);
            }
        }
        for (Expense expense : expenses) {
            if (!employee.getId().equals(expense.getEmployeeId()) || expense.getDate() == null) {
                continue;
            }
            if (expense.getDate().isBefore(start) || expense.getDate().isAfter(end)) {
                continue;
            }
            String status = expense.getStatus() == null ? "" : expense.getStatus();
            if (!"APPROVED".equals(status) && !"PAID".equals(status)) {
                continue;
            }
            BigDecimal amount = n(expense.getAmount());
            indemnity = indemnity.add(amount);
            String label = expense.getCategory() == null || expense.getCategory().isBlank() ? "Note de frais" : expense.getCategory();
            appendNote(note, label, 0, amount);
        }
        return new Linked(unpaidDays, paidDays, unpaid, primeCnss, primeIr, indemnity, note.toString());
    }

    private void appendNote(StringBuilder note, String label, int days, BigDecimal amount) {
        if (note.length() > 0) {
            note.append(" · ");
        }
        note.append(label == null || label.isBlank() ? "Élément" : label);
        if (days > 0) {
            note.append(' ').append(days).append(" j");
        }
        if (amount != null && amount.signum() != 0) {
            note.append(' ').append(amount.toPlainString()).append(" MAD");
        }
        if (note.length() > 1900) {
            note.setLength(1900);
        }
    }

    private record Linked(
            int unpaidDays,
            int paidLeaveDays,
            BigDecimal unpaidAmount,
            BigDecimal primeCnss,
            BigDecimal primeIr,
            BigDecimal indemnities,
            String note
    ) {}

    private PayrollEngine.Inputs inputsOf(Map<String, Object> body, BigDecimal credit) {
        if (body == null) {
            return new PayrollEngine.Inputs(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    n(credit), 26);
        }
        Integer days = JsonMaps.integer(body, "declaredDays");
        return new PayrollEngine.Inputs(
                n(JsonMaps.dec(body, "taxableCnssBonuses", "taxableBonuses", "bonuses")),
                n(JsonMaps.dec(body, "taxableNoCnssBonuses")),
                n(JsonMaps.dec(body, "nontaxableBonuses")),
                n(JsonMaps.dec(body, "overtimeHoursDay")),
                n(JsonMaps.dec(body, "overtimeHoursNight")),
                n(JsonMaps.dec(body, "overtimeHoursRest")),
                n(JsonMaps.dec(body, "overtimeHoursNoComp")),
                n(JsonMaps.dec(body, "unpaidAbsence")),
                n(JsonMaps.dec(body, "advances")),
                n(JsonMaps.dec(body, "garnishments")),
                n(credit),
                days == null ? 26 : days
        );
    }

    private BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
