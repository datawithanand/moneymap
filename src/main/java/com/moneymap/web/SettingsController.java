package com.moneymap.web;

import com.moneymap.model.HouseholdMember;
import com.moneymap.model.User;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.service.UserService;
import com.moneymap.service.Validators;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Map;

/** User Profile & Settings (Section 01 §4.1, §5; Section 01B rates; household members per Section 05/17 §1.7). */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UserService userService;
    private final HouseholdMemberRepository householdMembers;

    public SettingsController(UserService userService, HouseholdMemberRepository householdMembers) {
        this.userService = userService;
        this.householdMembers = householdMembers;
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    @GetMapping
    public String settings(HttpServletRequest request, Model model) {
        User user = currentUser(request);
        model.addAttribute("user", user);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        model.addAttribute("passwordRule", Validators.passwordRuleText());
        return "settings";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
                                @RequestParam String email,
                                @RequestParam(required = false) MultipartFile photo,
                                @RequestParam(required = false) String removePhoto,
                                HttpServletRequest request, RedirectAttributes ra) {
        Map<String, String> errors = userService.updateProfile(currentUser(request), fullName, email,
                photo, removePhoto != null);
        if (errors.isEmpty()) {
            ra.addFlashAttribute("success", "Profile updated.");
        } else {
            ra.addFlashAttribute("error", String.join(" ", errors.values()));
        }
        return "redirect:/settings";
    }

    /** One-click theme change from the sidebar switcher — saves and returns to the current page. */
    @PostMapping("/theme")
    public String quickTheme(@RequestParam User.Theme theme,
                             @RequestParam(required = false) String returnTo,
                             HttpServletRequest request) {
        User user = currentUser(request);
        user.setTheme(theme);
        userService.wizardStepPersist(user);
        String target = returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//")
                ? returnTo : "/dashboard";
        return "redirect:" + target;
    }

    @PostMapping("/preferences")
    public String updatePreferences(@RequestParam User.CurrencyPreference currency,
                                    @RequestParam String dateFormat,
                                    @RequestParam User.NumberFormat numberFormat,
                                    @RequestParam User.Theme theme,
                                    HttpServletRequest request, RedirectAttributes ra) {
        userService.updatePreferences(currentUser(request), currency, dateFormat, numberFormat, theme);
        ra.addFlashAttribute("success", "Preferences saved.");
        return "redirect:/settings";
    }

    @PostMapping("/notifications")
    public String updateNotifications(@RequestParam(required = false) String notifyInApp,
                                      @RequestParam(required = false) String notifyEmail,
                                      HttpServletRequest request, RedirectAttributes ra) {
        userService.updateNotificationPreferences(currentUser(request), notifyInApp != null, notifyEmail != null);
        ra.addFlashAttribute("success", "Notification preferences saved.");
        return "redirect:/settings";
    }

    @PostMapping("/rates")
    public String updateRates(@RequestParam BigDecimal goldRate24k,
                              @RequestParam BigDecimal goldRate22k,
                              @RequestParam BigDecimal usdInrRate,
                              HttpServletRequest request, RedirectAttributes ra) {
        String error = userService.updateRates(currentUser(request), goldRate24k, goldRate22k, usdInrRate);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Rates updated." : error);
        return "redirect:/settings";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpServletRequest request, RedirectAttributes ra) {
        String error = userService.changePassword(currentUser(request), currentPassword, newPassword, confirmPassword);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Password changed. Other devices have been signed out." : error);
        return "redirect:/settings";
    }

    // ── Household Members (familyMemberTag source — Section 05 opening note) ──

    @PostMapping("/household/add")
    public String addHouseholdMember(@RequestParam String label,
                                     HttpServletRequest request, RedirectAttributes ra) {
        User user = currentUser(request);
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) {
            ra.addFlashAttribute("error", "Household member name must be 2–50 characters.");
            return "redirect:/settings";
        }
        boolean duplicate = householdMembers.findByOwnerId(user.getId()).stream()
                .anyMatch(m -> m.getLabel().equalsIgnoreCase(trimmed));
        if (duplicate) {
            ra.addFlashAttribute("error", "That household member already exists.");
            return "redirect:/settings";
        }
        HouseholdMember member = new HouseholdMember();
        member.setOwnerId(user.getId());
        member.setLabel(trimmed);
        householdMembers.save(member);
        ra.addFlashAttribute("success", "Household member added.");
        return "redirect:/settings";
    }

    @PostMapping("/household/delete")
    public String deleteHouseholdMember(@RequestParam String id,
                                        HttpServletRequest request, RedirectAttributes ra) {
        User user = currentUser(request);
        householdMembers.findById(id)
                .filter(m -> m.getOwnerId().equals(user.getId()))
                .ifPresent(m -> {
                    if (m.isDefaultEntry()) return;   // "Self" cannot be deleted
                    householdMembers.deleteById(m.getId());
                });
        ra.addFlashAttribute("success", "Household member removed.");
        return "redirect:/settings";
    }

    // ── Account deletion (§5.5) ───────────────────────────────────────────────

    @PostMapping("/delete-account")
    public String deleteAccount(@RequestParam String confirmUsername,
                                @RequestParam String currentPassword,
                                HttpServletRequest request, RedirectAttributes ra) {
        String error = userService.requestDeletion(currentUser(request), confirmUsername, currentPassword);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/settings";
        }
        return "redirect:/login?loggedout=1";
    }
}
