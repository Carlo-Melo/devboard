package com.devboard.service;

import com.devboard.entity.User;
import com.devboard.entity.ProjectInvite;
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

    @Async("emailExecutor")
    public void sendProjectInviteEmail(ProjectInvite invite) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(invite.getEmail());
            message.setSubject("Convite para projeto no devBoard");
            String inviterName = invite.getInvitedBy().getFullName() != null
                    ? invite.getInvitedBy().getFullName() : invite.getInvitedBy().getUsername();
            String link = baseUrl + "/invites/" + invite.getToken();
            message.setText("""
                    Olá!

                    %s convidou você para participar do projeto %s no devBoard como %s.
                    Aceite o convite em até 7 dias pelo link abaixo:

                    %s
                    """.formatted(inviterName, invite.getProject().getName(), invite.getRole(), link));
            mailSender.send(message);
            log.info("Email de convite enviado: inviteId={}", invite.getId());
        } catch (Exception ex) {
            log.error("Falha ao enviar email de convite: inviteId={}", invite.getId(), ex);
        }
    }
}
