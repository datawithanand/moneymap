package com.moneymap.web;

import com.moneymap.model.AuditLogEntry;
import com.moneymap.model.GlobalSettings;
import com.moneymap.model.InviteToken;
import com.moneymap.model.User;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.AdminService;
import com.moneymap.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Admin panel: user management, global settings, audit log (Section 01 §6, §7.2). */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository users;
    private final AdminService adminService;
    private final AuditService auditService;

    public AdminController(UserRepository users, AdminService adminService, AuditService auditService) {
        this.users = users;
        this.adminService = adminService;
        this.auditService = auditService;
    }

    private User currentAdmin(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    // ── User management (§6.1) ───────────────────────────────────────────────

    @GetMapping("/users")
    public String userList(@RequestParam(required = false) String q, Model model) {
        List<User> all = users.findAll().stream()
                .filter(u -> q == null || q.isBlank()
                        || contains(u.getFullName(), q) || contains(u.getUsername(), q) || contains(u.getEmail(), q))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
        model.addAttribute("users", all);
        model.addAttribute("q", q);
        return "admin/users";
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q.toLowerCase());
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String fullName, @RequestParam String username,
                             @RequestParam String email, @RequestParam(defaultValue = "REGULAR") User.Role role,
                             HttpServletRequest request, Model model, RedirectAttributes ra) {
        StringBuilder createdUserId = new StringBuilder();
        StringBuilder tempPassword = new StringBuilder();
        var errors = adminService.createUser(currentAdmin(request), fullName, username, email, role,
                createdUserId, tempPassword);
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("error", String.join(" ", errors.values()));
            return "redirect:/admin/users";
        }
        User target = users.findById(createdUserId.toString()).orElseThrow();
        model.addAttribute("target", target);
        model.addAttribute("temporaryPassword", tempPassword.toString());
        return "admin/reset-result";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable String id, HttpServletRequest request, Model model,
                                RedirectAttributes ra) {
        User admin = currentAdmin(request);
        User target = users.findById(id).orElse(null);
        if (target == null || target.getId().equals(admin.getId())) {
            ra.addFlashAttribute("error", "Password reset is not available for this account.");
            return "redirect:/admin/users";
        }
        String temp = adminService.resetPassword(admin, target);
        // One-time reveal (§4.2): shown exactly once, never persisted in plaintext.
        model.addAttribute("target", target);
        model.addAttribute("temporaryPassword", temp);
        return "admin/reset-result";
    }

    @PostMapping("/users/{id}/disable")
    public String disable(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        User target = users.findById(id).orElse(null);
        if (target != null) {
            String error = adminService.disable(currentAdmin(request), target);
            ra.addFlashAttribute(error == null ? "success" : "error",
                    error == null ? "Account disabled." : error);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/enable")
    public String enable(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        users.findById(id).ifPresent(target -> adminService.enable(currentAdmin(request), target));
        ra.addFlashAttribute("success", "Account enabled.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/restore")
    public String restore(@PathVariable String id, HttpServletRequest request, RedirectAttributes ra) {
        users.findById(id)
                .filter(u -> u.getStatus() == User.Status.PENDING_DELETION)
                .ifPresent(target -> adminService.restore(currentAdmin(request), target));
        ra.addFlashAttribute("success", "Account restored.");
        return "redirect:/admin/users";
    }

    // ── Audited portfolio view (§6.3) ────────────────────────────────────────

    @GetMapping("/users/{id}/view")
    public String viewPortfolioConfirm(@PathVariable String id, Model model) {
        User target = users.findById(id).orElse(null);
        if (target == null) return "redirect:/admin/users";
        model.addAttribute("target", target);
        return "admin/view-confirm";
    }

    @PostMapping("/users/{id}/view")
    public String viewPortfolioEnter(@PathVariable String id, HttpServletRequest request) {
        User admin = currentAdmin(request);
        User target = users.findById(id).orElse(null);
        if (target == null) return "redirect:/admin/users";
        // Logged at the moment of confirmation — the access grant itself is the sensitive event (§6.3)
        adminService.logPortfolioView(admin, target);
        HttpSession session = request.getSession(false);
        session.setAttribute(SessionKeys.ADMIN_VIEWING_USER_ID, target.getId());
        session.setAttribute(SessionKeys.ADMIN_VIEW_ENTERED_AT, Instant.now());
        return "redirect:/dashboard";
    }

    @PostMapping("/view/exit")
    public String exitView(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SessionKeys.ADMIN_VIEWING_USER_ID);
            session.removeAttribute(SessionKeys.ADMIN_VIEW_ENTERED_AT);
        }
        return "redirect:/admin/users";
    }

    // ── Storage usage (§6.5) ─────────────────────────────────────────────────

    @GetMapping("/users/{id}/storage")
    public String storage(@PathVariable String id, Model model) {
        User target = users.findById(id).orElse(null);
        if (target == null) return "redirect:/admin/users";
        model.addAttribute("target", target);
        model.addAttribute("breakdown", adminService.storageBreakdown(target));
        return "admin/storage";
    }

    // ── Global settings (§6.6) ───────────────────────────────────────────────

    @GetMapping("/settings")
    public String settingsPage(Model model, HttpServletRequest request) {
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam int sessionTimeoutMinutes,
                               @RequestParam int maxFailedLoginAttempts,
                               @RequestParam int lockoutDurationMinutes,
                               @RequestParam GlobalSettings.RegistrationMode registrationMode,
                               @RequestParam String instanceName,
                               HttpServletRequest request, RedirectAttributes ra) {
        String error = adminService.updateGlobalSettings(currentAdmin(request), sessionTimeoutMinutes,
                maxFailedLoginAttempts, lockoutDurationMinutes, registrationMode, instanceName);
        ra.addFlashAttribute(error == null ? "success" : "error",
                error == null ? "Settings saved." : error);
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/invite")
    public String generateInvite(HttpServletRequest request, RedirectAttributes ra) {
        InviteToken token = adminService.generateInvite(currentAdmin(request));
        ra.addFlashAttribute("inviteLink", "/register?invite=" + token.getToken());
        return "redirect:/admin/settings";
    }

    // ── Audit log (§7.2) ─────────────────────────────────────────────────────

    @GetMapping("/audit")
    public String auditLog(@RequestParam(required = false) String actionType,
                           @RequestParam(required = false) String q,
                           Model model) {
        model.addAttribute("entries", filteredAudit(actionType, q));
        model.addAttribute("actionTypes", AuditLogEntry.ActionType.values());
        model.addAttribute("selectedType", actionType);
        model.addAttribute("q", q);
        return "admin/audit";
    }

    @GetMapping("/audit/export")
    public void exportCsv(@RequestParam(required = false) String actionType,
                          @RequestParam(required = false) String q,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-log.csv\"");
        try (PrintWriter out = response.getWriter()) {
            out.println("id,timestamp,actorUserId,actorUsername,actionType,targetUserId,targetUsername,details");
            for (AuditLogEntry e : filteredAudit(actionType, q)) {
                out.println(String.join(",",
                        csv(e.getId()), csv(String.valueOf(e.getTimestamp())), csv(e.getActorUserId()),
                        csv(e.getActorUsername()), csv(String.valueOf(e.getActionType())),
                        csv(e.getTargetUserId()), csv(e.getTargetUsername()), csv(e.getDetails())));
            }
        }
    }

    private List<AuditLogEntry> filteredAudit(String actionType, String q) {
        return auditService.findAll().stream()
                .filter(e -> actionType == null || actionType.isBlank()
                        || String.valueOf(e.getActionType()).equals(actionType))
                .filter(e -> q == null || q.isBlank()
                        || contains(e.getActorUsername(), q) || contains(e.getTargetUsername(), q))
                .toList();
    }

    private String csv(String v) {
        if (v == null) return "";
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
