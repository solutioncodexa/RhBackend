package ma.rhflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.service.PayrollEngine;
import ma.rhflow.service.WorkCalendar;

class RecetteTests {

    private final PayrollEngine engine = new PayrollEngine();

    @Test
    void recette8SundayAndHolidayAreNotWorkingDays() {
        Set<DayOfWeek> week = EnumSet.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        LocalDate start = LocalDate.of(2026, 4, 30);
        LocalDate end = LocalDate.of(2026, 5, 3);
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 5, 1));
        assertEquals(1, WorkCalendar.workingDays(start, end, week, holidays));
    }

    @Test
    void recette7CddWithinTwentyDaysIsInThirtyDayWindow() {
        LocalDate today = LocalDate.of(2026, 10, 6);
        LocalDate end = today.plusDays(20);
        assertTrue(!end.isAfter(today.plusDays(30)));
        assertTrue(!end.isAfter(today.plusDays(90)));
    }

    @Test
    void recette13PayrollWitness10000Mad2026() {
        Employee e = new Employee();
        e.setId(1L);
        e.setHireDate(LocalDate.of(2026, 10, 1));
        e.setDependents(0);
        Contract c = new Contract();
        c.setBaseSalary(new BigDecimal("10000"));
        c.setWeeklyHours(new BigDecimal("44"));
        var slip = engine.calculate(e, c, 2026, 10);
        assertEquals(new BigDecimal("10000.00"), slip.getGross());
        assertEquals(new BigDecimal("268.80"), slip.getCnss());
        assertEquals(new BigDecimal("226.00"), slip.getAmo());
        assertTrue(slip.getIr().signum() > 0);
        assertTrue(slip.getNet().signum() > 0);
        assertEquals(slip.getGross().subtract(slip.getDeductions()), slip.getNet());
    }
}
