package com.moneymap.web;

import com.moneymap.calculator.CalculatorMath;
import com.moneymap.model.User;
import com.moneymap.model.asset.NpsAccount;
import com.moneymap.model.asset.PpfAccount;
import com.moneymap.repository.Db;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/** SWP, NPS projector, FD (forward + reverse), PPF projector — pure projections, nothing persisted. */
@Controller
public class FixedIncomeCalculatorController {

    private final Db db;

    public FixedIncomeCalculatorController(Db db) {
        this.db = db;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    // ── SWP ──────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/swp")
    public String swpForm() {
        return "calculators/swp";
    }

    @PostMapping("/calculators/swp")
    public String swpCompute(@RequestParam BigDecimal corpus,
                             @RequestParam BigDecimal monthlyWithdrawal,
                             @RequestParam BigDecimal annualReturnPercent,
                             Model model) {
        model.addAttribute("corpus", corpus);
        model.addAttribute("monthlyWithdrawal", monthlyWithdrawal);
        model.addAttribute("annualReturnPercent", annualReturnPercent);
        model.addAttribute("result", CalculatorMath.swp(corpus, monthlyWithdrawal, annualReturnPercent));
        return "calculators/swp";
    }

    // ── NPS ──────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/nps")
    public String npsForm(HttpServletRequest request, Model model) {
        User user = user(request);
        List<NpsAccount> accounts = db.npsAccounts.findWhere(r -> user.getId().equals(r.getOwnerId()));
        BigDecimal currentCorpus = accounts.stream()
                .map(a -> a.getCurrentCorpus() == null ? BigDecimal.ZERO : a.getCurrentCorpus())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("currentCorpus", currentCorpus);
        model.addAttribute("npsAccountCount", accounts.size());
        return "calculators/nps";
    }

    @PostMapping("/calculators/nps")
    public String npsCompute(@RequestParam BigDecimal currentCorpus,
                             @RequestParam BigDecimal monthlyContribution,
                             @RequestParam BigDecimal annualReturnPercent,
                             @RequestParam int yearsToRetirement,
                             @RequestParam BigDecimal mandatoryAnnuityPercent,
                             Model model) {
        model.addAttribute("currentCorpus", currentCorpus);
        model.addAttribute("monthlyContribution", monthlyContribution);
        model.addAttribute("annualReturnPercent", annualReturnPercent);
        model.addAttribute("yearsToRetirement", yearsToRetirement);
        model.addAttribute("mandatoryAnnuityPercent", mandatoryAnnuityPercent);
        model.addAttribute("result", CalculatorMath.nps(currentCorpus, monthlyContribution, annualReturnPercent,
                yearsToRetirement, mandatoryAnnuityPercent));
        return "calculators/nps";
    }

    // ── FD ───────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/fd")
    public String fdForm() {
        return "calculators/fd";
    }

    @PostMapping("/calculators/fd")
    public String fdCompute(@RequestParam String mode,
                            @RequestParam(required = false) BigDecimal principal,
                            @RequestParam(required = false) BigDecimal targetMaturityValue,
                            @RequestParam BigDecimal annualRatePercent,
                            @RequestParam BigDecimal years,
                            Model model) {
        model.addAttribute("mode", mode);
        model.addAttribute("principal", principal);
        model.addAttribute("targetMaturityValue", targetMaturityValue);
        model.addAttribute("annualRatePercent", annualRatePercent);
        model.addAttribute("years", years);
        if ("REVERSE".equals(mode)) {
            model.addAttribute("reverseResult", CalculatorMath.fdReverse(targetMaturityValue, annualRatePercent, years));
        } else {
            model.addAttribute("forwardResult", CalculatorMath.fdForward(principal, annualRatePercent, years));
        }
        return "calculators/fd";
    }

    // ── PPF ──────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/ppf")
    public String ppfForm(HttpServletRequest request, Model model) {
        User user = user(request);
        List<PpfAccount> accounts = db.ppfAccounts.findWhere(r -> user.getId().equals(r.getOwnerId()));
        if (accounts.size() == 1) {
            PpfAccount only = accounts.get(0);
            model.addAttribute("openingBalance", only.getCurrentBalance());
            model.addAttribute("annualContribution", only.getYearlyContribution());
            model.addAttribute("annualRatePercent", only.getInterestRate());
            model.addAttribute("prefilled", true);
        }
        return "calculators/ppf";
    }

    @PostMapping("/calculators/ppf")
    public String ppfCompute(@RequestParam(required = false) BigDecimal openingBalance,
                             @RequestParam BigDecimal annualContribution,
                             @RequestParam BigDecimal annualRatePercent,
                             @RequestParam int years,
                             Model model) {
        model.addAttribute("openingBalance", openingBalance);
        model.addAttribute("annualContribution", annualContribution);
        model.addAttribute("annualRatePercent", annualRatePercent);
        model.addAttribute("years", years);
        model.addAttribute("result", CalculatorMath.ppf(openingBalance, annualContribution, annualRatePercent, years));
        return "calculators/ppf";
    }
}
