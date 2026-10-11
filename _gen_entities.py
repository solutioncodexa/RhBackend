# Generates JPA entities + Spring Data repositories for RHFlow.
from pathlib import Path

ROOT = Path(r"C:\Users\DELL\Desktop\code\rhflow\RHFlowBack\src\main\java\ma\rhflow")
ENT = ROOT / "domain" / "entity"
REPO = ROOT / "domain" / "repository"
ENT.mkdir(parents=True, exist_ok=True)
REPO.mkdir(parents=True, exist_ok=True)

FILTER = '''@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
'''

HEADER = '''package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.tenant.TenantScoped;
'''

def write(path, content):
    path.write_text(content.replace("\n", "\r\n") if False else content, encoding="utf-8")

# --- Tenant ---
write(ENT / "Tenant.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.SubscriptionPlan;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String legalName;
    @Column(length = 15)
    private String ice;
    private String taxId;
    private String rc;
    private String cnssAffiliation;
    private String email;
    private String phone;
    private String city;
    private String address;
    private String website;
    private String sector;
    private String currency = "MAD";
    private String whatsappPhoneNumberId;
    private String whatsappBusinessAccountId;
    private String whatsappAccessToken;
    private boolean whatsappEnabled;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan = SubscriptionPlan.ESSENTIEL;
    private boolean featureOllama;
    private boolean active = true;
    private LocalDate trialEndsAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
''')

write(ENT / "User.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String firstName;

    @Column(nullable = false, length = 120)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserRole role;

    @Column(length = 10)
    private String locale = "fr";

    private boolean active = true;
    private Long employeeId;
    private Instant lastLoginAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
''')

write(ENT / "UserPermission.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_permission", columnNames = {"user_id", "permission_code"})
})
@Getter
@Setter
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id")
    private Long tenantId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "permission_code", nullable = false, length = 80)
    private String permissionCode;
    private boolean granted = true;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
''')

write(ENT / "RefreshToken.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;
    private String deviceType = "WEB";
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean revoked;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }
}
''')

write(ENT / "PasswordResetToken.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean used;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
''')

write(ENT / "AuditLog.java", '''package ma.rhflow.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class AuditLog extends TenantScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String action;
    private String entityType;
    private String entityId;
    private String userEmail;
    private Long userId;
    @Column(length = 2000)
    private String details;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
''')

def tenant_entity(name, table, fields, extra_imports=""):
    return f'''package ma.rhflow.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.rhflow.tenant.TenantScoped;
{extra_imports}
@Entity
@Table(name = "{table}")
@Getter
@Setter
@Filter(name = TenantScoped.FILTER_NAME, condition = "tenant_id = :" + TenantScoped.PARAM_NAME)
public class {name} extends TenantScoped {{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
{fields}
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    @PreUpdate
    void onUpdate() {{
        updatedAt = Instant.now();
    }}
}}
'''

entities = {
"Employee": ("employees", '''
    private String firstName;
    private String lastName;
    private String email;
    private String cin;
    private String phone;
    private String city;
    private LocalDate hireDate;
    private String cnssNumber;
    private String rib;
    private Long departmentId;
    private Long positionId;
    private Long managerId;
    private String status = "ACTIVE";
    private Integer dependents = 0;
'''),
"Department": ("departments", '''
    private String name;
    @Column(length = 2000)
    private String description;
    private Long managerId;
'''),
"Position": ("positions", '''
    private String title;
    private Long departmentId;
    @Column(length = 2000)
    private String description;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
'''),
"Contract": ("contracts", '''
    private Long employeeId;
    private String type;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal baseSalary;
    private BigDecimal weeklyHours;
    private Long departmentId;
    private Long positionId;
    private String status = "ACTIVE";
'''),
"Attendance": ("attendance", '''
    private Long employeeId;
    private LocalDate date;
    private LocalTime checkIn;
    private LocalTime checkOut;
    private String status;
    @Column(length = 2000)
    private String comment;
'''),
"WorkingHours": ("working_hours", '''
    private String name;
    private Long departmentId;
    private Long employeeId;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime breakStart;
    private LocalTime breakEnd;
    private BigDecimal weeklyHours;
    private String workDays;
'''),
"LeaveType": ("leave_types", '''
    private String name;
    private String code;
    private boolean paid = true;
    private boolean deductsAnnual = true;
'''),
"LeaveBalance": ("leave_balances", '''
    private Long employeeId;
    private Long leaveTypeId;
    private Integer year;
    private BigDecimal accrued = BigDecimal.ZERO;
    private BigDecimal used = BigDecimal.ZERO;
    private BigDecimal remaining = BigDecimal.ZERO;
'''),
"LeaveRequest": ("leave_requests", '''
    private Long employeeId;
    private Long leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days;
    private String status = "PENDING";
    @Column(length = 2000)
    private String comment;
    @Column(length = 2000)
    private String decisionComment;
    private Long decidedBy;
'''),
"HrDocument": ("documents", '''
    private String name;
    private Long employeeId;
    private String category;
    private boolean visibleToEmployee = true;
    private String originalName;
'''),
"EmployeeRequest": ("employee_requests", '''
    private Long employeeId;
    private String type;
    private String status = "PENDING";
    @Column(length = 2000)
    private String notes;
'''),
"Announcement": ("announcements", '''
    private String title;
    @Column(length = 4000)
    private String body;
    private String target = "COMPANY";
    private Long departmentId;
'''),
"Notification": ("notifications", '''
    private Long userId;
    private String title;
    @Column(length = 2000)
    private String message;
    private boolean readFlag;
'''),
"Expense": ("expenses", '''
    private Long employeeId;
    private String category;
    private BigDecimal amount;
    private LocalDate date;
    private String status = "PENDING";
    @Column(length = 2000)
    private String notes;
'''),
"BusinessTrip": ("business_trips", '''
    private Long employeeId;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status = "PENDING";
    @Column(length = 2000)
    private String purpose;
'''),
"PerformanceReview": ("performance_reviews", '''
    private Long employeeId;
    private String period;
    private BigDecimal rating;
    private String status = "DRAFT";
    @Column(length = 4000)
    private String comments;
'''),
"Objective": ("objectives", '''
    private Long employeeId;
    private String title;
    private LocalDate dueDate;
    private Integer progress = 0;
'''),
"JobOffer": ("job_offers", '''
    private String title;
    private Long departmentId;
    private String city;
    @Column(length = 4000)
    private String description;
    private String status = "OPEN";
'''),
"Candidate": ("candidates", '''
    private String firstName;
    private String lastName;
    private String email;
    private Long jobOfferId;
    private String status = "NEW";
'''),
"OnboardingTask": ("onboarding_tasks", '''
    private Long employeeId;
    private String task;
    private LocalDate dueDate;
    private String status = "PENDING";
'''),
"OffboardingTask": ("offboarding_tasks", '''
    private Long employeeId;
    private String task;
    private LocalDate dueDate;
    private String status = "PENDING";
'''),
"Holiday": ("holidays", '''
    private LocalDate date;
    private String name;
    private String type = "NATIONAL";
'''),
"PayrollPeriod": ("payroll_periods", '''
    private Integer year;
    private Integer month;
    private String status = "DRAFT";
    private BigDecimal employerCost = BigDecimal.ZERO;
'''),
"Payslip": ("payslips", '''
    private Long periodId;
    private Long employeeId;
    private Integer year;
    private Integer month;
    private BigDecimal baseSalary = BigDecimal.ZERO;
    private BigDecimal seniorityAmount = BigDecimal.ZERO;
    private BigDecimal bonuses = BigDecimal.ZERO;
    private BigDecimal deductions = BigDecimal.ZERO;
    private BigDecimal gross = BigDecimal.ZERO;
    private BigDecimal cnss = BigDecimal.ZERO;
    private BigDecimal amo = BigDecimal.ZERO;
    private BigDecimal ir = BigDecimal.ZERO;
    private BigDecimal net = BigDecimal.ZERO;
    private BigDecimal employerCnss = BigDecimal.ZERO;
    private BigDecimal employerAmo = BigDecimal.ZERO;
    private BigDecimal employerAf = BigDecimal.ZERO;
    private BigDecimal employerPsct = BigDecimal.ZERO;
    private BigDecimal employerPslt = BigDecimal.ZERO;
    private BigDecimal employerTfp = BigDecimal.ZERO;
    private BigDecimal employerCost = BigDecimal.ZERO;
    private boolean smigAlert;
'''),
"AssistantPendingAction": ("assistant_pending_actions", '''
    private String actionId;
    private String actionType;
    private Long userId;
    @Column(length = 8000)
    private String draftJson;
    private Instant expiresAt;
    private boolean consumed;
'''),
}

for name, (table, fields) in entities.items():
    write(ENT / f"{name}.java", tenant_entity(name, table, fields))

# Repositories
simple_repos = [
    ("Tenant", False),
    ("User", False),
    ("UserPermission", False),
    ("RefreshToken", False),
    ("PasswordResetToken", False),
    ("AuditLog", True),
    ("Employee", True),
    ("Department", True),
    ("Position", True),
    ("Contract", True),
    ("Attendance", True),
    ("WorkingHours", True),
    ("LeaveType", True),
    ("LeaveBalance", True),
    ("LeaveRequest", True),
    ("HrDocument", True),
    ("EmployeeRequest", True),
    ("Announcement", True),
    ("Notification", True),
    ("Expense", True),
    ("BusinessTrip", True),
    ("PerformanceReview", True),
    ("Objective", True),
    ("JobOffer", True),
    ("Candidate", True),
    ("OnboardingTask", True),
    ("OffboardingTask", True),
    ("Holiday", True),
    ("PayrollPeriod", True),
    ("Payslip", True),
    ("AssistantPendingAction", True),
]

extra_methods = {
"User": '''
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByTenantIdOrderByLastNameAscFirstNameAsc(Long tenantId);
''',
"UserPermission": '''
    List<UserPermission> findByUserIdAndGrantedTrue(Long userId);
    void deleteByUserId(Long userId);
''',
"RefreshToken": '''
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
''',
"PasswordResetToken": '''
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
''',
"Tenant": '''
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);
''',
"Employee": '''
    Optional<Employee> findByEmailIgnoreCase(String email);
    long countByStatus(String status);
''',
"LeaveRequest": '''
    List<LeaveRequest> findByStatus(String status);
''',
"LeaveBalance": '''
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);
''',
"PayrollPeriod": '''
    Optional<PayrollPeriod> findByYearAndMonth(Integer year, Integer month);
''',
"Payslip": '''
    List<Payslip> findByPeriodId(Long periodId);
    Optional<Payslip> findByPeriodIdAndEmployeeId(Long periodId, Long employeeId);
    void deleteByPeriodId(Long periodId);
''',
"Holiday": '''
    List<Holiday> findByDateBetween(LocalDate from, LocalDate to);
    boolean existsByDate(LocalDate date);
''',
"AssistantPendingAction": '''
    Optional<AssistantPendingAction> findByActionId(String actionId);
''',
"Attendance": '''
    List<Attendance> findByDateAndStatus(LocalDate date, String status);
    Optional<Attendance> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
''',
"Contract": '''
    List<Contract> findByEmployeeIdAndStatus(Long employeeId, String status);
''',
"Notification": '''
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
''',
}

imports_extra = {
"User": "import java.util.List;\nimport java.util.Optional;\n",
"UserPermission": "import java.util.List;\n",
"RefreshToken": "import java.util.List;\nimport java.util.Optional;\n",
"PasswordResetToken": "import java.util.Optional;\n",
"Tenant": "import java.util.Optional;\n",
"Employee": "import java.util.Optional;\n",
"LeaveRequest": "import java.util.List;\n",
"LeaveBalance": "import java.util.Optional;\n",
"PayrollPeriod": "import java.util.Optional;\n",
"Payslip": "import java.util.List;\nimport java.util.Optional;\n",
"Holiday": "import java.time.LocalDate;\nimport java.util.List;\n",
"AssistantPendingAction": "import java.util.Optional;\n",
"Attendance": "import java.time.LocalDate;\nimport java.util.List;\nimport java.util.Optional;\n",
"Contract": "import java.util.List;\n",
"Notification": "import java.util.List;\n",
}

for name, _ in simple_repos:
    extra = extra_methods.get(name, "")
    imps = imports_extra.get(name, "")
    write(REPO / f"{name}Repository.java", f'''package ma.rhflow.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
{imps}
import ma.rhflow.domain.entity.{name};

public interface {name}Repository extends JpaRepository<{name}, Long>, JpaSpecificationExecutor<{name}> {{{extra}}}
''')

print("generated", len(list(ENT.glob('*.java'))), "entities", len(list(REPO.glob('*.java'))), "repos")
