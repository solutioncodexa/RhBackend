package ma.rhflow.domain.enums;

import java.util.Set;

public final class PermissionCodes {

    public static final String EMPLOYEES_VIEW = "EMPLOYEES_VIEW";
    public static final String EMPLOYEES_CREATE = "EMPLOYEES_CREATE";
    public static final String EMPLOYEES_EDIT = "EMPLOYEES_EDIT";
    public static final String DEPARTMENTS_MANAGE = "DEPARTMENTS_MANAGE";
    public static final String POSITIONS_MANAGE = "POSITIONS_MANAGE";
    public static final String CONTRACTS_VIEW = "CONTRACTS_VIEW";
    public static final String CONTRACTS_MANAGE = "CONTRACTS_MANAGE";
    public static final String ATTENDANCE_VIEW = "ATTENDANCE_VIEW";
    public static final String ATTENDANCE_MANAGE = "ATTENDANCE_MANAGE";
    public static final String LEAVE_VIEW = "LEAVE_VIEW";
    public static final String LEAVE_REQUEST = "LEAVE_REQUEST";
    public static final String LEAVE_APPROVE = "LEAVE_APPROVE";
    public static final String DOCUMENTS_VIEW = "DOCUMENTS_VIEW";
    public static final String DOCUMENTS_UPLOAD = "DOCUMENTS_UPLOAD";
    public static final String DOCUMENTS_PRIVATE = "DOCUMENTS_PRIVATE";
    public static final String REQUESTS_CREATE = "REQUESTS_CREATE";
    public static final String REQUESTS_PROCESS = "REQUESTS_PROCESS";
    public static final String ANNOUNCEMENTS_VIEW = "ANNOUNCEMENTS_VIEW";
    public static final String ANNOUNCEMENTS_MANAGE = "ANNOUNCEMENTS_MANAGE";
    public static final String USERS_MANAGE = "USERS_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String ASSISTANT_USE = "ASSISTANT_USE";
    public static final String STATS_VIEW = "STATS_VIEW";
    public static final String PAYROLL_VIEW = "PAYROLL_VIEW";
    public static final String PAYROLL_EDIT = "PAYROLL_EDIT";
    public static final String PAYSLIPS_VIEW_OWN = "PAYSLIPS_VIEW_OWN";
    public static final String EXPENSES_VIEW = "EXPENSES_VIEW";
    public static final String EXPENSES_APPROVE = "EXPENSES_APPROVE";
    public static final String RECRUITMENT_MANAGE = "RECRUITMENT_MANAGE";
    public static final String PERFORMANCE_MANAGE = "PERFORMANCE_MANAGE";
    public static final String PLATFORM = "PLATFORM";

    public static final Set<String> ALL = Set.of(
            EMPLOYEES_VIEW, EMPLOYEES_CREATE, EMPLOYEES_EDIT,
            DEPARTMENTS_MANAGE, POSITIONS_MANAGE,
            CONTRACTS_VIEW, CONTRACTS_MANAGE,
            ATTENDANCE_VIEW, ATTENDANCE_MANAGE,
            LEAVE_VIEW, LEAVE_REQUEST, LEAVE_APPROVE,
            DOCUMENTS_VIEW, DOCUMENTS_UPLOAD, DOCUMENTS_PRIVATE,
            REQUESTS_CREATE, REQUESTS_PROCESS,
            ANNOUNCEMENTS_VIEW, ANNOUNCEMENTS_MANAGE,
            USERS_MANAGE, SETTINGS_MANAGE, AUDIT_VIEW,
            ASSISTANT_USE, STATS_VIEW,
            PAYROLL_VIEW, PAYROLL_EDIT, PAYSLIPS_VIEW_OWN,
            EXPENSES_VIEW, EXPENSES_APPROVE, RECRUITMENT_MANAGE, PERFORMANCE_MANAGE
    );

    public static final Set<String> DEFAULT_MANAGER = Set.of(
            EMPLOYEES_VIEW, CONTRACTS_VIEW, ATTENDANCE_VIEW,
            LEAVE_VIEW, LEAVE_APPROVE, LEAVE_REQUEST,
            DOCUMENTS_VIEW, REQUESTS_CREATE,
            ANNOUNCEMENTS_VIEW, ANNOUNCEMENTS_MANAGE,
            ASSISTANT_USE, STATS_VIEW, REQUESTS_PROCESS,
            EXPENSES_VIEW, EXPENSES_APPROVE, PERFORMANCE_MANAGE
    );

    public static final Set<String> DEFAULT_EMPLOYEE = Set.of(
            LEAVE_VIEW, LEAVE_REQUEST, DOCUMENTS_VIEW,
            REQUESTS_CREATE, ANNOUNCEMENTS_VIEW,
            ASSISTANT_USE, PAYSLIPS_VIEW_OWN, EXPENSES_VIEW
    );

    public static boolean isKnown(String code) {
        return "*".equals(code) || ALL.contains(code);
    }

    private PermissionCodes() {
    }
}
