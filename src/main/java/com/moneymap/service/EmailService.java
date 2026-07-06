package com.moneymap.service;

import com.moneymap.model.GlobalSettings;
import com.moneymap.repository.GlobalSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Outbound email via the admin-configured SMTP settings (Section 01B). No email of any
 * kind is sent while smtpEnabled = false. Failures never block the in-app notification.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final GlobalSettingsRepository globalSettings;
    private final CryptoService crypto;

    public EmailService(GlobalSettingsRepository globalSettings, CryptoService crypto) {
        this.globalSettings = globalSettings;
        this.crypto = crypto;
    }

    public boolean isEnabled() {
        return globalSettings.get().isSmtpEnabled();
    }

    /** Returns true if the email was sent. Never throws — email is a best-effort secondary channel. */
    public boolean send(String to, String subject, String body) {
        GlobalSettings s = globalSettings.get();
        if (!s.isSmtpEnabled() || to == null || to.isBlank()) return false;
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(s.getSmtpHost());
            sender.setPort(s.getSmtpPort() == null ? 587 : s.getSmtpPort());
            sender.setUsername(s.getSmtpUsername());
            if (s.getSmtpPasswordEncrypted() != null && crypto.isConfigured()) {
                sender.setPassword(crypto.decrypt(s.getSmtpPasswordEncrypted()));
            }
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(s.getSmtpFromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("Email send failed (to={}): {}", to, e.getMessage());
            return false;
        }
    }
}
