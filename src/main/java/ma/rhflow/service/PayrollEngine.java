package ma.rhflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Component;

import ma.rhflow.domain.entity.Contract;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.Payslip;

@Component
public class PayrollEngine {

    public record CompanyBenefits(BigDecimal cimrEmployee, BigDecimal cimrEmployer, BigDecimal mutualEmployee, BigDecimal mutualEmployer) {
        public static CompanyBenefits none() {
            return new CompanyBenefits(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Inputs(
            BigDecimal taxableCnssBonuses,
            BigDecimal taxableNoCnssBonuses,
            BigDecimal nontaxableBonuses,
            BigDecimal overtimeHoursDay,
            BigDecimal overtimeHoursNight,
            BigDecimal overtimeHoursRest,
            BigDecimal overtimeHoursNoComp,
            BigDecimal unpaidAbsence,
            BigDecimal advances,
            BigDecimal garnishments,
            BigDecimal creditNote,
            int declaredDays
    ) {
        public static Inputs none() {
            return new Inputs(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 26);
        }
    }

    public Payslip calculate(Employee employee, Contract contract, int year, int month) {
        return calculate(employee, contract, year, month, PayrollRules.ma2026(), Inputs.none(), CompanyBenefits.none());
    }

    public Payslip calculate(Employee employee, Contract contract, int year, int month, Inputs inputs) {
        return calculate(employee, contract, year, month, PayrollRules.ma2026(), inputs, CompanyBenefits.none());
    }

    public Payslip calculate(
            Employee employee,
            Contract contract,
            int year,
            int month,
            PayrollRules rules,
            Inputs inputs,
            CompanyBenefits benefits
    ) {
        PayrollRules r = rules == null ? PayrollRules.ma2026() : rules;
        Inputs in = inputs == null ? Inputs.none() : inputs;
        CompanyBenefits b = benefits == null ? CompanyBenefits.none() : benefits;
        Payslip p = new Payslip();
        BigDecimal base = n(contract.getBaseSalary());
        BigDecimal weekly = n(contract.getWeeklyHours()).signum() == 0 ? r.legalWeeklyHours() : n(contract.getWeeklyHours());
        BigDecimal monthlyHours = weekly.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        BigDecimal hourly = monthlyHours.signum() == 0 ? BigDecimal.ZERO : base.divide(monthlyHours, 6, RoundingMode.HALF_UP);
        BigDecimal smigHourly = r.legalWeeklyHours().multiply(BigDecimal.valueOf(52))
                .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        smigHourly = smigHourly.signum() == 0 ? r.smigMonthly() : r.smigMonthly().divide(smigHourly, 6, RoundingMode.HALF_UP);

        BigDecimal seniorityPct = seniorityRate(employee.getHireDate(), LocalDate.of(year, month, 1));
        BigDecimal seniority = money(base.multiply(seniorityPct));
        BigDecimal otDay = money(hourly.multiply(n(in.overtimeHoursDay())).multiply(BigDecimal.ONE.add(r.overtimeDay())));
        BigDecimal otNight = money(hourly.multiply(n(in.overtimeHoursNight())).multiply(BigDecimal.ONE.add(r.overtimeNight())));
        BigDecimal otRest = money(hourly.multiply(n(in.overtimeHoursRest())).multiply(BigDecimal.ONE.add(r.overtimeRest())));
        BigDecimal otNoComp = money(hourly.multiply(n(in.overtimeHoursNoComp())).multiply(BigDecimal.ONE.add(r.overtimeNoComp())));
        BigDecimal overtime = money(otDay.add(otNight).add(otRest).add(otNoComp));
        BigDecimal bonusCnss = money(n(in.taxableCnssBonuses()));
        BigDecimal bonusIr = money(n(in.taxableNoCnssBonuses()));
        BigDecimal bonusNet = money(n(in.nontaxableBonuses()));
        BigDecimal unpaid = money(n(in.unpaidAbsence()));
        BigDecimal advances = money(n(in.advances()));
        BigDecimal garnish = money(n(in.garnishments()));
        BigDecimal credit = money(n(in.creditNote()));

        BigDecimal socialGross = money(base.add(seniority).add(overtime).add(bonusCnss).subtract(unpaid).max(BigDecimal.ZERO));
        BigDecimal displayGross = money(socialGross.add(bonusIr).add(bonusNet));

        BigDecimal cnssBase = socialGross.min(r.cnssCap());
        BigDecimal cnss = money(cnssBase.multiply(r.cnssEmployee()));
        BigDecimal amo = money(socialGross.multiply(r.amoEmployee()));
        BigDecimal afterSocial = socialGross.add(bonusIr).subtract(cnss).subtract(amo);

        BigDecimal annualTaxableProxy = afterSocial.multiply(BigDecimal.valueOf(12));
        BigDecimal fraisRate = annualTaxableProxy.compareTo(r.fpAnnualThreshold()) <= 0 ? r.fpLowRate() : r.fpHighRate();
        BigDecimal frais = money(afterSocial.multiply(fraisRate));
        BigDecimal fraisCap = money(r.fpAnnualCap().divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP));
        if (fraisRate.compareTo(r.fpHighRate()) == 0) {
            frais = frais.min(fraisCap);
        }
        BigDecimal monthlyTaxable = afterSocial.subtract(frais).max(BigDecimal.ZERO);
        BigDecimal annualTaxable = monthlyTaxable.multiply(BigDecimal.valueOf(12));
        BigDecimal irAnnual = irAnnual(annualTaxable, r);
        int deps = Math.min(employee.getDependents() == null ? 0 : employee.getDependents(), r.maxDependents());
        irAnnual = irAnnual.subtract(r.familyReductionAnnual().multiply(BigDecimal.valueOf(deps))).max(BigDecimal.ZERO);
        BigDecimal ir = money(irAnnual.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP));

        BigDecimal cimrEmp = money(socialGross.multiply(n(b.cimrEmployee())));
        BigDecimal cimrEr = money(socialGross.multiply(n(b.cimrEmployer())));
        BigDecimal mutEmp = money(socialGross.multiply(n(b.mutualEmployee())));
        BigDecimal mutEr = money(socialGross.multiply(n(b.mutualEmployer())));

        BigDecimal socialAndTax = cnss.add(amo).add(ir);
        BigDecimal net = money(displayGross.subtract(socialAndTax).subtract(advances).subtract(garnish).subtract(credit).subtract(cimrEmp).subtract(mutEmp));

        BigDecimal erAf = money(socialGross.multiply(r.employerAf()));
        BigDecimal erPsct = money(cnssBase.multiply(r.employerPsct()));
        BigDecimal erPslt = money(cnssBase.multiply(r.employerPslt()));
        BigDecimal erAmo = money(socialGross.multiply(r.employerAmo()));
        BigDecimal erTfp = money(socialGross.multiply(r.employerTfp()));
        BigDecimal employerCost = money(displayGross.add(erAf).add(erPsct).add(erPslt).add(erAmo).add(erTfp).add(cimrEr).add(mutEr));

        p.setEmployeeId(employee.getId());
        p.setYear(year);
        p.setMonth(month);
        p.setRuleSetId(r.id());
        p.setBaseSalary(money(base.add(seniority)));
        p.setSeniorityAmount(seniority);
        p.setBonuses(money(bonusCnss.add(bonusIr).add(bonusNet)));
        p.setOvertimeAmount(overtime);
        p.setUnpaidAbsence(unpaid);
        p.setAdvances(advances);
        p.setGarnishments(garnish);
        p.setCreditNote(credit);
        p.setCimrEmployee(cimrEmp);
        p.setCimrEmployer(cimrEr);
        p.setMutualEmployee(mutEmp);
        p.setMutualEmployer(mutEr);
        p.setDeclaredDays(in.declaredDays() <= 0 ? 26 : in.declaredDays());
        p.setGross(displayGross);
        p.setCnss(cnss);
        p.setAmo(amo);
        p.setIr(ir);
        p.setDeductions(socialAndTax.add(advances).add(garnish).add(credit).add(cimrEmp).add(mutEmp));
        p.setNet(net);
        p.setEmployerAf(erAf);
        p.setEmployerPsct(erPsct);
        p.setEmployerPslt(erPslt);
        p.setEmployerAmo(erAmo);
        p.setEmployerTfp(erTfp);
        p.setEmployerCnss(erPsct.add(erPslt));
        p.setEmployerCost(employerCost);
        p.setSmigAlert(hourly.compareTo(smigHourly) < 0);
        return p;
    }

    public BigDecimal seniorityRate(LocalDate hireDate, LocalDate at) {
        if (hireDate == null) {
            return BigDecimal.ZERO;
        }
        int years = Period.between(hireDate, at).getYears();
        if (years >= 20) {
            return new BigDecimal("0.20");
        }
        if (years >= 12) {
            return new BigDecimal("0.15");
        }
        if (years >= 5) {
            return new BigDecimal("0.10");
        }
        if (years >= 2) {
            return new BigDecimal("0.05");
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal irAnnual(BigDecimal annualTaxable) {
        return irAnnual(annualTaxable, PayrollRules.ma2026());
    }

    public BigDecimal irAnnual(BigDecimal annualTaxable, PayrollRules rules) {
        BigDecimal x = n(annualTaxable);
        for (PayrollRules.Bracket br : rules.brackets()) {
            BigDecimal max = br.maxInclusive();
            if (max == null || x.compareTo(max) <= 0) {
                if (x.compareTo(n(br.minInclusive())) < 0 && br.minInclusive().signum() > 0) {
                    continue;
                }
                return money(x.multiply(n(br.rate())).subtract(n(br.deduction()))).max(BigDecimal.ZERO);
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal money(BigDecimal v) {
        return n(v).setScale(2, RoundingMode.HALF_UP);
    }
}
