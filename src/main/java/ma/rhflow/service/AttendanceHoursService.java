package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.Attendance;
import ma.rhflow.domain.entity.HrRecord;
import ma.rhflow.domain.entity.WorkingHours;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.HrRecordRepository;
import ma.rhflow.domain.repository.WorkingHoursRepository;

@Service
@RequiredArgsConstructor
public class AttendanceHoursService {

    private final AttendanceRepository attendanceRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final HrRecordRepository hrRecordRepository;

    public record Overtime(BigDecimal day, BigDecimal night, BigDecimal rest) {
        public static Overtime none() {
            return new Overtime(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public Overtime overtime(Long employeeId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Attendance> rows = attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, start, end);
        WorkingHours hours = hoursOf(employeeId);
        BigDecimal day = BigDecimal.ZERO;
        BigDecimal night = BigDecimal.ZERO;
        BigDecimal rest = BigDecimal.ZERO;
        for (Attendance row : rows) {
            if (row.getCheckIn() == null || row.getCheckOut() == null) {
                continue;
            }
            BigDecimal worked = hoursBetween(row.getCheckIn(), row.getCheckOut(), hours);
            row.setHours(worked);
            boolean weekend = row.getDate().getDayOfWeek().getValue() >= 6;
            String workDays = hours == null || hours.getWorkDays() == null ? "1,2,3,4,5" : hours.getWorkDays();
            boolean scheduled = workDays.contains(String.valueOf(row.getDate().getDayOfWeek().getValue()));
            BigDecimal expected = expectedDay(hours);
            BigDecimal extra = worked.subtract(expected).max(BigDecimal.ZERO);
            if (weekend || !scheduled) {
                rest = rest.add(worked);
            } else if (row.getCheckOut().isAfter(LocalTime.of(21, 0))) {
                night = night.add(extra);
            } else {
                day = day.add(extra);
            }
        }
        attendanceRepository.saveAll(rows);
        return new Overtime(money(day), money(night), money(rest));
    }

    public BigDecimal loanInstallment(Long employeeId) {
        BigDecimal total = BigDecimal.ZERO;
        for (HrRecord loan : hrRecordRepository.findByKindAndEmployeeId(HrRecord.LOAN, employeeId)) {
            if (!"ACTIVE".equalsIgnoreCase(blank(loan.getStatus()))) {
                continue;
            }
            BigDecimal monthly = n(loan.getMonthlyAmount());
            if (monthly.signum() <= 0) {
                continue;
            }
            BigDecimal remaining = n(loan.getRemaining());
            total = total.add(monthly.min(remaining.signum() <= 0 ? monthly : remaining));
        }
        return money(total);
    }

    public void applyLoanPayments(Long employeeId, BigDecimal paid) {
        BigDecimal left = n(paid);
        for (HrRecord loan : hrRecordRepository.findByKindAndEmployeeId(HrRecord.LOAN, employeeId)) {
            if (!"ACTIVE".equalsIgnoreCase(blank(loan.getStatus())) || left.signum() <= 0) {
                continue;
            }
            BigDecimal remaining = n(loan.getRemaining());
            BigDecimal take = remaining.min(left).min(n(loan.getMonthlyAmount()).max(left));
            if (take.signum() <= 0) {
                take = remaining.min(left);
            }
            remaining = remaining.subtract(take).max(BigDecimal.ZERO);
            loan.setRemaining(remaining);
            if (remaining.signum() == 0) {
                loan.setStatus("CLOSED");
            }
            hrRecordRepository.save(loan);
            left = left.subtract(take);
        }
    }

    private WorkingHours hoursOf(Long employeeId) {
        List<WorkingHours> all = workingHoursRepository.findAll();
        WorkingHours fallback = null;
        for (WorkingHours h : all) {
            if (employeeId.equals(h.getEmployeeId())) {
                return h;
            }
            if (h.getEmployeeId() == null) {
                fallback = h;
            }
        }
        return fallback;
    }

    private static BigDecimal expectedDay(WorkingHours hours) {
        if (hours == null || hours.getWeeklyHours() == null || hours.getWeeklyHours().signum() == 0) {
            return new BigDecimal("8");
        }
        int days = hours.getWorkDays() == null || hours.getWorkDays().isBlank()
                ? 5
                : hours.getWorkDays().split(",").length;
        if (days <= 0) {
            days = 5;
        }
        return hours.getWeeklyHours().divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hoursBetween(LocalTime in, LocalTime out, WorkingHours hours) {
        Duration worked = Duration.between(in, out);
        if (worked.isNegative()) {
            worked = worked.plusHours(24);
        }
        if (hours != null && hours.getBreakStart() != null && hours.getBreakEnd() != null
                && !hours.getBreakEnd().isBefore(hours.getBreakStart())
                && !in.isAfter(hours.getBreakStart()) && !out.isBefore(hours.getBreakEnd())) {
            worked = worked.minus(Duration.between(hours.getBreakStart(), hours.getBreakEnd()));
        }
        return BigDecimal.valueOf(worked.toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return n(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blank(String v) {
        return v == null ? "" : v.trim();
    }
}
