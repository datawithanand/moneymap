package com.moneymap.web;

import com.moneymap.model.TaxSlabSet;
import com.moneymap.model.User;
import com.moneymap.model.asset.SalaryProfile;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.service.TaxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Salary Profile & Tax Engine (Section 11A §24) — dynamic earning/deduction rows + tax preview. */
@Controller
@RequestMapping("/assets/salary")
public class SalaryController {

    private final Db db;
    private final TaxService taxService;
    private final HouseholdMemberRepository householdMembers;

    public SalaryController(Db db, TaxService taxService, HouseholdMemberRepository householdMembers) {
        this.db = db;
        this.taxService = taxService;
        this.householdMembers = householdMembers;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    @GetMapping("/new")
    public String createForm(HttpServletRequest request, Model model) {
        SalaryProfile p = new SalaryProfile();
        p.getEarnings().add(new SalaryProfile.PayComponent("Basic", null));
        p.getEarnings().add(new SalaryProfile.PayComponent("HRA", null));
        p.getDeductions().add(new SalaryProfile.PayComponent("Employee PF", null));
        model.addAttribute("record", p);
        model.addAttribute("household", householdMembers.findByOwnerId(user(request).getId()));
        return "assets/salary-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, HttpServletRequest request, Model model) {
        User user = user(request);
        SalaryProfile record = db.salaryProfiles.findById(id)
                .filter(r -> user.getId().equals(r.getOwnerId())).orElse(null);
        if (record == null) return "redirect:/assets/salary";
        model.addAttribute("record", record);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        TaxSlabSet set = taxService.findSet(record.getFinancialYear(), record.getRegime()).orElse(null);
        if (set != null) {
            BigDecimal grossAnnual = record.grossMonthly().multiply(BigDecimal.valueOf(12));
            BigDecimal taxable = grossAnnual.subtract(set.getStandardDeduction() == null
                    ? BigDecimal.ZERO : set.getStandardDeduction()).max(BigDecimal.ZERO);
            model.addAttribute("grossAnnual", grossAnnual);
            model.addAttribute("taxableIncome", taxable);
            model.addAttribute("estimatedTax", taxService.computeTax(taxable, set));
        }
        return "assets/salary-form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String id,
                       @RequestParam String employerName,
                       @RequestParam(required = false) String designation,
                       @RequestParam LocalDate effectiveDate,
                       @RequestParam String financialYear,
                       @RequestParam String regime,
                       @RequestParam(defaultValue = "Self") String familyMemberTag,
                       @RequestParam(name = "earnLabel", required = false) List<String> earnLabels,
                       @RequestParam(name = "earnAmount", required = false) List<BigDecimal> earnAmounts,
                       @RequestParam(name = "dedLabel", required = false) List<String> dedLabels,
                       @RequestParam(name = "dedAmount", required = false) List<BigDecimal> dedAmounts,
                       HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        SalaryProfile record = id == null || id.isBlank() ? new SalaryProfile()
                : db.salaryProfiles.findById(id).filter(r -> user.getId().equals(r.getOwnerId()))
                        .orElse(new SalaryProfile());
        record.setOwnerId(user.getId());
        record.setEmployerName(employerName);
        record.setDesignation(designation);
        record.setEffectiveDate(effectiveDate);
        record.setFinancialYear(financialYear);
        record.setRegime(regime);
        record.setFamilyMemberTag(familyMemberTag);
        record.setEarnings(components(earnLabels, earnAmounts));
        record.setDeductions(components(dedLabels, dedAmounts));
        db.salaryProfiles.save(record);
        ra.addFlashAttribute("success", "Salary profile saved.");
        return "redirect:/assets/salary";
    }

    private List<SalaryProfile.PayComponent> components(List<String> labels, List<BigDecimal> amounts) {
        List<SalaryProfile.PayComponent> out = new ArrayList<>();
        if (labels == null || amounts == null) return out;
        for (int i = 0; i < Math.min(labels.size(), amounts.size()); i++) {
            if (labels.get(i) == null || labels.get(i).isBlank() || amounts.get(i) == null) continue;
            out.add(new SalaryProfile.PayComponent(labels.get(i).trim(), amounts.get(i)));
        }
        return out;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        User user = user(request);
        db.salaryProfiles.findById(id).filter(r -> user.getId().equals(r.getOwnerId()))
                .ifPresent(r -> db.salaryProfiles.deleteById(id));
        ra.addFlashAttribute("success", "Salary profile deleted.");
        return "redirect:/assets/salary";
    }
}
