package com.devboard.service;

import com.devboard.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Async("emailExecutor")
    public void sendPasswordResetEmail(User user, String rawToken) {
        String link = baseUrl + "/reset-password?token=" + rawToken;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("Recuperação de senha no devBoard");
            message.setText("""
                    Olá, %s!

                    Recebemos uma solicitação para redefinir sua senha no devBoard.
                    Clique no link abaixo para escolher uma nova senha. Este link expira em 24 horas.

                    %s

                    Se você não solicitou isso, apenas ignore este email.
                    """.formatted(user.getFullName() != null ? user.getFullName() : user.getUsername(), link));

            mailSender.send(message);
            log.info("Email de recuperação de senha enviado: userId={}", user.getId());
        } catch (Exception ex) {
            log.error("Falha ao enviar email de recuperação de senha: userId={}", user.getId(), ex);
        }
    }
}
