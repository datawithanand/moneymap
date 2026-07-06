package com.moneymap.web;

import com.moneymap.model.GlobalSettings;
import com.moneymap.model.TaxSlabSet;
import com.moneymap.model.User;
import com.moneymap.model.AuditLogEntry;
import com.moneymap.repository.Db;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.JsonCollectionStore;
import com.moneymap.repository.LoginAttemptRepository;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.AuditService;
import com.moneymap.service.CryptoService;
import com.moneymap.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Admin extras: System Health (Section 02), SMTP/defaults (Section 01B), Tax Slab editor (Section 11A §d). */
@Controller
@RequestMapping("/admin")
public class AdminExtraController {

    private static final Instant STARTED_AT = Instant.now();

    private final Db db;
    private final UserRepository users;
    private final GlobalSettingsRepository globalSettings;
    private final LoginAttemptRepository loginAttempts;
    private final JsonCollectionStore store;
    private final CryptoService crypto;
    private final EmailService email;
    private final AuditService audit;

    public AdminExtraController(Db db, UserRepository users, GlobalSettingsRepository globalSettings,
                                LoginAttemptRepository loginAttempts, JsonCollectionStore store,
                                CryptoService crypto, EmailService email, AuditService audit) {
        this.db = db;
        this.users = users;
        this.globalSettings = globalSettings;
        this.loginAttempts = loginAttempts;
        this.store = store;
        this.crypto = crypto;
        this.email = email;
        this.audit = audit;
    }

    private User admin(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    // ── System Health (Section 02) ───────────────────────────────────────────

    @GetMapping("/health")
    public String health(Model model) throws IOException {
        model.addAttribute("userCount", users.count());
        model.addAttribute("failedLogins24h",
                loginAttempts.findSince(Instant.now().minus(Duration.ofHours(24))).size());
        long bytes = 0;
        try (var stream = Files.walk(store.getDataDir())) {
            bytes = stream.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        }
        model.addAttribute("dataDirSize", String.format("%.1f MB", bytes / (1024.0 * 1024.0)));
        Duration up = Duration.between(STARTED_AT, Instant.now());
        model.addAttribute("uptime", up.toDays() + "d " + up.toHoursPart() + "h " + up.toMinutesPart() + "m");
        model.addAttribute("lastBackupMarkedAt", globalSettings.get().getLastBackupMarkedAt());
        return "admin/health";
    }

    @PostMapping("/health/mark-backup")
    public String markBackup(RedirectAttributes ra) {
        globalSettings.update(s -> { s.setLastBackupMarkedAt(Instant.now()); return s; });
        ra.addFlashAttribute("success", "Backup marked as taken.");
        return "redirect:/admin/health";
    }

    // ── SMTP + defaults for new users (Section 01B) ─────────────────────────

    @PostMapping("/settings/smtp")
    public String saveSmtp(@RequestParam(required = false) String smtpEnabled,
                           @RequestParam(required = false) String smtpHost,
                           @RequestParam(required = false) Integer smtpPort,
                           @RequestParam(required = false) String smtpUsername,
                           @RequestParam(required = false) String smtpPassword,
                           @RequestParam(required = false) String smtpFromAddress,
                           @RequestParam User.CurrencyPreference defaultCurrency,
                           @RequestParam User.Theme defaultTheme,
                           HttpServletRequest request, RedirectAttributes ra) {
        boolean enabled = smtpEnabled != null;
        if (enabled) {
            if (smtpHost == null || smtpHost.isBlank() || smtpPort == null
                    || smtpFromAddress == null || smtpFromAddress.isBlank()) {
                ra.addFlashAttribute("error", "SMTP host, port and from-address are required when SMTP is enabled.");
                return "redirect:/admin/settings";
            }
            if (smtpPassword != null && !smtpPassword.isBlank() && !crypto.isConfigured()) {
                ra.addFlashAttribute("error", "Set the MONEYMAP_ENCRYPTION_KEY environment variable (min 32 chars) "
                        + "before storing an SMTP password — it is stored reversibly encrypted, never hashed.");
                return "redirect:/admin/settings";
            }
        }
        globalSettings.update(s -> {
            s.setSmtpEnabled(enabled);
            s.setSmtpHost(trim(smtpHost));
            s.setSmtpPort(smtpPort);
            s.setSmtpUsername(trim(smtpUsername));
            if (smtpPassword != null && !smtpPassword.isBlank()) {
                s.setSmtpPasswordEncrypted(crypto.encrypt(smtpPassword));
            }
            s.setSmtpFromAddress(trim(smtpFromAddress));
            s.setDefaultCurrency(defaultCurrency);
            s.setDefaultTheme(defaultTheme);
            return s;
        });
        audit.log(admin(request), AuditLogEntry.ActionType.SETTINGS_CHANGED, null,
                "SMTP/default settings updated (smtpEnabled=" + enabled + ")");
        ra.addFlashAttribute("success", "Email & defaults saved.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/smtp-test")
    public String testEmail(RedirectAttributes ra) {
        GlobalSettings s = globalSettings.get();
        boolean ok = email.send(s.getSmtpFromAddress(), "MoneyMap test email",
                "This is a test message confirming your MoneyMap SMTP configuration works.");
        ra.addFlashAttribute(ok ? "success" : "error",
                ok ? "Test email sent to " + s.getSmtpFromAddress() + "."
                   : "Test email failed — check the SMTP settings and application logs.");
        return "redirect:/admin/settings";
    }

    private String trim(String s) { return s == null ? null : s.trim(); }

    // ── Tax Slab editor (Section 11A §d) ─────────────────────────────────────

    @GetMapping("/tax-slabs")
    public String taxSlabs(Model model) {
        model.addAttribute("sets", db.taxSlabSets.findAll());
        return "admin/tax-slabs";
    }

    @PostMapping("/tax-slabs/save")
    public String saveTaxSlabs(@RequestParam(required = false) String id,
                               @RequestParam String financialYear,
                               @RequestParam String regime,
                               @RequestParam BigDecimal standardDeduction,
                               @RequestParam BigDecimal rebate87AThreshold,
                               @RequestParam BigDecimal rebate87AMaxAmount,
                               @RequestParam BigDecimal cessPercent,
                               @RequestParam(name = "slabUpTo", required = false) List<String> slabUpTos,
                               @RequestParam(name = "slabRate", required = false) List<BigDecimal> slabRates,
                               RedirectAttributes ra) {
        TaxSlabSet set = id == null || id.isBlank()
                ? new TaxSlabSet() : db.taxSlabSets.findById(id).orElseGet(TaxSlabSet::new);
        set.setFinancialYear(financialYear.trim());
        set.setRegime(regime);
        set.setStandardDeduction(standardDeduction);
        set.setRebate87AThreshold(rebate87AThreshold);
        set.setRebate87AMaxAmount(rebate87AMaxAmount);
        set.setCessPercent(cessPercent);
        List<TaxSlabSet.TaxSlab> slabs = new ArrayList<>();
        if (slabUpTos != null && slabRates != null) {
            for (int i = 0; i < Math.min(slabUpTos.size(), slabRates.size()); i++) {
                if (slabRates.get(i) == null) continue;
                BigDecimal upTo = slabUpTos.get(i) == null || slabUpTos.get(i).isBlank()
                        ? null : new BigDecimal(slabUpTos.get(i));
                slabs.add(new TaxSlabSet.TaxSlab(upTo, slabRates.get(i)));
            }
        }
        set.setSlabs(slabs);
        db.taxSlabSets.save(set);
        ra.addFlashAttribute("success", "Tax slab set saved.");
        return "redirect:/admin/tax-slabs";
    }
}
