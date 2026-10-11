package ma.rhflow.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rhflow.domain.entity.Announcement;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.Candidate;
import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Department;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.EmployeeRequest;
import ma.rhflow.domain.entity.Expense;
import ma.rhflow.domain.entity.Holiday;
import ma.rhflow.domain.entity.HrDocument;
import ma.rhflow.domain.entity.JobOffer;
import ma.rhflow.domain.entity.LeaveBalance;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.entity.Notification;
import ma.rhflow.domain.entity.OffboardingTask;
import ma.rhflow.domain.entity.OnboardingTask;
import ma.rhflow.domain.entity.PayrollPeriod;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.Position;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.entity.User;
import ma.rhflow.domain.entity.WorkingHours;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.AnnouncementRepository;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.CandidateRepository;
import ma.rhflow.domain.repository.ContractRepository;
import ma.rhflow.domain.repository.DepartmentRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.EmployeeRequestRepository;
import ma.rhflow.domain.repository.ExpenseRepository;
import ma.rhflow.domain.repository.HolidayRepository;
import ma.rhflow.domain.repository.HrDocumentRepository;
import ma.rhflow.domain.repository.JobOfferRepository;
import ma.rhflow.domain.repository.LeaveBalanceRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.LeaveTypeRepository;
import ma.rhflow.domain.repository.NotificationRepository;
import ma.rhflow.domain.repository.OffboardingTaskRepository;
import ma.rhflow.domain.repository.OnboardingTaskRepository;
import ma.rhflow.domain.repository.PayrollPeriodRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.PositionRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.domain.repository.UserRepository;
import ma.rhflow.domain.repository.WorkingHoursRepository;
import ma.rhflow.service.PayrollEngine;
import ma.rhflow.service.PayrollRuleService;
import ma.rhflow.service.PermissionService;
import ma.rhflow.service.SubscriptionService;
import ma.rhflow.tenant.TenantContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceRepository attendanceRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final HrDocumentRepository hrDocumentRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final ExpenseRepository expenseRepository;
    private final JobOfferRepository jobOfferRepository;
    private final CandidateRepository candidateRepository;
    private final OnboardingTaskRepository onboardingTaskRepository;
    private final OffboardingTaskRepository offboardingTaskRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollEngine payrollEngine;
    private final SubscriptionService subscriptionService;
    private final PayrollRuleService payrollRuleService;

    @Override
    @Transactional
    public void run(String... args) {
        TenantContext.setBypass(true);
        try {
            payrollRuleService.ensureSeeded();
            subscriptionService.ensureDefaults();
            if (userRepository.count() > 0) {
                tenantRepository.findAll().forEach(subscriptionService::ensureFor);
                permissionService.grantMissingDefaults();
                return;
            }
            seed();
            log.info("Demo data seeded: Technova Maroc + 4 users");
        } finally {
            TenantContext.clear();
        }
    }

    private void seed() {
        Tenant tenant = new Tenant();
        tenant.setSlug("technova");
        tenant.setName("Technova Maroc");
        tenant.setLegalName("Technova Maroc SARL");
        tenant.setIce("001234567000089");
        tenant.setTaxId("IF-45210");
        tenant.setRc("RC-Casa-12345");
        tenant.setCnssAffiliation("1234567");
        tenant.setEmail("contact@technova.ma");
        tenant.setPhone("+212522000000");
        tenant.setCity("Casablanca");
        tenant.setAddress("Twin Center, Bd Zerktouni");
        tenant.setWebsite("https://technova.ma");
        tenant.setSector("Services informatiques");
        tenant.setCurrency("MAD");
        tenant.setPlan(SubscriptionPlan.PRO);
        tenant.setFeatureOllama(true);
        tenant.setWhatsappEnabled(true);
        tenant.setActive(true);
        tenant.setTrialEndsAt(LocalDate.now().plusYears(1));
        tenantRepository.save(tenant);
        subscriptionService.ensureFor(tenant);
        Long tid = tenant.getId();

        User superAdmin = user("superadmin@rhflow.ma", "Super@123456", "Super", "Admin", UserRole.SUPER_ADMIN, null, "fr");
        User admin = user("admin@rhflow.ma", "Admin@123456", "Amina", "Kadiri", UserRole.ADMIN, tid, "fr");
        userRepository.save(superAdmin);
        userRepository.save(admin);

        String[] deptNames = {"IT", "HR", "Finance", "Commercial", "Marketing", "Operations"};
        List<Department> depts = new ArrayList<>();
        for (String n : deptNames) {
            Department d = new Department();
            d.setTenantId(tid);
            d.setName(n);
            d.setDescription("Département " + n);
            depts.add(departmentRepository.save(d));
        }

        String[] titles = {"Ingénieur", "Responsable RH", "Comptable", "Commercial", "Chargé marketing", "Chef d'équipe"};
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            Position p = new Position();
            p.setTenantId(tid);
            p.setTitle(titles[i]);
            p.setDepartmentId(depts.get(i).getId());
            p.setMinSalary(new BigDecimal("6000"));
            p.setMaxSalary(new BigDecimal("25000"));
            positions.add(positionRepository.save(p));
        }

        LeaveType annual = leaveType(tid, "Congé annuel", "ANNUAL", true, true);
        leaveType(tid, "Maladie", "SICK", true, false);
        leaveType(tid, "Sans solde", "UNPAID", false, false);
        leaveType(tid, "Mariage", "MARRIAGE", true, false);
        leaveType(tid, "Maternité", "MATERNITY", true, false);

        holiday(tid, LocalDate.of(2026, 1, 1), "Nouvel an", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 1, 11), "Manifeste de l'Indépendance", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 5, 1), "Fête du Travail", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 7, 30), "Fête du Trône", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 8, 14), "Allégeance Oued Ed-Dahab", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 8, 20), "Révolution du Roi et du Peuple", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 8, 21), "Fête de la Jeunesse", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 11, 6), "Marche Verte", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 11, 18), "Fête de l'Indépendance", "NATIONAL");
        holiday(tid, LocalDate.of(2026, 3, 20), "Aïd al-Fitr", "RELIGIOUS");
        holiday(tid, LocalDate.of(2026, 5, 27), "Aïd al-Adha", "RELIGIOUS");
        holiday(tid, LocalDate.of(2026, 6, 17), "1er Moharram", "RELIGIOUS");
        holiday(tid, LocalDate.of(2026, 8, 26), "Aïd al-Mawlid", "RELIGIOUS");

        WorkingHours hours = new WorkingHours();
        hours.setTenantId(tid);
        hours.setName("Horaires société");
        hours.setStartTime(LocalTime.of(8, 30));
        hours.setEndTime(LocalTime.of(17, 30));
        hours.setBreakStart(LocalTime.of(12, 30));
        hours.setBreakEnd(LocalTime.of(14, 0));
        hours.setWeeklyHours(new BigDecimal("44"));
        hours.setWorkDays("MON-FRI");
        workingHoursRepository.save(hours);

        String[][] people = {
                {"Youssef", "Benali"}, {"Sara", "El Amrani"}, {"Mehdi", "Tazi"}, {"Fatima", "Bennani"},
                {"Omar", "Cherkaoui"}, {"Nadia", "Lahlou"}, {"Karim", "Fassi"}, {"Imane", "Alaoui"},
                {"Hicham", "Mansouri"}, {"Salma", "Idrissi"}, {"Anas", "Berrada"}, {"Ghita", "Kettani"},
                {"Reda", "Squalli"}, {"Zineb", "Chraibi"}, {"Amine", "Senoussi"}, {"Maryam", "Tahiri"},
                {"Hamza", "Ouazzani"}, {"Leila", "Benjelloun"}, {"Tarik", "Kabbaj"}, {"Soukaina", "Mernissi"},
                {"Adil", "Naciri"}, {"Houda", "Sefrioui"}, {"Yassine", "Bensouda"}, {"Kenza", "Lazrak"},
                {"Nabil", "El Idrissi"}, {"Sanaa", "Benkirane"}, {"Rachid", "Touzani"}, {"Aicha", "Lahlou"},
                {"Bilal", "Amrani"}, {"Nour", "Chafik"}, {"Ismail", "Bennis"}, {"Yasmine", "El Fassi"},
                {"Othmane", "Kettani"}, {"Rim", "Berrada"}, {"Mehdi", "Senoussi"}, {"Lina", "Tahiri"},
                {"Walid", "Mansouri"}, {"Salma", "Alaoui"}, {"Anas", "Cherkaoui"}, {"Ghita", "Fassi"},
                {"Reda", "Benjelloun"}, {"Zineb", "Ouazzani"}, {"Sofia", "El Yazidi"}, {"Adam", "Kadiri"},
                {"Ines", "Tazi"}, {"Rayan", "Bennani"}, {"Maya", "Cherkaoui"}, {"Nizar", "Alaoui"},
                {"Hana", "Idrissi"}, {"Younes", "Fassi"}
        };
        List<Employee> employees = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < people.length; i++) {
            Employee e = new Employee();
            e.setTenantId(tid);
            e.setFirstName(people[i][0]);
            e.setLastName(people[i][1]);
            e.setEmail(people[i][0].toLowerCase() + "." + people[i][1].toLowerCase().replace(" ", "") + (i + 1) + "@technova.ma");
            e.setCin("BE" + (100000 + i));
            e.setPhone("06" + String.format("%08d", 10000000 + i));
            e.setCity(i % 2 == 0 ? "Casablanca" : "Rabat");
            e.setHireDate(today.minusYears(1 + (i % 8)).minusMonths(i % 11));
            e.setCnssNumber(String.format("%09d", 200000000 + i));
            e.setRib(String.format("%024d", 1000000000000000000L + i).substring(0, 24));
            e.setDepartmentId(depts.get(i % depts.size()).getId());
            e.setPositionId(positions.get(i % positions.size()).getId());
            e.setStatus("ACTIVE");
            e.setDependents(i % 4);
            employees.add(employeeRepository.save(e));

            Contract c = new Contract();
            c.setTenantId(tid);
            c.setEmployeeId(e.getId());
            c.setType(i % 7 == 0 ? "CDD" : "CDI");
            c.setStartDate(e.getHireDate());
            if ("CDD".equals(c.getType())) {
                c.setEndDate(today.plusDays(20 + i));
            }
            c.setBaseSalary(new BigDecimal(8000 + i * 400L));
            c.setWeeklyHours(new BigDecimal("44"));
            c.setDepartmentId(e.getDepartmentId());
            c.setPositionId(e.getPositionId());
            c.setStatus("ACTIVE");
            contractRepository.save(c);

            LeaveBalance bal = new LeaveBalance();
            bal.setTenantId(tid);
            bal.setEmployeeId(e.getId());
            bal.setLeaveTypeId(annual.getId());
            bal.setYear(today.getYear());
            bal.setAccrued(new BigDecimal("18"));
            bal.setUsed(new BigDecimal(i % 5));
            bal.setRemaining(bal.getAccrued().subtract(bal.getUsed()));
            leaveBalanceRepository.save(bal);
        }

        Employee managerEmp = employees.get(0);
        Employee staffEmp = employees.get(1);
        User manager = user("manager@rhflow.ma", "Manager@123456", managerEmp.getFirstName(), managerEmp.getLastName(),
                UserRole.MANAGER, tid, "fr");
        manager.setEmployeeId(managerEmp.getId());
        User employeeUser = user("employee@rhflow.ma", "Employee@123456", staffEmp.getFirstName(), staffEmp.getLastName(),
                UserRole.EMPLOYEE, tid, "fr");
        employeeUser.setEmployeeId(staffEmp.getId());
        userRepository.save(manager);
        userRepository.save(employeeUser);
        permissionService.seedDefaults(manager);
        permissionService.seedDefaults(employeeUser);
        managerEmp.setManagerId(null);
        staffEmp.setManagerId(managerEmp.getId());
        employeeRepository.save(managerEmp);
        employeeRepository.save(staffEmp);

        LocalDate monthStart = today.withDayOfMonth(1);
        for (Employee e : employees) {
            for (int d = 0; d < Math.min(12, today.getDayOfMonth()); d++) {
                LocalDate day = monthStart.plusDays(d);
                if (day.getDayOfWeek().getValue() >= 6) {
                    continue;
                }
                Attendance a = new Attendance();
                a.setTenantId(tid);
                a.setEmployeeId(e.getId());
                a.setDate(day);
                int roll = ThreadLocalRandom.current().nextInt(10);
                if (roll == 0) {
                    a.setStatus("ABSENT");
                } else if (roll == 1) {
                    a.setStatus("LATE");
                    a.setCheckIn(LocalTime.of(9, 20));
                    a.setCheckOut(LocalTime.of(17, 30));
                } else {
                    a.setStatus("PRESENT");
                    a.setCheckIn(LocalTime.of(8, 30));
                    a.setCheckOut(LocalTime.of(17, 30));
                }
                attendanceRepository.save(a);
            }
        }

        LeaveRequest pending = new LeaveRequest();
        pending.setTenantId(tid);
        pending.setEmployeeId(staffEmp.getId());
        pending.setLeaveTypeId(annual.getId());
        pending.setStartDate(today.plusDays(10));
        pending.setEndDate(today.plusDays(12));
        pending.setDays(new BigDecimal("3"));
        pending.setStatus("PENDING");
        pending.setComment("Voyage familial");
        leaveRequestRepository.save(pending);

        LeaveRequest approved = new LeaveRequest();
        approved.setTenantId(tid);
        approved.setEmployeeId(employees.get(2).getId());
        approved.setLeaveTypeId(annual.getId());
        approved.setStartDate(today.minusDays(2));
        approved.setEndDate(today.plusDays(1));
        approved.setDays(new BigDecimal("3"));
        approved.setStatus("APPROVED");
        leaveRequestRepository.save(approved);

        HrDocument doc = new HrDocument();
        doc.setTenantId(tid);
        doc.setName("Contrat CDI — " + managerEmp.getLastName());
        doc.setEmployeeId(managerEmp.getId());
        doc.setCategory("CONTRACT");
        doc.setVisibleToEmployee(true);
        hrDocumentRepository.save(doc);

        EmployeeRequest req = new EmployeeRequest();
        req.setTenantId(tid);
        req.setEmployeeId(staffEmp.getId());
        req.setType("WORK_CERTIFICATE");
        req.setStatus("PENDING");
        req.setNotes("Pour banque");
        employeeRequestRepository.save(req);

        Announcement ann = new Announcement();
        ann.setTenantId(tid);
        ann.setTitle("Réunion mensuelle RH");
        ann.setBody("Rendez-vous vendredi 10h, salle Atlas.");
        ann.setTarget("COMPANY");
        announcementRepository.save(ann);

        Notification n = new Notification();
        n.setTenantId(tid);
        n.setUserId(employeeUser.getId());
        n.setTitle("Demande de congé");
        n.setMessage("Votre demande est en attente.");
        notificationRepository.save(n);

        Expense exp = new Expense();
        exp.setTenantId(tid);
        exp.setEmployeeId(managerEmp.getId());
        exp.setCategory("Transport");
        exp.setAmount(new BigDecimal("320"));
        exp.setDate(today.minusDays(3));
        exp.setStatus("PENDING");
        expenseRepository.save(exp);

        JobOffer offer = new JobOffer();
        offer.setTenantId(tid);
        offer.setTitle("Développeur Java");
        offer.setDepartmentId(depts.get(0).getId());
        offer.setCity("Casablanca");
        offer.setDescription("Spring Boot, 3 ans d'expérience.");
        offer.setStatus("OPEN");
        jobOfferRepository.save(offer);

        Candidate cand = new Candidate();
        cand.setTenantId(tid);
        cand.setFirstName("Rania");
        cand.setLastName("Benkirane");
        cand.setEmail("rania.benkirane@mail.ma");
        cand.setJobOfferId(offer.getId());
        cand.setStatus("INTERVIEW");
        candidateRepository.save(cand);

        OnboardingTask on = new OnboardingTask();
        on.setTenantId(tid);
        on.setEmployeeId(employees.get(3).getId());
        on.setTask("Remettre CIN et RIB");
        on.setDueDate(today.plusDays(5));
        on.setStatus("PENDING");
        onboardingTaskRepository.save(on);

        OffboardingTask off = new OffboardingTask();
        off.setTenantId(tid);
        off.setEmployeeId(employees.get(23).getId());
        off.setTask("Restituer le matériel");
        off.setDueDate(today.plusDays(15));
        off.setStatus("PENDING");
        offboardingTaskRepository.save(off);

        PayrollPeriod period = new PayrollPeriod();
        period.setTenantId(tid);
        period.setYear(today.getYear());
        period.setMonth(today.getMonthValue());
        period.setStatus("CALCULATED");
        payrollPeriodRepository.save(period);
        BigDecimal cost = BigDecimal.ZERO;
        TenantContext.setTenantId(tid);
        for (Employee e : employees) {
            Contract c = contractRepository.findByEmployeeIdAndStatus(e.getId(), "ACTIVE").stream().findFirst().orElse(null);
            if (c == null) {
                continue;
            }
            Payslip slip = payrollEngine.calculate(e, c, period.getYear(), period.getMonth());
            slip.setTenantId(tid);
            slip.setPeriodId(period.getId());
            payslipRepository.save(slip);
            cost = cost.add(slip.getEmployerCost());
        }
        period.setEmployerCost(cost);
        payrollPeriodRepository.save(period);
    }

    private User user(String email, String password, String first, String last, UserRole role, Long tenantId, String locale) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setFirstName(first);
        u.setLastName(last);
        u.setRole(role);
        u.setTenantId(tenantId);
        u.setLocale(locale);
        u.setActive(true);
        return u;
    }

    private LeaveType leaveType(Long tid, String name, String code, boolean paid, boolean deducts) {
        LeaveType t = new LeaveType();
        t.setTenantId(tid);
        t.setName(name);
        t.setCode(code);
        t.setPaid(paid);
        t.setDeductsAnnual(deducts);
        return leaveTypeRepository.save(t);
    }

    private void holiday(Long tid, LocalDate date, String name, String type) {
        Holiday h = new Holiday();
        h.setTenantId(tid);
        h.setDate(date);
        h.setName(name);
        h.setType(type);
        holidayRepository.save(h);
    }
}
