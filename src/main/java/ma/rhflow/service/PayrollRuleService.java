package ma.rhflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rhflow.domain.entity.PayrollBracket;
import ma.rhflow.domain.entity.PayrollRuleSet;
import ma.rhflow.domain.repository.PayrollRuleSetRepository;

@Service
@RequiredArgsConstructor
public class PayrollRuleService {

    private final PayrollRuleSetRepository repository;

    @Transactional
    public PayrollRules rulesFor(LocalDate date) {
        ensureSeeded();
        LocalDate d = date == null ? LocalDate.now() : date;
        return repository.findAll().stream()
                .filter(s -> s.getEffectiveFrom() == null || !s.getEffectiveFrom().isAfter(d))
                .filter(s -> s.getEffectiveTo() == null || !s.getEffectiveTo().isBefore(d))
                .max(Comparator.comparing(PayrollRuleSet::getEffectiveFrom, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::toRules)
                .orElse(PayrollRules.ma2026());
    }

    @Transactional
    public PayrollRules rulesById(Long id) {
        if (id == null) {
            return rulesFor(LocalDate.now());
        }
        return repository.findById(id).map(this::toRules).orElse(rulesFor(LocalDate.now()));
    }

    @Transactional
    public void ensureSeeded() {
        if (repository.count() > 0) {
            return;
        }
        PayrollRules src = PayrollRules.ma2026();
        PayrollRuleSet set = new PayrollRuleSet();
        set.setCode(src.code());
        set.setEffectiveFrom(src.effectiveFrom());
        set.setEffectiveTo(src.effectiveTo());
        set.setSmigMonthly(src.smigMonthly());
        set.setLegalWeeklyHours(src.legalWeeklyHours());
        set.setCnssCap(src.cnssCap());
        set.setCnssSt(src.cnssSt());
        set.setCnssLt(src.cnssLt());
        set.setAmoEmployee(src.amoEmployee());
        set.setEmployerAf(src.employerAf());
        set.setEmployerPsct(src.employerPsct());
        set.setEmployerPslt(src.employerPslt());
        set.setEmployerAmo(src.employerAmo());
        set.setEmployerTfp(src.employerTfp());
        set.setFpAnnualThreshold(src.fpAnnualThreshold());
        set.setFpLowRate(src.fpLowRate());
        set.setFpHighRate(src.fpHighRate());
        set.setFpAnnualCap(src.fpAnnualCap());
        set.setOvertimeDay(src.overtimeDay());
        set.setOvertimeNight(src.overtimeNight());
        set.setOvertimeRest(src.overtimeRest());
        set.setOvertimeNoComp(src.overtimeNoComp());
        set.setFamilyReductionAnnual(src.familyReductionAnnual());
        set.setMaxDependents(src.maxDependents());
        for (PayrollRules.Bracket b : src.brackets()) {
            PayrollBracket row = new PayrollBracket();
            row.setRuleSet(set);
            row.setMinInclusive(b.minInclusive());
            row.setMaxInclusive(b.maxInclusive());
            row.setRate(b.rate());
            row.setDeduction(b.deduction());
            set.getBrackets().add(row);
        }
        repository.save(set);
    }

    private PayrollRules toRules(PayrollRuleSet s) {
        List<PayrollRules.Bracket> brackets = s.getBrackets() == null ? List.of() : s.getBrackets().stream()
                .sorted(Comparator.comparing(b -> n(b.getMinInclusive())))
                .map(b -> new PayrollRules.Bracket(b.getMinInclusive(), b.getMaxInclusive(), b.getRate(), b.getDeduction()))
                .toList();
        if (brackets.isEmpty()) {
            brackets = PayrollRules.ma2026().brackets();
        }
        return new PayrollRules(
                s.getId(),
                s.getCode(),
                s.getEffectiveFrom(),
                s.getEffectiveTo(),
                n(s.getSmigMonthly(), PayrollRules.ma2026().smigMonthly()),
                n(s.getLegalWeeklyHours(), PayrollRules.ma2026().legalWeeklyHours()),
                n(s.getCnssCap(), PayrollRules.ma2026().cnssCap()),
                n(s.getCnssSt(), PayrollRules.ma2026().cnssSt()),
                n(s.getCnssLt(), PayrollRules.ma2026().cnssLt()),
                n(s.getAmoEmployee(), PayrollRules.ma2026().amoEmployee()),
                n(s.getEmployerAf(), PayrollRules.ma2026().employerAf()),
                n(s.getEmployerPsct(), PayrollRules.ma2026().employerPsct()),
                n(s.getEmployerPslt(), PayrollRules.ma2026().employerPslt()),
                n(s.getEmployerAmo(), PayrollRules.ma2026().employerAmo()),
                n(s.getEmployerTfp(), PayrollRules.ma2026().employerTfp()),
                n(s.getFpAnnualThreshold(), PayrollRules.ma2026().fpAnnualThreshold()),
                n(s.getFpLowRate(), PayrollRules.ma2026().fpLowRate()),
                n(s.getFpHighRate(), PayrollRules.ma2026().fpHighRate()),
                n(s.getFpAnnualCap(), PayrollRules.ma2026().fpAnnualCap()),
                n(s.getOvertimeDay(), PayrollRules.ma2026().overtimeDay()),
                n(s.getOvertimeNight(), PayrollRules.ma2026().overtimeNight()),
                n(s.getOvertimeRest(), PayrollRules.ma2026().overtimeRest()),
                n(s.getOvertimeNoComp(), PayrollRules.ma2026().overtimeNoComp()),
                n(s.getFamilyReductionAnnual(), PayrollRules.ma2026().familyReductionAnnual()),
                s.getMaxDependents() == null ? 6 : s.getMaxDependents(),
                brackets
        );
    }

    private BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal n(BigDecimal v, BigDecimal fallback) {
        return v == null ? fallback : v;
    }
}
