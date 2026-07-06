package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.asset.PfAccount;
import com.moneymap.model.asset.PfEmployerRecord;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EPF/VPF — parent/child structure (Section 07 §6): one UAN parent per owner,
 * multiple employer records rolled into the parent's total.
 */
@Controller
@RequestMapping("/assets/pf")
public class PfController {

    private final Db db;
    private final HouseholdMemberRepository householdMembers;

    public PfController(Db db, HouseholdMemberRepository householdMembers) {
        this.db = db;
        this.householdMembers = householdMembers;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    @GetMapping
    public String overview(HttpServletRequest request, Model model) {
        User user = user(request);
        List<PfAccount> accounts = db.pfAccounts.findWhere(a -> user.getId().equals(a.getOwnerId()));
        Map<String, List<PfEmployerRecord>> children = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PfAccount acc : accounts) {
            List<PfEmployerRecord> recs = db.pfEmployerRecords.findWhere(
                    r -> acc.getId().equals(r.getPfAccountId()));
            children.put(acc.getId(), recs);
            totals.put(acc.getId(), recs.stream()
                    .map(r -> r.getCurrentBalance() == null ? BigDecimal.ZERO : r.getCurrentBalance())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        model.addAttribute("accounts", accounts);
        model.addAttribute("children", children);
        model.addAttribute("totals", totals);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        return "assets/pf";
    }

    @PostMapping("/save-account")
    public String saveAccount(@RequestParam(required = false) String id,
                              @RequestParam String uan,
                              @RequestParam(defaultValue = "Self") String familyMemberTag,
                              HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        String trimmedUan = uan == null ? "" : uan.trim();
        if (!trimmedUan.matches("\\d{12}")) {
            ra.addFlashAttribute("error", "UAN must be exactly 12 digits.");
            return "redirect:/assets/pf";
        }
        // UAN is unique per owner (Section 07 §6 / Section 13 import rule)
        boolean duplicate = db.pfAccounts.findWhere(a -> user.getId().equals(a.getOwnerId())
                && trimmedUan.equals(a.getUan()) && (id == null || !id.equals(a.getId()))).size() > 0;
        if (duplicate) {
            ra.addFlashAttribute("error", "A PF account with this UAN already exists.");
            return "redirect:/assets/pf";
        }
        PfAccount acc = id == null || id.isBlank() ? new PfAccount()
                : db.pfAccounts.findById(id).filter(a -> user.getId().equals(a.getOwnerId())).orElse(new PfAccount());
        acc.setOwnerId(user.getId());
        acc.setUan(trimmedUan);
        acc.setFamilyMemberTag(familyMemberTag);
        db.pfAccounts.save(acc);
        ra.addFlashAttribute("success", "PF account saved.");
        return "redirect:/assets/pf";
    }

    @PostMapping("/save-employer")
    public String saveEmployer(@RequestParam(required = false) String id,
                               @RequestParam String pfAccountId,
                               @RequestParam String employerName,
                               @RequestParam String pfMemberId,
                               @RequestParam LocalDate employmentStartDate,
                               @RequestParam(required = false) LocalDate employmentEndDate,
                               @RequestParam(required = false) BigDecimal employeeContributionPerMonth,
                               @RequestParam(required = false) BigDecimal employerEpfContributionPerMonth,
                               @RequestParam(required = false) BigDecimal employerEpsContributionPerMonth,
                               @RequestParam(required = false) BigDecimal vpfContributionPerMonth,
                               @RequestParam BigDecimal currentBalance,
                               @RequestParam(required = false) BigDecimal interestRate,
                               @RequestParam(defaultValue = "ACTIVE") String status,
                               HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        PfAccount parent = db.pfAccounts.findById(pfAccountId)
                .filter(a -> user.getId().equals(a.getOwnerId())).orElse(null);
        if (parent == null) return "redirect:/assets/pf";
        PfEmployerRecord rec = id == null || id.isBlank() ? new PfEmployerRecord()
                : db.pfEmployerRecords.findById(id).filter(r -> user.getId().equals(r.getOwnerId()))
                        .orElse(new PfEmployerRecord());
        rec.setOwnerId(user.getId());
        rec.setFamilyMemberTag(parent.getFamilyMemberTag());
        rec.setPfAccountId(pfAccountId);
        rec.setEmployerName(employerName);
        rec.setPfMemberId(pfMemberId);
        rec.setEmploymentStartDate(employmentStartDate);
        rec.setEmploymentEndDate(employmentEndDate);
        rec.setEmployeeContributionPerMonth(employeeContributionPerMonth);
        rec.setEmployerEpfContributionPerMonth(employerEpfContributionPerMonth);
        rec.setEmployerEpsContributionPerMonth(employerEpsContributionPerMonth);
        rec.setVpfContributionPerMonth(vpfContributionPerMonth);
        rec.setCurrentBalance(currentBalance);
        rec.setInterestRate(interestRate);
        rec.setStatus(status);
        db.pfEmployerRecords.save(rec);
        ra.addFlashAttribute("success", "Employer record saved.");
        return "redirect:/assets/pf";
    }

    @PostMapping("/delete-account/{id}")
    public String deleteAccount(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        db.pfAccounts.findById(id).filter(a -> user.getId().equals(a.getOwnerId())).ifPresent(a -> {
            db.pfEmployerRecords.deleteWhere(r -> id.equals(r.getPfAccountId()));
            db.pfAccounts.deleteById(id);
        });
        ra.addFlashAttribute("success", "PF account and its employer records deleted.");
        return "redirect:/assets/pf";
    }

    @PostMapping("/delete-employer/{id}")
    public String deleteEmployer(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        db.pfEmployerRecords.findById(id).filter(r -> user.getId().equals(r.getOwnerId()))
                .ifPresent(r -> db.pfEmployerRecords.deleteById(id));
        ra.addFlashAttribute("success", "Employer record deleted.");
        return "redirect:/assets/pf";
    }
}
