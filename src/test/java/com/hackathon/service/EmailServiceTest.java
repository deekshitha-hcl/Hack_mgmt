package com.hackathon.service;

import com.hackathon.entity.EmailLog;
import com.hackathon.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService = new EmailService(emailLogRepository, mailSender, "sender@example.com");

    @Test
    void sendRegistrationConfirmationSendsSuccessEmailWithParticipantCodeAndMotivationalQuote() {
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendRegistrationConfirmation(7L, "participant@example.com", "Asha", "PART-0007");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly("participant@example.com");
        assertThat(message.getSubject()).isEqualTo("Registration successful - PART-0007");
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getText()).contains("Hello Asha");
        assertThat(message.getText()).contains("Your registration was successful.");
        assertThat(message.getText()).contains("Your participant code is: PART-0007");
        assertThat(message.getText()).containsAnyOf(
                "Success is the sum of small efforts, repeated day in and day out. - Robert Collier",
                "Believe you can and you're halfway there. - Theodore Roosevelt",
                "Great things are done by a series of small things brought together. - Vincent van Gogh",
                "The future depends on what you do today. - Mahatma Gandhi",
                "Start where you are. Use what you have. Do what you can. - Arthur Ashe"
        );
    }
}