package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.service.UserService;
import com.moneymap.service.Validators;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/** Forced password change (§1.3) and the 4-step Setup Wizard (§1.2). */
@Controller
public class OnboardingController {

    private final UserService userService;

    public OnboardingController(UserService userService) {
        this.userService = userService;
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    // ── Forced password change (§1.3) ────────────────────────────────────────

    @GetMapping("/password/force")
    public String forceChangePage(Model model) {
        model.addAttribute("passwordRule", Validators.passwordRuleText());
        return "force-password";
    }

    @PostMapping("/password/force")
    public String forceChange(@RequestParam String currentPassword,
                              @RequestParam String newPassword,
                              @RequestParam String confirmPassword,
                              HttpServletRequest request, Model model) {
        User user = currentUser(request);
        String error = userService.changePassword(user, currentPassword, newPassword, confirmPassword);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("passwordRule", Validators.passwordRuleText());
            return "force-password";
        }
        // Admin proceeds into the Setup Wizard exactly like any other new user (§1.3)
        return user.isOnboardingCompleted() ? "redirect:/dashboard" : "redirect:/setup";
    }

    // ── Setup Wizard (§1.2) ──────────────────────────────────────────────────

    @GetMapping("/setup")
    public String wizard(HttpServletRequest request, Model model) {
        User user = currentUser(request);
        if (user.isOnboardingCompleted()) return "redirect:/dashboard";
        model.addAttribute("step", user.getOnboardingStep());
        return "wizard";
    }

    @PostMapping("/setup/step1")
    public String step1(@RequestParam User.CurrencyPreference currency, HttpServletRequest request) {
        userService.wizardStep1Currency(currentUser(request), currency);
        return "redirect:/setup";
    }

    @PostMapping("/setup/step2")
    public String step2(@RequestParam User.Theme theme, HttpServletRequest request) {
        userService.wizardStep2Theme(currentUser(request), theme);
        return "redirect:/setup";
    }

    @PostMapping("/setup/step3")
    public String step3(HttpServletRequest request) {
        // "Skip for now" — Create/Join hand off to Section 03's flows (a later increment)
        userService.wizardStep3FamilySkip(currentUser(request));
        return "redirect:/setup";
    }

    @PostMapping("/setup/step4")
    public String step4(@RequestParam BigDecimal goldRate24k,
                        @RequestParam(required = false) BigDecimal goldRate22k,
                        HttpServletRequest request, Model model) {
        User user = currentUser(request);
        String error = userService.wizardStep4GoldRate(user, goldRate24k, goldRate22k);
        if (error != null) {
            model.addAttribute("step", 4);
            model.addAttribute("error", error);
            return "wizard";
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/setup/back")
    public String back(HttpServletRequest request) {
        User user = currentUser(request);
        if (user.getOnboardingStep() > 1) {
            user.setOnboardingStep(user.getOnboardingStep() - 1);
            userService.wizardStepPersist(user);
        }
        return "redirect:/setup";
    }
}
