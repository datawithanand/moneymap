package com.moneymap.web;

import com.moneymap.calculator.CalculatorMath;
import com.moneymap.model.User;
import com.moneymap.service.PortfolioAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/** Calculators landing page, plus FIRE / SIP / CAGR — pure projections, nothing persisted. */
@Controller
public class CalculatorController {

    private final PortfolioAggregationService aggregationService;

    public CalculatorController(PortfolioAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    @GetMapping("/calculators")
    public String index() {
        return "calculators/index";
    }

    // ── FIRE ─────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/fire")
    public String fireForm(HttpServletRequest request, Model model) {
        User user = user(request);
        BigDecimal netWorth = aggregationService.aggregate(user).netWorth;
        model.addAttribute("currentCorpus", netWorth);
        return "calculators/fire";
    }

    @PostMapping("/calculators/fire")
    public String fireCompute(@RequestParam BigDecimal annualExpense,
                              @RequestParam BigDecimal swrPercent,
                              @RequestParam BigDecimal currentCorpus,
                              @RequestParam BigDecimal monthlyInvestment,
                              @RequestParam BigDecimal expectedReturnPercent,
                              Model model) {
        model.addAttribute("annualExpense", annualExpense);
        model.addAttribute("swrPercent", swrPercent);
        model.addAttribute("currentCorpus", currentCorpus);
        model.addAttribute("monthlyInvestment", monthlyInvestment);
        model.addAttribute("expectedReturnPercent", expectedReturnPercent);
        model.addAttribute("result",
                CalculatorMath.fire(annualExpense, swrPercent, currentCorpus, monthlyInvestment, expectedReturnPercent));
        return "calculators/fire";
    }

    // ── SIP ──────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/sip")
    public String sipForm() {
        return "calculators/sip";
    }

    @PostMapping("/calculators/sip")
    public String sipCompute(@RequestParam BigDecimal monthlyAmount,
                             @RequestParam BigDecimal annualReturnPercent,
                             @RequestParam int years,
                             @RequestParam(required = false) BigDecimal stepUpPercent,
                             @RequestParam(required = false) Boolean inflationAdjust,
                             @RequestParam(required = false) BigDecimal inflationPercent,
                             Model model) {
        boolean adjust = Boolean.TRUE.equals(inflationAdjust);
        model.addAttribute("monthlyAmount", monthlyAmount);
        model.addAttribute("annualReturnPercent", annualReturnPercent);
        model.addAttribute("years", years);
        model.addAttribute("stepUpPercent", stepUpPercent);
        model.addAttribute("inflationAdjust", adjust);
        model.addAttribute("inflationPercent", inflationPercent);
        model.addAttribute("result",
                CalculatorMath.sip(monthlyAmount, annualReturnPercent, years, stepUpPercent, adjust, inflationPercent));
        return "calculators/sip";
    }

    // ── CAGR ─────────────────────────────────────────────────────────────────

    @GetMapping("/calculators/cagr")
    public String cagrForm() {
        return "calculators/cagr";
    }

    @PostMapping("/calculators/cagr")
    public String cagrCompute(@RequestParam BigDecimal startValue,
                              @RequestParam BigDecimal endValue,
                              @RequestParam BigDecimal years,
                              Model model) {
        model.addAttribute("startValue", startValue);
        model.addAttribute("endValue", endValue);
        model.addAttribute("years", years);
        model.addAttribute("result", CalculatorMath.cagr(startValue, endValue, years));
        return "calculators/cagr";
    }
}
