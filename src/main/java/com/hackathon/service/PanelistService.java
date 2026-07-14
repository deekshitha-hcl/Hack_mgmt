package com.hackathon.service;

import com.hackathon.dto.PanelistInviteResponse;
import com.hackathon.dto.PanelistRegistrationRequest;
import com.hackathon.entity.Panelist;
import com.hackathon.entity.PanelistAvailability;
import com.hackathon.entity.PanelistInvite;
import com.hackathon.entity.Role;
import com.hackathon.entity.User;
import com.hackathon.exception.BadRequestException;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.PanelistInviteRepository;
import com.hackathon.repository.PanelistRepository;
import com.hackathon.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PanelistService {

    private final PanelistRepository panelistRepository;
    private final PanelistInviteRepository panelistInviteRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PanelistService(PanelistRepository panelistRepository,
            PanelistInviteRepository panelistInviteRepository,
            UserRepository userRepository,
            FeedbackRepository feedbackRepository,
            PasswordEncoder passwordEncoder) {
        this.panelistRepository = panelistRepository;
        this.panelistInviteRepository = panelistInviteRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Admin generates a one-time registration link valid for 48 hours. */
    @Transactional
    public PanelistInviteResponse generateInvite() {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(48);

        PanelistInvite invite = PanelistInvite.builder()
                .token(token)
                .createdAt(now)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        panelistInviteRepository.save(invite);

        String registrationUrl = frontendUrl + "/panelist/register?token=" + token;
        return new PanelistInviteResponse(token, registrationUrl, expiresAt);
    }

    /** Validates that an invite token is still usable (not expired, not already used). */
    public PanelistInvite validateInviteToken(String token) {
        PanelistInvite invite = panelistInviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid registration link."));

        if (invite.isUsed()) {
            throw new BadRequestException("This registration link has already been used.");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This registration link has expired.");
        }
        return invite;
    }

    /** Panelist self-registers using an invite token. */
    @Transactional
    public Panelist registerWithToken(String token, PanelistRegistrationRequest request) {
        PanelistInvite invite = validateInviteToken(token);

        if (panelistRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered: " + request.email());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already registered: " + request.email());
        }
        if (request.availability() == PanelistAvailability.CUSTOM
                && (request.customAvailabilityTime() == null || request.customAvailabilityTime().isBlank())) {
            throw new BadRequestException("customAvailabilityTime is required when availability is CUSTOM.");
        }

        Panelist panelist = panelistRepository.save(Panelist.builder()
                .name(request.name())
                .email(request.email())
                .domain(request.domain())
                .availability(request.availability())
                .customAvailabilityTime(request.availability() == PanelistAvailability.CUSTOM
                        ? request.customAvailabilityTime()
                        : null)
                .build());

        User user = User.builder()
                .username(request.email())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_PANELIST)
                .build();
        userRepository.save(user);

        invite.setUsed(true);
        panelistInviteRepository.save(invite);

        return panelist;
    }

    public List<Panelist> findAll() {
        return panelistRepository.findAll();
    }

    /** Returns all active (unused, non-expired) invite tokens. */
    public List<PanelistInvite> findActiveInvites() {
        return panelistInviteRepository.findByUsedFalseAndExpiresAtAfter(LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id) {
        Panelist panelist = panelistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Panelist not found: " + id));
        feedbackRepository.deleteByPanelistId(id);
        userRepository.findByEmail(panelist.getEmail()).ifPresent(userRepository::delete);
        panelistRepository.delete(panelist);
    }
}

