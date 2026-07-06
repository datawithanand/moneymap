package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Notification Centre (Section 03/04 §5.1). */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    private User user(HttpServletRequest r) { return (User) r.getAttribute("currentUser"); }

    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        model.addAttribute("entries", notifications.forUser(user(request).getId()));
        return "notifications";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable String id, HttpServletRequest request) {
        notifications.markRead(user(request).getId(), id);
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(HttpServletRequest request) {
        notifications.markAllRead(user(request).getId());
        return "redirect:/notifications";
    }
}
