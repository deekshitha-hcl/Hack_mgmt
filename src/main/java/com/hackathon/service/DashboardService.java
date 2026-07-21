package com.hackathon.service;

import com.hackathon.dto.DashboardSummary;
import com.hackathon.dto.PanelistDashboardEventSummary;
import com.hackathon.dto.PanelistDashboardResponse;
import com.hackathon.entity.Event;
import com.hackathon.entity.Feedback;
import com.hackathon.entity.Panelist;
import com.hackathon.entity.Participant;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EmailLogRepository;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.PanelistRepository;
import com.hackathon.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final FeedbackRepository feedbackRepository;
    private final PanelistRepository panelistRepository;
    private final EmailLogRepository emailLogRepository;

    public DashboardService(EventRepository eventRepository, ParticipantRepository participantRepository,
                            FeedbackRepository feedbackRepository, PanelistRepository panelistRepository,
                            EmailLogRepository emailLogRepository) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.feedbackRepository = feedbackRepository;
        this.panelistRepository = panelistRepository;
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

    /**
     * Ranks event summaries by feedback recency, submission volume, and participant scope.
     * Sort order:
     * 1. Most recent lastFeedbackAt (null values last)
     * 2. Highest feedbackCount
     * 3. Highest participantCount
     */
    private List<PanelistDashboardEventSummary> rankEventSummaries(
            List<PanelistDashboardEventSummary> summaries) {
        return summaries.stream()
            .sorted(Comparator
                .comparing(PanelistDashboardEventSummary::lastFeedbackAt, 
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PanelistDashboardEventSummary::feedbackCount, 
                    Comparator.reverseOrder())
                .thenComparing(PanelistDashboardEventSummary::participantCount, 
                    Comparator.reverseOrder()))
            .toList();
    }

        public PanelistDashboardResponse panelistDashboardByEmail(String email) {
        Panelist panelist = panelistRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Panelist not found: " + email));
        return panelistDashboard(panelist.getId());
        }

        public PanelistDashboardResponse panelistDashboard(Long panelistId) {
        Panelist panelist = panelistRepository.findById(panelistId)
            .orElseThrow(() -> new ResourceNotFoundException("Panelist not found: " + panelistId));

        List<Feedback> feedbacks = feedbackRepository.findByPanelistIdOrderBySubmittedAtAsc(panelistId);
        List<Long> participantIds = feedbacks.stream()
            .map(Feedback::getParticipantId)
            .distinct()
            .toList();

        Map<Long, Participant> participantsById = participantRepository.findAllById(participantIds).stream()
            .collect(Collectors.toMap(Participant::getId, Function.identity()));

        List<Long> eventIds = participantsById.values().stream()
            .map(Participant::getEventId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, Event> eventsById = eventRepository.findAllById(eventIds).stream()
            .collect(Collectors.toMap(Event::getId, Function.identity()));

        Map<Long, List<Feedback>> feedbacksByEventId = new LinkedHashMap<>();
        for (Feedback feedback : feedbacks) {
            Participant participant = participantsById.get(feedback.getParticipantId());
            if (participant == null || participant.getEventId() == null) {
            continue;
            }
            feedbacksByEventId.computeIfAbsent(participant.getEventId(), key -> new ArrayList<>()).add(feedback);
        }

        List<PanelistDashboardEventSummary> eventSummaries = feedbacksByEventId.entrySet().stream()
            .map(entry -> {
                Long eventId = entry.getKey();
                List<Feedback> eventFeedbacks = entry.getValue();
                Event event = eventsById.get(eventId);
                long participantCount = eventFeedbacks.stream().map(Feedback::getParticipantId).distinct().count();
                long feedbackCount = eventFeedbacks.size();
                List<com.hackathon.entity.FeedbackType> feedbackTypes = eventFeedbacks.stream()
                    .map(Feedback::getFeedbackType)
                    .distinct()
                    .toList();
                LocalDateTime lastFeedbackAt = eventFeedbacks.stream()
                    .map(Feedback::getSubmittedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
                return new PanelistDashboardEventSummary(
                    eventId,
                    event != null ? event.getName() : "Unknown Event",
                    participantCount,
                    feedbackCount,
                    feedbackTypes,
                    lastFeedbackAt
                );
            })
            .toList();

        eventSummaries = rankEventSummaries(eventSummaries);

        long participantsFeedbackGiven = feedbacks.stream().map(Feedback::getParticipantId).distinct().count();

        return new PanelistDashboardResponse(
            panelist.getId(),
            panelist.getName(),
            panelist.getEmail(),
            eventSummaries.size(),
            participantsFeedbackGiven,
            feedbacks.size(),
            eventSummaries
        );
        }
}
