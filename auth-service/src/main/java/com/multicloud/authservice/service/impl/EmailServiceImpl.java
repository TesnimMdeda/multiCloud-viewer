package com.multicloud.authservice.service.impl;

import com.multicloud.authservice.entity.User;
import com.multicloud.authservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.*;
        import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordSetupEmail(User user, String setupLink) {
        sendHtmlEmail(
                user.getEmail(),
                "Welcome to Multi-Cloud Viewer - Set Your Password",
                buildSetupEmailBody(user, setupLink)
        );
    }

    @Override
    public void sendPasswordResetEmail(User user, String resetLink) {
        sendHtmlEmail(
                user.getEmail(),
                "Reset Your Password - Multi-Cloud Viewer",
                buildResetEmailBody(user, resetLink)
        );
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private String buildSetupEmailBody(User user, String link) {
        try {
            ClassPathResource resource =
                    new ClassPathResource("templates/email/set-password.html");
            String template = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            return template.formatted(
                    user.getFirstName(),
                    link,
                    link
            );
        } catch (IOException e) {
            log.error("Failed to load email template", e);
            return buildFallbackSetupEmail(user, link);
        }
    }

    private String buildFallbackSetupEmail(User user, String link) {
        return """
        <html><body style="font-family:Arial,sans-serif;padding:20px;">
          <h2 style="color:#1E3A5F;">Bienvenue, %s !</h2>
          <p>Cliquez pour définir votre mot de passe (expire dans 1h) :</p>
          <a href="%s" style="background:#2563EB;color:white;
             padding:12px 24px;text-decoration:none;border-radius:4px;
             display:inline-block;margin:16px 0;">
            Définir mon mot de passe</a>
          <p style="font-size:12px;color:#9CA3AF;">
            Si le bouton ne fonctionne pas : %s</p>
        </body></html>
        """.formatted(user.getFirstName(), link, link);
    }

    private String buildResetEmailBody(User user, String link) {
        try {
            ClassPathResource resource =
                    new ClassPathResource("templates/email/reset-password.html");
            String template = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            return template.formatted(
                    user.getFirstName(),
                    link,
                    link
            );
        } catch (IOException e) {
            log.error("Failed to load password reset email template", e);
            return buildFallbackResetEmail(user, link);
        }
    }

    private String buildFallbackResetEmail(User user, String link) {
        return """
        <html><body style="font-family:Arial,sans-serif;padding:20px;">
          <h2 style="color:#1E3A5F;">Password Reset Request</h2>
          <p>Hi %s, we received a request to reset your password.</p>
          <a href="%s" style="background:#4CAF50;color:white;
             padding:12px 24px;text-decoration:none;border-radius:4px;
             display:inline-block;margin:16px 0;">
            Reset Password</a>
          <p style="font-size:12px;color:#9CA3AF;">
            If the button doesn’t work, copy this link: %s</p>
          <p style="font-size:12px;color:#9CA3AF;">
            This link expires in 1 hour. If you did not request this, ignore it.</p>
        </body></html>
        """.formatted(user.getFirstName(), link, link);
    }
}