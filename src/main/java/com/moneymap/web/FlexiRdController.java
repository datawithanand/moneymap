package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.asset.FlexiRd;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** iWISH / Flexi RD — custom form with an irregular-deposit ledger (Section 05 §5). */
@Controller
@RequestMapping("/assets/flexi-rds")
public class FlexiRdController {

    private final Db db;
    private final HouseholdMemberRepository householdMembers;

    public FlexiRdController(Db db, HouseholdMemberRepository householdMembers) {
        this.db = db;
        this.householdMembers = householdMembers;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    @GetMapping("/new")
    public String createForm(HttpServletRequest request, Model model) {
        model.addAttribute("record", new FlexiRd());
        model.addAttribute("household", householdMembers.findByOwnerId(user(request).getId()));
        return "assets/flexi-rd-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, HttpServletRequest request, Model model) {
        User user = user(request);
        FlexiRd record = db.flexiRds.findById(id)
                .filter(r -> user.getId().equals(r.getOwnerId())).orElse(null);
        if (record == null) return "redirect:/assets/flexi-rds";
        model.addAttribute("record", record);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        return "assets/flexi-rd-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String bankName,
                       @RequestParam(required = false) String accountNumber,
                       @RequestParam(required = false) LocalDate targetMaturityDate,
                       @RequestParam(required = false) BigDecimal interestRate,
                       @RequestParam(required = false) BigDecimal currentValueOverride,
                       @RequestParam(defaultValue = "INR") String currency,
                       @RequestParam(defaultValue = "Self") String familyMemberTag,
                       @RequestParam(name = "depositDate", required = false) List<LocalDate> depositDates,
                       @RequestParam(name = "depositAmount", required = false) List<BigDecimal> depositAmounts,
                       HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        FlexiRd record = id == null || id.isBlank() ? new FlexiRd()
                : db.flexiRds.findById(id).filter(r -> user.getId().equals(r.getOwnerId())).orElse(new FlexiRd());
        record.setOwnerId(user.getId());
        record.setBankName(bankName);
        record.setAccountNumber(accountNumber);
        record.setTargetMaturityDate(targetMaturityDate);
        record.setInterestRate(interestRate);
        record.setCurrentValueOverride(currentValueOverride);
        record.setCurrency(currency);
        record.setFamilyMemberTag(familyMemberTag);
        List<FlexiRd.FlexiDeposit> deposits = new ArrayList<>();
        if (depositDates != null && depositAmounts != null) {
            for (int i = 0; i < Math.min(depositDates.size(), depositAmounts.size()); i++) {
                if (depositDates.get(i) == null || depositAmounts.get(i) == null) continue;
                FlexiRd.FlexiDeposit d = new FlexiRd.FlexiDeposit();
                d.setDepositDate(depositDates.get(i));
                d.setAmount(depositAmounts.get(i));
                deposits.add(d);
            }
        }
        record.setDeposits(deposits);
        db.flexiRds.save(record);
        ra.addFlashAttribute("success", "Flexi RD saved.");
        return "redirect:/assets/flexi-rds";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        db.flexiRds.findById(id).filter(r -> user.getId().equals(r.getOwnerId()))
                .ifPresent(r -> db.flexiRds.deleteById(id));
        ra.addFlashAttribute("success", "Record deleted.");
        return "redirect:/assets/flexi-rds";
    }
}
