package ma.rhflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.service.PayrollEngine;

class ExtraModulesTest {

    @Test
    void netToGrossSimulationConvergesAboveTarget() {
        PayrollEngine engine = new PayrollEngine();
        Employee e = new Employee();
        e.setDependents(0);
        e.setHireDate(LocalDate.of(2020, 1, 1));
        Contract c = new Contract();
        c.setWeeklyHours(new BigDecimal("44"));
        BigDecimal target = new BigDecimal("8000");
        BigDecimal low = target;
        BigDecimal high = new BigDecimal("20000");
        for (int i = 0; i < 24; i++) {
            BigDecimal mid = low.add(high).divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
            c.setBaseSalary(mid);
            var slip = engine.calculate(e, c, 2026, 10);
            if (slip.getNet().compareTo(target) >= 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        c.setBaseSalary(high);
        var slip = engine.calculate(e, c, 2026, 10);
        assertTrue(slip.getNet().compareTo(target) >= 0);
        assertTrue(slip.getGross().compareTo(target) > 0);
    }
}
