package ma.rhflow.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

import lombok.RequiredArgsConstructor;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.EmployeeRequest;
import ma.rhflow.domain.entity.HrDocument;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.EmployeeRequestRepository;
import ma.rhflow.domain.repository.HrDocumentRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.util.XlsxFiles;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayslipRepository payslipRepository;
    private final ContractRepository contractRepository;
    private final HrDocumentRepository hrDocumentRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final ma.rhflow.domain.repository.ExpenseRepository expenseRepository;
    private final ma.rhflow.domain.repository.BusinessTripRepository businessTripRepository;
    private final ma.rhflow.domain.repository.OnboardingTaskRepository onboardingTaskRepository;
    private final ma.rhflow.domain.repository.OffboardingTaskRepository offboardingTaskRepository;
    private final ma.rhflow.domain.repository.PerformanceReviewRepository performanceReviewRepository;
    private final ma.rhflow.domain.repository.ObjectiveRepository objectiveRepository;
    private final ma.rhflow.domain.repository.JobOfferRepository jobOfferRepository;
    private final ma.rhflow.domain.repository.CandidateRepository candidateRepository;
    private final ma.rhflow.domain.repository.PositionRepository positionRepository;
    private final PermissionService permissionService;
    private final ScopeService scopeService;

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public ResponseEntity<byte[]> export(String kind, UserPrincipal actor, Integer year, Integer month) {
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        int m = month == null ? 0 : month;
        java.util.function.Predicate<Payslip> period = p -> p.getYear() != null && p.getYear() == y && (m == 0 || (p.getMonth() != null && p.getMonth() == m));
        return switch (kind) {
            case "damancom" -> {
                permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
                StringBuilder sb = new StringBuilder("matricule_cnss,nom,prenom,cin,mouvement,date_entree,date_sortie,jours_declares,salaire_declare,periode\n");
                for (Payslip p : payslipRepository.findAll()) {
                    if (!period.test(p)) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
                    if (e == null) {
                        continue;
                    }
                    int days = p.getDeclaredDays() == null ? 26 : p.getDeclaredDays();
                    String cnss = p.getSnapshotCnss() != null && !p.getSnapshotCnss().isBlank() ? p.getSnapshotCnss() : e.getCnssNumber();
                    sb.append(csv(cnss)).append(',')
                            .append(csv(e.getLastName())).append(',')
                            .append(csv(e.getFirstName())).append(',')
                            .append(csv(e.getCin())).append(',')
                            .append(mouvement(e, p.getYear(), p.getMonth())).append(',')
                            .append(e.getHireDate() == null ? "" : e.getHireDate()).append(',')
                            .append(e.getExitDate() == null ? "" : e.getExitDate()).append(',')
                            .append(days).append(',')
                            .append(p.getGross()).append(',')
                            .append(p.getMonth()).append('/').append(p.getYear()).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "damancom.csv", "text/csv");
            }
            case "virement" -> {
                permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
                StringBuilder sb = new StringBuilder("nom,prenom,rib,montant,indemnites,reference,libelle,rib_manquant,periode\n");
                for (Payslip p : payslipRepository.findAll()) {
                    if (!period.test(p)) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
                    if (e == null) {
                        continue;
                    }
                    String rib = p.getSnapshotRib() != null && !p.getSnapshotRib().isBlank() ? p.getSnapshotRib() : e.getRib();
                    String reference = p.getTransferReference() != null && !p.getTransferReference().isBlank()
                            ? p.getTransferReference()
                            : "VIR-" + p.getYear() + "-" + p.getMonth() + "-" + p.getEmployeeId();
                    sb.append(csv(e.getLastName())).append(',').append(csv(e.getFirstName())).append(',')
                            .append(csv(rib)).append(',').append(p.getNet()).append(',')
                            .append(p.getIndemnities() == null ? "0" : p.getIndemnities()).append(',')
                            .append(csv(reference)).append(',')
                            .append(csv("Salaire " + p.getMonth() + "/" + p.getYear())).append(',')
                            .append(rib == null || rib.isBlank() ? "OUI" : "NON").append(',')
                            .append(p.getMonth()).append('/').append(p.getYear()).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "ordre-virement.csv", "text/csv");
            }
            case "9421" -> {
                permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    Document doc = new Document();
                    PdfWriter.getInstance(doc, out);
                    doc.open();
                    doc.add(new Paragraph("État 9421 — revenus salariaux " + y));
                    doc.add(new Paragraph("Identification des salariés et retenues IR — DGI"));
                    PdfPTable t = new PdfPTable(7);
                    t.addCell("Nom");
                    t.addCell("CIN");
                    t.addCell("CNSS");
                    t.addCell("Brut annuel");
                    t.addCell("CNSS+AMO");
                    t.addCell("IR");
                    t.addCell("Net");
                    java.util.Map<Long, Payslip> annual = new java.util.LinkedHashMap<>();
                    for (Payslip p : payslipRepository.findByYear(y)) {
                        Payslip acc = annual.computeIfAbsent(p.getEmployeeId(), id -> {
                            Payslip x = new Payslip();
                            x.setEmployeeId(id);
                            x.setGross(java.math.BigDecimal.ZERO);
                            x.setCnss(java.math.BigDecimal.ZERO);
                            x.setAmo(java.math.BigDecimal.ZERO);
                            x.setIr(java.math.BigDecimal.ZERO);
                            x.setNet(java.math.BigDecimal.ZERO);
                            return x;
                        });
                        acc.setGross(acc.getGross().add(p.getGross() == null ? java.math.BigDecimal.ZERO : p.getGross()));
                        acc.setCnss(acc.getCnss().add(p.getCnss() == null ? java.math.BigDecimal.ZERO : p.getCnss()));
                        acc.setAmo(acc.getAmo().add(p.getAmo() == null ? java.math.BigDecimal.ZERO : p.getAmo()));
                        acc.setIr(acc.getIr().add(p.getIr() == null ? java.math.BigDecimal.ZERO : p.getIr()));
                        acc.setNet(acc.getNet().add(p.getNet() == null ? java.math.BigDecimal.ZERO : p.getNet()));
                    }
                    for (Payslip p : annual.values()) {
                        Employee e = employeeRepository.findById(p.getEmployeeId()).orElse(null);
                        t.addCell(e == null ? "" : e.getLastName() + " " + e.getFirstName());
                        t.addCell(e == null ? "" : String.valueOf(e.getCin()));
                        t.addCell(e == null ? "" : String.valueOf(e.getCnssNumber()));
                        t.addCell(String.valueOf(p.getGross()));
                        t.addCell(String.valueOf(p.getCnss().add(p.getAmo())));
                        t.addCell(String.valueOf(p.getIr()));
                        t.addCell(String.valueOf(p.getNet()));
                    }
                    doc.add(t);
                    doc.close();
                    yield file(out.toByteArray(), "etat-9421.pdf", MediaType.APPLICATION_PDF_VALUE);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            case "headcount" -> {
                permissionService.require(actor, PermissionCodes.STATS_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (Employee e : employeeRepository.findAll()) {
                    if (!visible(actor, e.getId())) {
                        continue;
                    }
                    rows.add(new String[] {
                            String.valueOf(e.getId()), nz(e.getLastName()), nz(e.getFirstName()), nz(e.getCin()),
                            nz(e.getEmail()), nz(e.getStatus()), nz(e.getCity())
                    });
                }
                yield xlsx("Effectif", new String[] {"Id", "Nom", "Prénom", "CIN", "E-mail", "Statut", "Ville"}, rows, "effectif.xlsx");
            }
            case "employees.pdf" -> {
                permissionService.require(actor, PermissionCodes.STATS_VIEW);
                yield pdfTable("Effectif", new String[] {"Nom", "Prénom", "CIN", "Statut"}, rows -> {
                    for (Employee e : employeeRepository.findAll()) {
                        if (!visible(actor, e.getId())) {
                            continue;
                        }
                        rows.add(new String[] {
                                nz(e.getLastName()), nz(e.getFirstName()), nz(e.getCin()), nz(e.getStatus())
                        });
                    }
                }, "effectif.pdf");
            }
            case "contracts" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (Contract c : contractRepository.findAll()) {
                    if (!visible(actor, c.getEmployeeId())) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(c.getEmployeeId()).orElse(null);
                    rows.add(new String[] {
                            person(e), nz(c.getType()), String.valueOf(c.getStartDate()), String.valueOf(c.getEndDate()),
                            String.valueOf(c.getBaseSalary()), nz(c.getStatus())
                    });
                }
                yield xlsx("Contrats", new String[] {"Employé", "Type", "Début", "Fin", "Salaire", "Statut"}, rows, "contrats.xlsx");
            }
            case "contracts.pdf" -> {
                permissionService.require(actor, PermissionCodes.CONTRACTS_VIEW);
                yield pdfTable("Contrats", new String[] {"Employé", "Type", "Début", "Fin", "Statut"}, rows -> {
                    for (Contract c : contractRepository.findAll()) {
                        if (!visible(actor, c.getEmployeeId())) {
                            continue;
                        }
                        Employee e = employeeRepository.findById(c.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(e), nz(c.getType()), String.valueOf(c.getStartDate()), String.valueOf(c.getEndDate()), nz(c.getStatus()) });
                    }
                }, "contrats.pdf");
            }
            case "expenses" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.Expense e : expenseRepository.findAll()) {
                    if (!visible(actor, e.getEmployeeId()) || !inPeriod(e.getDate(), y, m)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(e.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(e.getCategory()), String.valueOf(e.getAmount()), String.valueOf(e.getDate()), nz(e.getStatus()) });
                }
                yield xlsx("Notes de frais", new String[] {"Employé", "Catégorie", "Montant", "Date", "Statut"}, rows, "notes-de-frais.xlsx");
            }
            case "expenses.pdf" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                yield pdfTable("Notes de frais", new String[] {"Employé", "Catégorie", "Montant", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.Expense e : expenseRepository.findAll()) {
                        if (!visible(actor, e.getEmployeeId()) || !inPeriod(e.getDate(), y, m)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(e.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(e.getCategory()), String.valueOf(e.getAmount()), nz(e.getStatus()) });
                    }
                }, "notes-de-frais.pdf");
            }
            case "trips" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.BusinessTrip t : businessTripRepository.findAll()) {
                    if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getStartDate(), y, m)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(t.getDestination()), String.valueOf(t.getStartDate()), String.valueOf(t.getEndDate()), nz(t.getStatus()) });
                }
                yield xlsx("Déplacements", new String[] {"Employé", "Destination", "Début", "Fin", "Statut"}, rows, "deplacements.xlsx");
            }
            case "trips.pdf" -> {
                permissionService.require(actor, PermissionCodes.EXPENSES_VIEW);
                yield pdfTable("Déplacements", new String[] {"Employé", "Destination", "Début", "Fin", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.BusinessTrip t : businessTripRepository.findAll()) {
                        if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getStartDate(), y, m)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(t.getDestination()), String.valueOf(t.getStartDate()), String.valueOf(t.getEndDate()), nz(t.getStatus()) });
                    }
                }, "deplacements.pdf");
            }
            case "onboarding" -> {
                requireChecklistExport(actor);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.OnboardingTask t : onboardingTaskRepository.findAll()) {
                    if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getDueDate(), y, m)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(t.getTask()), String.valueOf(t.getDueDate()), nz(t.getStatus()) });
                }
                yield xlsx("Intégration", new String[] {"Employé", "Tâche", "Échéance", "Statut"}, rows, "integration.xlsx");
            }
            case "onboarding.pdf" -> {
                requireChecklistExport(actor);
                yield pdfTable("Intégration", new String[] {"Employé", "Tâche", "Échéance", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.OnboardingTask t : onboardingTaskRepository.findAll()) {
                        if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getDueDate(), y, m)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(t.getTask()), String.valueOf(t.getDueDate()), nz(t.getStatus()) });
                    }
                }, "integration.pdf");
            }
            case "offboarding" -> {
                requireChecklistExport(actor);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.OffboardingTask t : offboardingTaskRepository.findAll()) {
                    if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getDueDate(), y, m)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(t.getTask()), String.valueOf(t.getDueDate()), nz(t.getStatus()) });
                }
                yield xlsx("Départ", new String[] {"Employé", "Tâche", "Échéance", "Statut"}, rows, "depart.xlsx");
            }
            case "offboarding.pdf" -> {
                requireChecklistExport(actor);
                yield pdfTable("Départ", new String[] {"Employé", "Tâche", "Échéance", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.OffboardingTask t : offboardingTaskRepository.findAll()) {
                        if (!visible(actor, t.getEmployeeId()) || !inPeriod(t.getDueDate(), y, m)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(t.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(t.getTask()), String.valueOf(t.getDueDate()), nz(t.getStatus()) });
                    }
                }, "depart.pdf");
            }
            case "reviews" -> {
                requirePerformanceExport(actor);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.PerformanceReview r : performanceReviewRepository.findAll()) {
                    if (!visible(actor, r.getEmployeeId()) || !reviewInYear(r, y)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(r.getPeriod()), String.valueOf(r.getRating()), nz(r.getStatus()) });
                }
                yield xlsx("Entretiens", new String[] {"Employé", "Période", "Note", "Statut"}, rows, "entretiens.xlsx");
            }
            case "reviews.pdf" -> {
                requirePerformanceExport(actor);
                yield pdfTable("Entretiens", new String[] {"Employé", "Période", "Note", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.PerformanceReview r : performanceReviewRepository.findAll()) {
                        if (!visible(actor, r.getEmployeeId()) || !reviewInYear(r, y)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(r.getPeriod()), String.valueOf(r.getRating()), nz(r.getStatus()) });
                    }
                }, "entretiens.pdf");
            }
            case "objectives" -> {
                requirePerformanceExport(actor);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.Objective o : objectiveRepository.findAll()) {
                    if (!visible(actor, o.getEmployeeId()) || !objectiveInPeriod(o, y, m)) {
                        continue;
                    }
                    Employee employee = employeeRepository.findById(o.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(employee), nz(o.getTitle()), String.valueOf(o.getDueDate()), String.valueOf(o.getProgress()) });
                }
                yield xlsx("Objectifs", new String[] {"Employé", "Objectif", "Échéance", "Avancement"}, rows, "objectifs.xlsx");
            }
            case "objectives.pdf" -> {
                requirePerformanceExport(actor);
                yield pdfTable("Objectifs", new String[] {"Employé", "Objectif", "Échéance", "Avancement"}, rows -> {
                    for (ma.rhflow.domain.entity.Objective o : objectiveRepository.findAll()) {
                        if (!visible(actor, o.getEmployeeId()) || !objectiveInPeriod(o, y, m)) {
                            continue;
                        }
                        Employee employee = employeeRepository.findById(o.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(employee), nz(o.getTitle()), String.valueOf(o.getDueDate()), String.valueOf(o.getProgress()) });
                    }
                }, "objectifs.pdf");
            }
            case "jobs" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.JobOffer o : jobOfferRepository.findAll()) {
                    if (!inPeriod(o.getCreatedAt(), y, m)) {
                        continue;
                    }
                    rows.add(new String[] { nz(o.getTitle()), nz(o.getCity()), nz(o.getStatus()) });
                }
                yield xlsx("Offres", new String[] {"Poste", "Ville", "Statut"}, rows, "offres.xlsx");
            }
            case "jobs.pdf" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                yield pdfTable("Offres", new String[] {"Poste", "Ville", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.JobOffer o : jobOfferRepository.findAll()) {
                        if (!inPeriod(o.getCreatedAt(), y, m)) {
                            continue;
                        }
                        rows.add(new String[] { nz(o.getTitle()), nz(o.getCity()), nz(o.getStatus()) });
                    }
                }, "offres.pdf");
            }
            case "candidates" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                List<String[]> rows = new ArrayList<>();
                for (ma.rhflow.domain.entity.Candidate c : candidateRepository.findAll()) {
                    if (!inPeriod(c.getCreatedAt(), y, m)) {
                        continue;
                    }
                    ma.rhflow.domain.entity.JobOffer offer = c.getJobOfferId() == null ? null : jobOfferRepository.findById(c.getJobOfferId()).orElse(null);
                    rows.add(new String[] { nz(c.getLastName()) + " " + nz(c.getFirstName()), nz(c.getEmail()), offer == null ? "" : nz(offer.getTitle()), nz(c.getStatus()) });
                }
                yield xlsx("Candidats", new String[] {"Nom", "E-mail", "Offre", "Statut"}, rows, "candidats.xlsx");
            }
            case "candidates.pdf" -> {
                permissionService.require(actor, PermissionCodes.RECRUITMENT_MANAGE);
                yield pdfTable("Candidats", new String[] {"Nom", "E-mail", "Offre", "Statut"}, rows -> {
                    for (ma.rhflow.domain.entity.Candidate c : candidateRepository.findAll()) {
                        if (!inPeriod(c.getCreatedAt(), y, m)) {
                            continue;
                        }
                        ma.rhflow.domain.entity.JobOffer offer = c.getJobOfferId() == null ? null : jobOfferRepository.findById(c.getJobOfferId()).orElse(null);
                        rows.add(new String[] { nz(c.getLastName()) + " " + nz(c.getFirstName()), nz(c.getEmail()), offer == null ? "" : nz(offer.getTitle()), nz(c.getStatus()) });
                    }
                }, "candidats.pdf");
            }
            case "attendance.csv" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_VIEW);
                StringBuilder sb = new StringBuilder("date,employeeId,status\n");
                for (Attendance a : attendanceRepository.findAll()) {
                    if (!visible(actor, a.getEmployeeId()) || !inPeriod(a.getDate(), y, m)) {
                        continue;
                    }
                    sb.append(a.getDate()).append(',').append(a.getEmployeeId()).append(',').append(a.getStatus()).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "attendance.csv", "text/csv");
            }
            case "attendance", "attendance.xlsx" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (Attendance a : attendanceRepository.findAll()) {
                    if (!visible(actor, a.getEmployeeId()) || !inPeriod(a.getDate(), y, m)) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(a.getEmployeeId()).orElse(null);
                    rows.add(new String[] { String.valueOf(a.getDate()), person(e), nz(a.getStatus()) });
                }
                yield xlsx("Présences", new String[] {"Date", "Employé", "Statut"}, rows, "presences.xlsx");
            }
            case "attendance.pdf" -> {
                permissionService.require(actor, PermissionCodes.ATTENDANCE_VIEW);
                yield pdfTable("Présences", new String[] {"Date", "Employé", "Statut"}, rows -> {
                    for (Attendance a : attendanceRepository.findAll()) {
                        if (!visible(actor, a.getEmployeeId()) || !inPeriod(a.getDate(), y, m)) {
                            continue;
                        }
                        Employee e = employeeRepository.findById(a.getEmployeeId()).orElse(null);
                        rows.add(new String[] { String.valueOf(a.getDate()), person(e), nz(a.getStatus()) });
                    }
                }, "presences.pdf");
            }
            case "leave" -> {
                permissionService.require(actor, PermissionCodes.LEAVE_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (LeaveRequest r : leaveRequestRepository.findAll()) {
                    if (!visible(actor, r.getEmployeeId()) || !inPeriod(r.getStartDate(), y, m)) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                    rows.add(new String[] {
                            person(e), String.valueOf(r.getStartDate()), String.valueOf(r.getEndDate()),
                            String.valueOf(r.getDays()), nz(r.getStatus())
                    });
                }
                yield xlsx("Congés", new String[] {"Employé", "Début", "Fin", "Jours", "Statut"}, rows, "conges.xlsx");
            }
            case "leave.pdf" -> {
                permissionService.require(actor, PermissionCodes.LEAVE_VIEW);
                yield pdfTable("Congés", new String[] {"Employé", "Début", "Fin", "Jours", "Statut"}, rows -> {
                    for (LeaveRequest r : leaveRequestRepository.findAll()) {
                        if (!visible(actor, r.getEmployeeId()) || !inPeriod(r.getStartDate(), y, m)) {
                            continue;
                        }
                        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                        rows.add(new String[] {
                                person(e), String.valueOf(r.getStartDate()), String.valueOf(r.getEndDate()),
                                String.valueOf(r.getDays()), nz(r.getStatus())
                        });
                    }
                }, "conges.pdf");
            }
            case "documents" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_VIEW);
                List<String[]> rows = new ArrayList<>();
                for (HrDocument d : hrDocumentRepository.findAll()) {
                    if (!visible(actor, d.getEmployeeId()) || !inPeriod(d.getCreatedAt(), y, m)) {
                        continue;
                    }
                    Employee e = d.getEmployeeId() == null ? null : employeeRepository.findById(d.getEmployeeId()).orElse(null);
                    rows.add(new String[] { nz(d.getName()), nz(d.getCategory()), person(e), String.valueOf(d.getCreatedAt()) });
                }
                yield xlsx("Documents", new String[] {"Nom", "Catégorie", "Employé", "Date"}, rows, "documents.xlsx");
            }
            case "documents.pdf" -> {
                permissionService.require(actor, PermissionCodes.DOCUMENTS_VIEW);
                yield pdfTable("Documents", new String[] {"Nom", "Catégorie", "Employé"}, rows -> {
                    for (HrDocument d : hrDocumentRepository.findAll()) {
                        if (!visible(actor, d.getEmployeeId()) || !inPeriod(d.getCreatedAt(), y, m)) {
                            continue;
                        }
                        Employee e = d.getEmployeeId() == null ? null : employeeRepository.findById(d.getEmployeeId()).orElse(null);
                        rows.add(new String[] { nz(d.getName()), nz(d.getCategory()), person(e) });
                    }
                }, "documents.pdf");
            }
            case "requests" -> {
                permissionService.requireAny(actor, PermissionCodes.REQUESTS_CREATE, PermissionCodes.REQUESTS_PROCESS);
                List<String[]> rows = new ArrayList<>();
                for (EmployeeRequest r : employeeRequestRepository.findAll()) {
                    if (!visible(actor, r.getEmployeeId()) || !inPeriod(r.getCreatedAt(), y, m)) {
                        continue;
                    }
                    Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                    rows.add(new String[] { person(e), nz(r.getType()), nz(r.getStatus()), String.valueOf(r.getCreatedAt()) });
                }
                yield xlsx("Demandes", new String[] {"Employé", "Type", "Statut", "Date"}, rows, "demandes.xlsx");
            }
            case "requests.pdf" -> {
                permissionService.requireAny(actor, PermissionCodes.REQUESTS_CREATE, PermissionCodes.REQUESTS_PROCESS);
                yield pdfTable("Demandes RH", new String[] {"Employé", "Type", "Statut"}, rows -> {
                    for (EmployeeRequest r : employeeRequestRepository.findAll()) {
                        if (!visible(actor, r.getEmployeeId()) || !inPeriod(r.getCreatedAt(), y, m)) {
                            continue;
                        }
                        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
                        rows.add(new String[] { person(e), nz(r.getType()), nz(r.getStatus()) });
                    }
                }, "demandes.pdf");
            }
            case "payroll" -> {
                permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
                StringBuilder sb = new StringBuilder("employeeId,period,base,brut,cnss,amo,ir,net,employerCost\n");
                for (Payslip p : payslipRepository.findAll()) {
                    if (!period.test(p)) {
                        continue;
                    }
                    sb.append(p.getEmployeeId()).append(',').append(p.getMonth()).append('/').append(p.getYear()).append(',')
                            .append(p.getBaseSalary()).append(',').append(p.getGross()).append(',')
                            .append(p.getCnss()).append(',')
                            .append(p.getAmo()).append(',').append(p.getIr()).append(',')
                            .append(p.getNet()).append(',').append(p.getEmployerCost()).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "livre-paie.csv", "text/csv");
            }
            case "journal" -> {
                permissionService.require(actor, PermissionCodes.PAYROLL_VIEW);
                StringBuilder sb = new StringBuilder("date,compte,libelle,debit,credit\n");
                java.math.BigDecimal salaires = java.math.BigDecimal.ZERO;
                java.math.BigDecimal charges = java.math.BigDecimal.ZERO;
                java.math.BigDecimal cnss = java.math.BigDecimal.ZERO;
                java.math.BigDecimal amo = java.math.BigDecimal.ZERO;
                java.math.BigDecimal ir = java.math.BigDecimal.ZERO;
                java.math.BigDecimal cimr = java.math.BigDecimal.ZERO;
                java.math.BigDecimal mutuelle = java.math.BigDecimal.ZERO;
                java.math.BigDecimal tfp = java.math.BigDecimal.ZERO;
                java.math.BigDecimal net = java.math.BigDecimal.ZERO;
                java.math.BigDecimal retenues = java.math.BigDecimal.ZERO;
                String date = "";
                for (Payslip p : payslipRepository.findAll()) {
                    if (!period.test(p)) {
                        continue;
                    }
                    int last = java.time.YearMonth.of(p.getYear(), p.getMonth()).lengthOfMonth();
                    date = p.getYear() + "-" + String.format("%02d", p.getMonth()) + "-" + String.format("%02d", last);
                    salaires = salaires.add(nz(p.getGross()));
                    java.math.BigDecimal patronal = nz(p.getEmployerAf()).add(nz(p.getEmployerCnss())).add(nz(p.getEmployerAmo()))
                            .add(nz(p.getEmployerTfp())).add(nz(p.getCimrEmployer())).add(nz(p.getMutualEmployer()));
                    charges = charges.add(patronal);
                    cnss = cnss.add(nz(p.getCnss())).add(nz(p.getEmployerCnss())).add(nz(p.getEmployerAf()));
                    amo = amo.add(nz(p.getAmo())).add(nz(p.getEmployerAmo()));
                    ir = ir.add(nz(p.getIr()));
                    cimr = cimr.add(nz(p.getCimrEmployee())).add(nz(p.getCimrEmployer()));
                    mutuelle = mutuelle.add(nz(p.getMutualEmployee())).add(nz(p.getMutualEmployer()));
                    tfp = tfp.add(nz(p.getEmployerTfp()));
                    net = net.add(nz(p.getNet()));
                    retenues = retenues.add(nz(p.getAdvances())).add(nz(p.getGarnishments())).add(nz(p.getCreditNote()));
                }
                if (!date.isBlank()) {
                    sb.append(date).append(",61711,Salaires bruts,").append(salaires).append(",0\n");
                    sb.append(date).append(",61741,Charges patronales,").append(charges).append(",0\n");
                    sb.append(date).append(",4441,CNSS à payer,0,").append(cnss).append('\n');
                    sb.append(date).append(",4443,AMO à payer,0,").append(amo).append('\n');
                    sb.append(date).append(",44525,IR à payer,0,").append(ir).append('\n');
                    sb.append(date).append(",4434,CIMR à payer,0,").append(cimr).append('\n');
                    sb.append(date).append(",4436,Mutuelle à payer,0,").append(mutuelle).append('\n');
                    sb.append(date).append(",4458,Taxe de formation professionnelle,0,").append(tfp).append('\n');
                    sb.append(date).append(",4432,Net à payer au personnel,0,").append(net).append('\n');
                    sb.append(date).append(",3431,Acomptes saisies et avoirs,0,").append(retenues).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "journal-paie.csv", "text/csv");
            }
            case "registre" -> {
                permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
                StringBuilder sb = new StringBuilder("nom,prenom,nationalite,date_naissance,cin,emploi,date_entree,date_sortie,adresse,cnss\n");
                for (Employee e : employeeRepository.findAll()) {
                    String job = "";
                    if (e.getPositionId() != null) {
                        job = positionRepository.findById(e.getPositionId()).map(p -> p.getTitle() == null ? "" : p.getTitle()).orElse("");
                    }
                    sb.append(csv(e.getLastName())).append(',')
                            .append(csv(e.getFirstName())).append(',')
                            .append(csv(e.getNationality())).append(',')
                            .append(e.getBirthDate() == null ? "" : e.getBirthDate()).append(',')
                            .append(csv(e.getCin())).append(',')
                            .append(csv(job)).append(',')
                            .append(e.getHireDate() == null ? "" : e.getHireDate()).append(',')
                            .append(e.getExitDate() == null ? "" : e.getExitDate()).append(',')
                            .append(csv(e.getAddress())).append(',')
                            .append(csv(e.getCnssNumber())).append('\n');
                }
                yield file(sb.toString().getBytes(StandardCharsets.UTF_8), "registre-personnel.csv", "text/csv");
            }
            default -> throw BusinessException.notFound("Rapport inconnu");
        };
    }

    private ResponseEntity<byte[]> xlsx(String sheet, String[] headers, List<String[]> rows, String name) {
        return file(XlsxFiles.table(sheet, headers, rows), name, XLSX);
    }

    private void requirePerformanceExport(UserPrincipal actor) {
        if (actor.getRole() == ma.rhflow.domain.enums.UserRole.EMPLOYEE) {
            return;
        }
        permissionService.require(actor, PermissionCodes.PERFORMANCE_MANAGE);
    }

    private boolean reviewInYear(ma.rhflow.domain.entity.PerformanceReview review, int year) {
        if (review.getPeriod() != null && review.getPeriod().contains(String.valueOf(year))) {
            return true;
        }
        return review.getCreatedAt() != null && review.getCreatedAt().atZone(CASABLANCA).getYear() == year;
    }

    private boolean objectiveInPeriod(ma.rhflow.domain.entity.Objective objective, int year, int month) {
        if (objective.getDueDate() != null) {
            return inPeriod(objective.getDueDate(), year, month);
        }
        return month == 0 && inPeriod(objective.getCreatedAt(), year, 0);
    }

    private void requireChecklistExport(UserPrincipal actor) {
        if (actor.getRole() == ma.rhflow.domain.enums.UserRole.EMPLOYEE) {
            return;
        }
        permissionService.require(actor, PermissionCodes.EMPLOYEES_VIEW);
    }

    private boolean visible(UserPrincipal actor, Long employeeId) {
        Set<Long> ids = scopeService.visibleEmployeeIds(actor);
        return ids == null || (employeeId != null && ids.contains(employeeId));
    }

    private boolean inPeriod(LocalDate date, int year, int month) {
        if (date == null) {
            return false;
        }
        if (date.getYear() != year) {
            return false;
        }
        return month == 0 || date.getMonthValue() == month;
    }

    private boolean inPeriod(Instant instant, int year, int month) {
        if (instant == null) {
            return false;
        }
        return inPeriod(instant.atZone(CASABLANCA).toLocalDate(), year, month);
    }

    private String person(Employee employee) {
        if (employee == null) {
            return "";
        }
        return nz(employee.getLastName()) + " " + nz(employee.getFirstName());
    }

    private String csv(String s) {
        if (s == null) {
            return "";
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private String mouvement(Employee employee, Integer year, Integer month) {
        boolean entree = employee.getHireDate() != null && year != null && month != null
                && employee.getHireDate().getYear() == year && employee.getHireDate().getMonthValue() == month;
        boolean sortie = employee.getExitDate() != null && year != null && month != null
                && employee.getExitDate().getYear() == year && employee.getExitDate().getMonthValue() == month;
        if (entree && sortie) {
            return "ENTREE_SORTIE";
        }
        if (entree) {
            return "ENTREE";
        }
        if (sortie) {
            return "SORTIE";
        }
        return "NORMAL";
    }

    private ResponseEntity<byte[]> excel(String csv, String name) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return file(out, name, "application/vnd.ms-excel");
    }

    private ResponseEntity<byte[]> pdfTable(String title, String[] headers, java.util.function.Consumer<java.util.List<String[]>> fill, String filename) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(title));
            PdfPTable t = new PdfPTable(headers.length);
            for (String h : headers) {
                t.addCell(h);
            }
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            fill.accept(rows);
            for (String[] row : rows) {
                for (String cell : row) {
                    t.addCell(cell);
                }
            }
            doc.add(t);
            doc.close();
            return file(out.toByteArray(), filename, MediaType.APPLICATION_PDF_VALUE);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<byte[]> file(byte[] bytes, String name, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
