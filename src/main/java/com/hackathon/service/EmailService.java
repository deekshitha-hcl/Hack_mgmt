package com.hackathon.service;

import com.hackathon.entity.EmailLog;
import com.hackathon.repository.EmailLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final List<String> MOTIVATIONAL_QUOTES = List.of(
            "Success is the sum of small efforts, repeated day in and day out. - Robert Collier",
            "Believe you can and you're halfway there. - Theodore Roosevelt",
            "Great things are done by a series of small things brought together. - Vincent van Gogh",
            "The future depends on what you do today. - Mahatma Gandhi",
            "Start where you are. Use what you have. Do what you can. - Arthur Ashe"
    );

    private final EmailLogRepository emailLogRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(EmailLogRepository emailLogRepository, JavaMailSender mailSender,
                        @Value("${app.mail.from:}") String fromAddress) {
        this.emailLogRepository = emailLogRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendRegistrationConfirmation(Long participantId, String email, String participantName,
                                             String participantCode) {
        String subject = "Registration successful - " + participantCode;
        String body = buildRegistrationConfirmationBody(participantName, participantCode);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email phase=sent participantId={} email={}", participantId, email);
        } catch (MailException ex) {
            log.error("Email phase=send_failed participantId={} email={} reason={}", participantId, email,
                    ex.getMessage(), ex);
        }

        emailLogRepository.save(EmailLog.builder()
                .participantId(participantId)
                .email(email)
                .subject(subject)
                .sentTime(LocalDateTime.now())
                .build());
    }

    private String buildRegistrationConfirmationBody(String participantName, String participantCode) {
        String quote = MOTIVATIONAL_QUOTES.get(ThreadLocalRandom.current().nextInt(MOTIVATIONAL_QUOTES.size()));
        String greetingName = participantName == null || participantName.isBlank() ? "Participant" : participantName;

        return """
                Hello %s,

                Your registration was successful.
                Your participant code is: %s

                Please keep this code safe for event communication and check-in.

                %s

                Regards,
                Hackathon Team
                """.formatted(greetingName, participantCode, quote);
    }
}