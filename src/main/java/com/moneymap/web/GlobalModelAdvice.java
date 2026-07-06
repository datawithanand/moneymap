package com.moneymap.web;

import com.moneymap.model.GlobalSettings;
import com.moneymap.model.User;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import com.moneymap.repository.GlobalSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.SecureRandom;
import java.util.Base64;

/** Model attributes available to every Thymeleaf view: CSRF token, instance settings, current user. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final GlobalSettingsRepository globalSettings;
    private final DateFmt dateFmt;
    private final ModuleRegistry moduleRegistry;
    private final Db db;
    private final SecureRandom random = new SecureRandom();

    public GlobalModelAdvice(GlobalSettingsRepository globalSettings, DateFmt dateFmt,
                             ModuleRegistry moduleRegistry, Db db) {
        this.globalSettings = globalSettings;
        this.dateFmt = dateFmt;
        this.moduleRegistry = moduleRegistry;
        this.db = db;
    }

    @ModelAttribute("fmt")
    public DateFmt fmt() {
        return dateFmt;
    }

    /** Current path for the sidebar theme switcher's return-to redirect (Thymeleaf 3.1 has no #request). */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("assetModules")
    public java.util.List<com.moneymap.module.ModuleDef<?>> assetModules() {
        return moduleRegistry.all();
    }

    /** Asset modules grouped for the top-nav dropdowns. */
    @ModelAttribute("moduleGroups")
    public java.util.Map<String, java.util.List<com.moneymap.module.ModuleDef<?>>> moduleGroups() {
        java.util.Map<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        groups.put("Banking & Cash", java.util.List.of("savings-accounts", "cash-in-hand", "fixed-deposits", "recurring-deposits", "flexi-rds"));
        groups.put("Investments", java.util.List.of("mutual-funds", "stocks", "bonds", "crypto", "esops"));
        groups.put("Retirement", java.util.List.of("pf", "ppf", "nps", "gratuity", "government-schemes"));
        groups.put("Insurance", java.util.List.of("term-insurance", "health-insurance", "lic-policies"));
        groups.put("Property & Gold", java.util.List.of("real-estate", "gold", "physical-assets"));
        groups.put("Loans & Cards", java.util.List.of("loans"));
        groups.put("Planning & Income", java.util.List.of("goals", "other-income", "salary"));
        java.util.Map<String, java.util.List<com.moneymap.module.ModuleDef<?>>> out = new java.util.LinkedHashMap<>();
        for (var e : groups.entrySet()) {
            java.util.List<com.moneymap.module.ModuleDef<?>> defs = new java.util.ArrayList<>();
            for (String path : e.getValue()) {
                var def = moduleRegistry.byPath(path);
                if (def != null) defs.add(def);
            }
            out.put(e.getKey(), defs);
        }
        return out;
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        if (!(user instanceof User u)) return 0;
        return db.notifications.findWhere(n -> u.getId().equals(n.getUserId()) && !n.isRead()).size();
    }

    /** CSRF token per session — required on every POST form (Section 16). */
    @ModelAttribute("csrf")
    public String csrf(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(SessionKeys.CSRF_TOKEN);
        if (token == null) {
            byte[] raw = new byte[24];
            random.nextBytes(raw);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            session.setAttribute(SessionKeys.CSRF_TOKEN, token);
        }
        return token;
    }

    /** Instance name for the <title> tag and login-page branding (Section 01B). */
    @ModelAttribute("instanceSettings")
    public GlobalSettings instanceSettings() {
        return globalSettings.get();
    }

    @ModelAttribute("currentUser")
    public User currentUser(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        return user instanceof User u ? u : null;
    }

    /** Lowercase-hyphen theme class for the body tag, e.g. THEME GREEN → "theme-green". */
    @ModelAttribute("themeClass")
    public String themeClass(HttpServletRequest request) {
        Object user = request.getAttribute("currentUser");
        User.Theme theme = user instanceof User u ? u.getTheme() : User.Theme.GREEN;
        return "theme-" + theme.name().toLowerCase().replace('_', '-');
    }
}
