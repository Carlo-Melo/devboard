package com.devboard.service;

import com.devboard.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:4201");
        ReflectionTestUtils.setField(emailService, "from", "softwaredevmelo@gmail.com");
    }

    @Test
    void sendPasswordResetEmail_deveDefinirRemetenteConfigurado() {
        User user = new User();
        user.setId(1L);
        user.setUsername("usuario");
        user.setEmail("destinatario@example.com");

        emailService.sendPasswordResetEmail(user, "token-seguro");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getFrom()).isEqualTo("softwaredevmelo@gmail.com");
        assertThat(messageCaptor.getValue().getTo()).containsExactly("destinatario@example.com");
        assertThat(messageCaptor.getValue().getText())
                .contains("http://localhost:4201/reset-password?token=token-seguro");
    }
}
