package com.hackathon.service;

import com.hackathon.dto.FeedbackRequest;
import com.hackathon.entity.Feedback;
import com.hackathon.entity.Participant;
import com.hackathon.entity.ParticipantStatus;
import com.hackathon.entity.Recommendation;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.PanelistRepository;
import com.hackathon.repository.ParticipantRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ParticipantRepository participantRepository;
    private final PanelistRepository panelistRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, ParticipantRepository participantRepository,
                           PanelistRepository panelistRepository) {
        this.feedbackRepository = feedbackRepository;
        this.participantRepository = participantRepository;
        this.panelistRepository = panelistRepository;
    }

        @Transactional
        public Feedback create(FeedbackRequest request) {
        Participant participant = participantRepository.findById(request.participantId())
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + request.participantId()));
        panelistRepository.findById(request.panelistId())
                .orElseThrow(() -> new ResourceNotFoundException("Panelist not found: " + request.panelistId()));
        double avg = (request.technicalRating() + request.communicationRating()
                + request.problemSolvingRating() + request.attitudeRating()
                + request.teamworkRating()) / 5.0;
        Recommendation recommendation = avg >= 4.0 ? Recommendation.HIRE
                : avg >= 2.5 ? Recommendation.HOLD
                : Recommendation.REJECT;

        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .participantId(request.participantId())
                .panelistId(request.panelistId())
                .technicalRating(request.technicalRating())
                .communicationRating(request.communicationRating())
                .problemSolvingRating(request.problemSolvingRating())
                .attitudeRating(request.attitudeRating())
                .teamworkRating(request.teamworkRating())
                .recommendation(recommendation)
                .comments(request.comments())
                .strengths(request.strengths())
                .areasOfImprovement(request.areasOfImprovement())
                .build());

        participant.setStatus(ParticipantStatus.COMPLETED);
        participantRepository.save(participant);

        return feedback;
    }

    public List<Feedback> findAll() {
        return feedbackRepository.findAll();
    }
}
