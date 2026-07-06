package com.moneymap.web;

import com.moneymap.model.GlobalSettings;
import com.moneymap.model.User;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.AuthService;
import com.moneymap.service.SessionRegistry;
import com.moneymap.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Login, registration, logout (Section 01 §1.1, §2). */
@Controller
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository users;
    private final GlobalSettingsRepository globalSettings;
    private final SessionRegistry sessionRegistry;
    private final AuthInterceptor authInterceptor;

    public AuthController(AuthService authService, UserService userService, UserRepository users,
                          GlobalSettingsRepository globalSettings, SessionRegistry sessionRegistry,
                          AuthInterceptor authInterceptor) {
        this.authService = authService;
        this.userService = userService;
        this.users = users;
        this.globalSettings = globalSettings;
        this.sessionRegistry = sessionRegistry;
        this.authInterceptor = authInterceptor;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String expired,
                            @RequestParam(required = false) String elsewhere,
                            @RequestParam(required = false) String disabled,
                            @RequestParam(required = false) String loggedout,
                            Model model) {
        if (expired != null) model.addAttribute("info", "Your session has expired. Please log in again.");
        if (elsewhere != null) model.addAttribute("warning",
                "You've been signed out because your account was signed in from another location.");
        if (disabled != null) model.addAttribute("error", "This account is no longer active. Contact your administrator.");
        if (loggedout != null) model.addAttribute("info", "You have been logged out.");
        model.addAttribute("registrationOpen",
                globalSettings.get().getRegistrationMode() == GlobalSettings.RegistrationMode.OPEN);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String identifier,
                        @RequestParam String password,
                        @RequestParam(required = false) String rememberMe,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        AuthService.LoginResult result = authService.login(identifier, password);
        switch (result.status()) {
            case SUCCESS -> {
                User user = result.user();
                authInterceptor.establishSession(request, user);
                if (rememberMe != null) {
                    authService.issueRememberMe(user, response, request.isSecure());
                }
                if (user.isMustChangePassword()) return "redirect:/password/force";
                if (!user.isOnboardingCompleted()) return "redirect:/setup";
                return "redirect:/dashboard";
            }
            case DISABLED -> model.addAttribute("error", "This account has been disabled. Contact your administrator.");
            case PENDING_DELETION -> model.addAttribute("error",
                    "This account is scheduled for deletion. Contact your administrator if this was a mistake.");
            case LOCKED -> {
                String time = DateTimeFormatter.ofPattern("h:mm a")
                        .withZone(ZoneId.systemDefault())
                        .format(result.lockedUntil());
                model.addAttribute("error", "Too many failed attempts. This account is locked until " + time + ".");
            }
            default -> model.addAttribute("error", "Invalid username/email or password.");
        }
        model.addAttribute("identifier", identifier);
        model.addAttribute("registrationOpen",
                globalSettings.get().getRegistrationMode() == GlobalSettings.RegistrationMode.OPEN);
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String invite, Model model) {
        GlobalSettings settings = globalSettings.get();
        if (settings.getRegistrationMode() == GlobalSettings.RegistrationMode.INVITE_ONLY
                && (invite == null || invite.isBlank())) {
            model.addAttribute("inviteOnly", true);
        }
        model.addAttribute("invite", invite);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName,
                           @RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam(required = false) String invite,
                           HttpServletRequest request, Model model) {
        StringBuilder createdUserId = new StringBuilder();
        Map<String, String> errors = userService.register(fullName, username, email, password, confirmPassword,
                invite, createdUserId);
        if (!errors.isEmpty()) {
            if (errors.containsKey("_global")) {
                model.addAttribute("inviteOnly", true);
                return "register";
            }
            model.addAttribute("errors", errors);
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("invite", invite);
            return "register";
        }
        // Auto-login and straight into the Setup Wizard (§1.1)
        User user = users.findById(createdUserId.toString()).orElseThrow();
        authInterceptor.establishSession(request, user);
        return "redirect:/setup";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        User user = (User) request.getAttribute("currentUser");
        if (session != null) {
            if (user != null) {
                sessionRegistry.deregister(user.getId(), session.getId());
            }
            session.invalidate();
        }
        authService.clearRememberMe(user, response);   // explicit logout ends remember-me too (§2.2)
        return "redirect:/login?loggedout=1";
    }

    /** Public marketing landing page; logged-in users go straight to their dashboard. */
    @GetMapping("/")
    public String root(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKeys.USER_ID) instanceof String userId
                && users.findById(userId).isPresent()) {
            return "redirect:/dashboard";
        }
        model.addAttribute("registrationOpen",
                globalSettings.get().getRegistrationMode() == GlobalSettings.RegistrationMode.OPEN);
        return "landing";
    }
}
