package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.Holiday;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.HolidayRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.LeaveTypeRepository;

@Component
@RequiredArgsConstructor
public class PayrollAbsenceLinker {

    private static final Set<DayOfWeek> WEEK = Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;

    public record Link(String kind, String label, int days, BigDecimal amount, String sourceType, Long sourceId) {}

    public List<Link> links(Long employeeId, int year, int month, BigDecimal monthlyPay) {
        List<Link> rows = new ArrayList<>();
        if (employeeId == null) {
            return rows;
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        Set<LocalDate> holidays = holidays(start, end);
        Set<LocalDate> covered = new HashSet<>();
        for (LeaveRequest request : leaveRequestRepository.findByEmployeeIdAndStatusIn(employeeId, List.of("APPROVED"))) {
            if (request.getStartDate() == null || request.getEndDate() == null) {
                continue;
            }
            LeaveType type = request.getLeaveTypeId() == null
                    ? null
                    : leaveTypeRepository.findById(request.getLeaveTypeId()).orElse(null);
            boolean paid = type == null || type.isPaid();
            int days = overlapDays(request.getStartDate(), request.getEndDate(), start, end, holidays, covered);
            if (days <= 0) {
                continue;
            }
            String name = type == null || type.getName() == null || type.getName().isBlank() ? "Congé" : type.getName();
            if (paid) {
                rows.add(new Link("LEAVE_PAID", name, days, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), "LEAVE", request.getId()));
            } else {
                rows.add(new Link("LEAVE_UNPAID", name, days, deduction(monthlyPay, days), "LEAVE", request.getId()));
            }
        }
        int absent = 0;
        for (Attendance row : attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start, end)) {
            if (row.getDate() == null || !"ABSENT".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            if (working(row.getDate(), holidays) && covered.add(row.getDate())) {
                absent++;
            }
        }
        if (absent > 0) {
            rows.add(new Link("ABSENCE", "Absences pointées", absent, deduction(monthlyPay, absent), "ATTENDANCE", null));
        }
        return rows;
    }

    public int unpaidDays(Long employeeId, int year, int month) {
        if (employeeId == null) {
            return 0;
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByDateBetween(start, end)) {
            if (holiday.getDate() != null) {
                holidays.add(holiday.getDate());
            }
        }
        Set<LocalDate> unpaid = new HashSet<>();
        for (LeaveRequest request : leaveRequestRepository.findByEmployeeIdAndStatusIn(employeeId, List.of("APPROVED"))) {
            if (request.getStartDate() == null || request.getEndDate() == null) {
                continue;
            }
            LeaveType type = request.getLeaveTypeId() == null
                    ? null
                    : leaveTypeRepository.findById(request.getLeaveTypeId()).orElse(null);
            if (type == null || type.isPaid()) {
                continue;
            }
            LocalDate from = request.getStartDate().isBefore(start) ? start : request.getStartDate();
            LocalDate to = request.getEndDate().isAfter(end) ? end : request.getEndDate();
            if (to.isBefore(from)) {
                continue;
            }
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                if (working(day, holidays)) {
                    unpaid.add(day);
                }
            }
        }
        for (Attendance row : attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start, end)) {
            if (row.getDate() == null || !"ABSENT".equalsIgnoreCase(row.getStatus())) {
                continue;
            }
            if (working(row.getDate(), holidays)) {
                unpaid.add(row.getDate());
            }
        }
        return unpaid.size();
    }

    public BigDecimal deduction(BigDecimal monthlyPay, int days) {
        if (days <= 0 || monthlyPay == null || monthlyPay.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return monthlyPay.divide(BigDecimal.valueOf(26), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Set<LocalDate> holidays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByDateBetween(start, end)) {
            if (holiday.getDate() != null) {
                holidays.add(holiday.getDate());
            }
        }
        return holidays;
    }

    private int overlapDays(LocalDate startDate, LocalDate endDate, LocalDate monthStart, LocalDate monthEnd, Set<LocalDate> holidays, Set<LocalDate> mark) {
        LocalDate from = startDate.isBefore(monthStart) ? monthStart : startDate;
        LocalDate to = endDate.isAfter(monthEnd) ? monthEnd : endDate;
        if (to.isBefore(from)) {
            return 0;
        }
        int count = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!working(day, holidays)) {
                continue;
            }
            if (mark != null && !mark.add(day)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean working(LocalDate day, Set<LocalDate> holidays) {
        return WEEK.contains(day.getDayOfWeek()) && !holidays.contains(day);
    }
}
