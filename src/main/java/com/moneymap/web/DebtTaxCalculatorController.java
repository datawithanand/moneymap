package com.moneymap.web;

import com.moneymap.calculator.CalculatorMath;
import com.moneymap.calculator.CalculatorValidation.ValidationException;
import com.moneymap.model.TaxSlabSet;
import com.moneymap.service.TaxService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Optional;

import static com.moneymap.calculator.CalculatorValidation.*;

/** Loan/EMI (+ prepayment simulation) and Old-vs-New tax regime comparison. */
@Controller
public class DebtTaxCalculatorController {

    private final TaxService taxService;

    public DebtTaxCalculatorController(TaxService taxService) {
        this.taxService = taxService;
    }

    // ── Loan / EMI ───────────────────────────────────────────────────────────

    @GetMapping("/calculators/loan")
    public String loanForm() {
        return "calculators/loan";
    }

    @PostMapping("/calculators/loan")
    public String loanCompute(@RequestParam BigDecimal principal,
                              @RequestParam BigDecimal annualRatePercent,
                              @RequestParam int tenureMonths,
                              @RequestParam(required = false) Boolean simulatePrepayment,
                              @RequestParam(required = false) BigDecimal prepaymentAmount,
                              @RequestParam(required = false) Integer prepaymentMonth,
                              @RequestParam(required = false) String prepaymentMode,
                              Model model) {
        model.addAttribute("principal", principal);
        model.addAttribute("annualRatePercent", annualRatePercent);
        model.addAttribute("tenureMonths", tenureMonths);

        boolean doPrepay = Boolean.TRUE.equals(simulatePrepayment)
                && prepaymentAmount != null && prepaymentMonth != null && prepaymentMode != null;
        model.addAttribute("simulatePrepayment", doPrepay);
        try {
            check(positive(principal, "Loan amount"),
                    nonNegative(annualRatePercent, "Annual interest rate %"),
                    range(tenureMonths, 1, 600, "Tenure (months)"));
            model.addAttribute("result", CalculatorMath.loan(principal, annualRatePercent, tenureMonths));

            if (doPrepay) {
                check(positive(prepaymentAmount, "Prepayment amount"),
                        range(prepaymentMonth, 1, tenureMonths, "Prepayment month"));
                model.addAttribute("prepaymentAmount", prepaymentAmount);
                model.addAttribute("prepaymentMonth", prepaymentMonth);
                model.addAttribute("prepaymentMode", prepaymentMode);
                model.addAttribute("prepaymentResult", CalculatorMath.prepay(principal, annualRatePercent, tenureMonths,
                        prepaymentAmount, prepaymentMonth, prepaymentMode));
            }
        } catch (ValidationException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "calculators/loan";
    }

    // ── Tax regime ───────────────────────────────────────────────────────────

    public record TaxRegimeResult(BigDecimal taxOld, BigDecimal taxNew, String betterRegime, BigDecimal savings) {}

    @GetMapping("/calculators/tax-regime")
    public String taxRegimeForm() {
        return "calculators/tax-regime";
    }

    @PostMapping("/calculators/tax-regime")
    public String taxRegimeCompute(@RequestParam String financialYear,
                                   @RequestParam BigDecimal grossIncome,
                                   @RequestParam(required = false) BigDecimal oldRegimeDeductions,
                                   Model model) {
        model.addAttribute("financialYear", financialYear);
        model.addAttribute("grossIncome", grossIncome);
        model.addAttribute("oldRegimeDeductions", oldRegimeDeductions);

        String validationError = firstOf(nonNegative(grossIncome, "Gross annual income"),
                nonNegative(oldRegimeDeductions, "Old-regime deductions"));
        if (validationError != null) {
            model.addAttribute("error", validationError);
            return "calculators/tax-regime";
        }

        Optional<TaxSlabSet> oldSet = taxService.findSet(financialYear, "OLD");
        Optional<TaxSlabSet> newSet = taxService.findSet(financialYear, "NEW");
        if (oldSet.isEmpty() || newSet.isEmpty()) {
            model.addAttribute("error", "No tax slabs configured for financial year " + financialYear + ".");
            return "calculators/tax-regime";
        }

        BigDecimal deductions = oldRegimeDeductions == null ? BigDecimal.ZERO : oldRegimeDeductions;
        BigDecimal oldStdDeduction = oldSet.get().getStandardDeduction() == null ? BigDecimal.ZERO : oldSet.get().getStandardDeduction();
        BigDecimal newStdDeduction = newSet.get().getStandardDeduction() == null ? BigDecimal.ZERO : newSet.get().getStandardDeduction();

        BigDecimal taxableOld = grossIncome.subtract(oldStdDeduction).subtract(deductions).max(BigDecimal.ZERO);
        BigDecimal taxableNew = grossIncome.subtract(newStdDeduction).max(BigDecimal.ZERO);

        BigDecimal taxOld = taxService.computeTax(taxableOld, oldSet.get());
        BigDecimal taxNew = taxService.computeTax(taxableNew, newSet.get());
        String better = taxOld.compareTo(taxNew) <= 0 ? "OLD" : "NEW";
        BigDecimal savings = taxOld.subtract(taxNew).abs();

        model.addAttribute("result", new TaxRegimeResult(taxOld, taxNew, better, savings));
        return "calculators/tax-regime";
    }
}
