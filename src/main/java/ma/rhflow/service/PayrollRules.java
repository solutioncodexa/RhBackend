package ma.rhflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayrollRules(
        Long id,
        String code,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal smigMonthly,
        BigDecimal legalWeeklyHours,
        BigDecimal cnssCap,
        BigDecimal cnssSt,
        BigDecimal cnssLt,
        BigDecimal amoEmployee,
        BigDecimal employerAf,
        BigDecimal employerPsct,
        BigDecimal employerPslt,
        BigDecimal employerAmo,
        BigDecimal employerTfp,
        BigDecimal fpAnnualThreshold,
        BigDecimal fpLowRate,
        BigDecimal fpHighRate,
        BigDecimal fpAnnualCap,
        BigDecimal overtimeDay,
        BigDecimal overtimeNight,
        BigDecimal overtimeRest,
        BigDecimal overtimeNoComp,
        BigDecimal familyReductionAnnual,
        int maxDependents,
        List<Bracket> brackets
) {
    public record Bracket(BigDecimal minInclusive, BigDecimal maxInclusive, BigDecimal rate, BigDecimal deduction) {
    }

    public static PayrollRules ma2026() {
        return new PayrollRules(
                null,
                "MA-2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("3422"),
                new BigDecimal("44"),
                new BigDecimal("6000"),
                new BigDecimal("0.0052"),
                new BigDecimal("0.0396"),
                new BigDecimal("0.0226"),
                new BigDecimal("0.0640"),
                new BigDecimal("0.0105"),
                new BigDecimal("0.0793"),
                new BigDecimal("0.0411"),
                new BigDecimal("0.0160"),
                new BigDecimal("78000"),
                new BigDecimal("0.35"),
                new BigDecimal("0.25"),
                new BigDecimal("35000"),
                new BigDecimal("0.25"),
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("1.00"),
                new BigDecimal("600"),
                6,
                List.of(
                        new Bracket(BigDecimal.ZERO, new BigDecimal("40000"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new Bracket(new BigDecimal("40000.01"), new BigDecimal("60000"), new BigDecimal("0.10"), new BigDecimal("4000")),
                        new Bracket(new BigDecimal("60000.01"), new BigDecimal("80000"), new BigDecimal("0.20"), new BigDecimal("10000")),
                        new Bracket(new BigDecimal("80000.01"), new BigDecimal("100000"), new BigDecimal("0.30"), new BigDecimal("18000")),
                        new Bracket(new BigDecimal("100000.01"), new BigDecimal("180000"), new BigDecimal("0.34"), new BigDecimal("22000")),
                        new Bracket(new BigDecimal("180000.01"), null, new BigDecimal("0.37"), new BigDecimal("27400"))
                )
        );
    }

    public BigDecimal cnssEmployee() {
        return cnssSt.add(cnssLt);
    }
}
