package com.hackathon.service;

import com.hackathon.dto.DashboardSummary;
import com.hackathon.repository.EmailLogRepository;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.ParticipantRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final FeedbackRepository feedbackRepository;
    private final EmailLogRepository emailLogRepository;

    public DashboardService(EventRepository eventRepository, ParticipantRepository participantRepository,
                            FeedbackRepository feedbackRepository, EmailLogRepository emailLogRepository) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.feedbackRepository = feedbackRepository;
        this.emailLogRepository = emailLogRepository;
    }

    public DashboardSummary summary() {
        return new DashboardSummary(
                eventRepository.count(),
                participantRepository.count(),
                0,
                0,
                feedbackRepository.count(),
                emailLogRepository.count()
        );
    }
}
