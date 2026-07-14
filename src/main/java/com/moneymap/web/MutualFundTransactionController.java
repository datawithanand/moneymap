package com.moneymap.web;

import com.moneymap.model.MutualFundTransaction;
import com.moneymap.model.User;
import com.moneymap.model.asset.MutualFund;
import com.moneymap.module.Valuation;
import com.moneymap.repository.Db;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * BUY/SELL transaction ledger for a single Mutual Fund holding — the cash-flow history XIRR
 * (Section 12) is computed from. Kept separate from AssetController's generic asset CRUD since
 * it's a nested resource scoped to one MutualFund record, not a standalone module.
 */
@Controller
@RequestMapping("/assets/mutual-funds/{fundId}/transactions")
public class MutualFundTransactionController {

    private final Db db;

    public MutualFundTransactionController(Db db) {
        this.db = db;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private MutualFund fundOrThrow(String fundId, User user) {
        MutualFund fund = db.mutualFunds.findById(fundId)
                .filter(f -> user.getId().equals(f.getOwnerId()))
                .orElse(null);
        if (fund == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return fund;
    }

    @GetMapping
    public String list(@PathVariable String fundId, HttpServletRequest request, Model model) {
        User user = user(request);
        MutualFund fund = fundOrThrow(fundId, user);
        List<MutualFundTransaction> transactions = db.mutualFundTransactions
                .findWhere(t -> fundId.equals(t.getMutualFundId()))
                .stream().sorted(Comparator.comparing(MutualFundTransaction::getDate)).toList();
        model.addAttribute("fund", fund);
        model.addAttribute("transactions", transactions);
        model.addAttribute("xirr", Valuation.mfXirr(fund, transactions));
        model.addAttribute("currentValue", Valuation.mfCurrentValue(fund));
        model.addAttribute("today", LocalDate.now());
        return "assets/mutual-fund-transactions";
    }

    @PostMapping("/save")
    public String save(@PathVariable String fundId,
                       @RequestParam MutualFundTransaction.Type type,
                       @RequestParam LocalDate date,
                       @RequestParam BigDecimal amount,
                       @RequestParam(required = false) BigDecimal units,
                       HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        fundOrThrow(fundId, user);
        if (amount.signum() <= 0) {
            ra.addFlashAttribute("error", "Amount must be greater than zero.");
            return "redirect:/assets/mutual-funds/" + fundId + "/transactions";
        }
        if (date.isAfter(LocalDate.now())) {
            ra.addFlashAttribute("error", "Transaction date cannot be in the future.");
            return "redirect:/assets/mutual-funds/" + fundId + "/transactions";
        }
        MutualFundTransaction t = new MutualFundTransaction();
        t.setOwnerId(user.getId());
        t.setMutualFundId(fundId);
        t.setType(type);
        t.setDate(date);
        t.setAmount(amount);
        t.setUnits(units);
        db.mutualFundTransactions.save(t);
        ra.addFlashAttribute("success", "Transaction added.");
        return "redirect:/assets/mutual-funds/" + fundId + "/transactions";
    }

    @PostMapping("/{txId}/delete")
    public String delete(@PathVariable String fundId, @PathVariable String txId,
                         HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        fundOrThrow(fundId, user);
        db.mutualFundTransactions.deleteWhere(t -> txId.equals(t.getId())
                && fundId.equals(t.getMutualFundId()) && user.getId().equals(t.getOwnerId()));
        ra.addFlashAttribute("success", "Transaction deleted.");
        return "redirect:/assets/mutual-funds/" + fundId + "/transactions";
    }
}
