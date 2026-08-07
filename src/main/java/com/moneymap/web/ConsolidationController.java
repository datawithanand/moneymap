package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.expense.ExpenseEntry;
import com.moneymap.module.Valuation;
import com.moneymap.repository.Db;
import com.moneymap.service.PortfolioAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Multi-account/currency consolidation. Asset/liability modules already convert every
 * native-currency value into the owner's base currency via Valuation.convert + the manually
 * set USD/INR rate (PortfolioAggregationService) — this endpoint reuses that same conversion
 * for the standalone expense-tracking module and reports everything together as one
 * base-currency view.
 */
@RestController
public class ConsolidationController {

    public record ConsolidatedView(BigDecimal netWorth, BigDecimal totalAssets, BigDecimal totalLiabilities,
                                   BigDecimal totalIncomeBaseCurrency, BigDecimal totalExpenseBaseCurrency,
                                   String baseCurrency) {}

    private final Db db;
    private final PortfolioAggregationService aggregationService;

    public ConsolidationController(Db db, PortfolioAggregationService aggregationService) {
        this.db = db;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/api/consolidation")
    public ConsolidatedView consolidated(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        var portfolio = aggregationService.aggregate(user);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (ExpenseEntry e : db.expenseEntries.findWhere(x -> user.getId().equals(x.getOwnerId()))) {
            BigDecimal converted = Valuation.convert(Valuation.nz(e.getAmount()), e.getCurrency(), user);
            if ("INCOME".equals(e.getDirection())) income = income.add(converted);
            else expense = expense.add(converted);
        }

        return new ConsolidatedView(portfolio.netWorth, portfolio.totalAssets, portfolio.totalLiabilities,
                income, expense, user.getCurrencyPreference().name());
    }
}
