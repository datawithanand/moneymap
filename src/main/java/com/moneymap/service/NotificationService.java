package com.moneymap.service;

import com.moneymap.model.Notification;
import com.moneymap.model.User;
import com.moneymap.repository.Db;
import com.moneymap.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Notification Centre (Section 03/04 §5). Security-critical types always produce an in-app
 * notification regardless of preferences (Section 01 §5.4); email is a secondary channel.
 */
@Service
public class NotificationService {

    private final Db db;
    private final UserRepository users;
    private final EmailService email;

    public NotificationService(Db db, UserRepository users, EmailService email) {
        this.db = db;
        this.users = users;
        this.email = email;
    }

    public void notify(String userId, Notification.Type type, String relatedEntityId, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setRelatedEntityId(relatedEntityId);
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(Instant.now());

        User recipient = users.findById(userId).orElse(null);
        boolean sent = false;
        if (recipient != null && recipient.isNotifyEmail() && email.isEnabled()) {
            sent = email.send(recipient.getEmail(), "MoneyMap: " + message, message);
        }
        n.setEmailSent(sent);
        db.notifications.save(n);
    }

    public List<Notification> forUser(String userId) {
        return db.notifications.findWhere(n -> userId.equals(n.getUserId())).stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .toList();
    }

    public void markRead(String userId, String notificationId) {
        db.notifications.findById(notificationId)
                .filter(n -> userId.equals(n.getUserId()))
                .ifPresent(n -> {
                    n.setRead(true);
                    n.setReadAt(Instant.now());
                    db.notifications.save(n);
                });
    }

    public void markAllRead(String userId) {
        for (Notification n : db.notifications.findWhere(n -> userId.equals(n.getUserId()) && !n.isRead())) {
            n.setRead(true);
            n.setReadAt(Instant.now());
            db.notifications.save(n);
        }
    }
}
