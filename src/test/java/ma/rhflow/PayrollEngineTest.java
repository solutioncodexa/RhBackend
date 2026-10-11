package ma.rhflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.service.PayrollEngine;
import ma.rhflow.service.PayrollRules;

class PayrollEngineTest {

    private final PayrollEngine engine = new PayrollEngine();

    @Test
    void brut10000HasCappedCnssAndPositiveNet() {
        Employee e = employee();
        Contract c = contract("10000");
        var slip = engine.calculate(e, c, 2026, 10);
        assertEquals(new BigDecimal("268.80"), slip.getCnss());
        assertEquals(new BigDecimal("226.00"), slip.getAmo());
        assertFalse(slip.getNet().signum() <= 0);
        assertEquals(new BigDecimal("10000.00"), slip.getGross());
        assertFalse(slip.isSmigAlert());
    }

    @Test
    void overtimeDayUsesTwentyFivePercent() {
        Employee e = employee();
        Contract c = contract("10000");
        c.setWeeklyHours(new BigDecimal("44"));
        var inputs = new PayrollEngine.Inputs(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 26);
        var slip = engine.calculate(e, c, 2026, 10, PayrollRules.ma2026(), inputs, PayrollEngine.CompanyBenefits.none());
        assertTrue(slip.getOvertimeAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(slip.getGross().compareTo(new BigDecimal("10000")) > 0);
    }

    @Test
    void smigAlertWhenHourlyBelowMinimum() {
        Employee e = employee();
        Contract c = contract("2000");
        c.setWeeklyHours(new BigDecimal("44"));
        var slip = engine.calculate(e, c, 2026, 10);
        assertTrue(slip.isSmigAlert());
    }

    @Test
    void rulesMa2026MatchCahierRates() {
        PayrollRules r = PayrollRules.ma2026();
        assertEquals(new BigDecimal("0.0052"), r.cnssSt());
        assertEquals(new BigDecimal("0.0396"), r.cnssLt());
        assertEquals(new BigDecimal("0.0226"), r.amoEmployee());
        assertEquals(new BigDecimal("6000"), r.cnssCap());
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(1L);
        e.setHireDate(LocalDate.of(2026, 10, 1));
        e.setDependents(0);
        return e;
    }

    private Contract contract(String salary) {
        Contract c = new Contract();
        c.setBaseSalary(new BigDecimal(salary));
        c.setWeeklyHours(new BigDecimal("44"));
        return c;
    }
}
