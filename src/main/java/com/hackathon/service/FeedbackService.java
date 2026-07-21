package com.hackathon.service;

import com.hackathon.dto.FeedbackRequest;
import com.hackathon.dto.FeedbackSubmitResponse;
import com.hackathon.dto.FeedbackTemplateFieldResponse;
import com.hackathon.dto.FeedbackTemplateResponse;
import com.hackathon.dto.ParticipantFeedbackStatusResponse;
import com.hackathon.dto.ParticipantFeedbackTypeStatus;
import com.hackathon.dto.ParticipantFeedbackDetailResponse;
import com.hackathon.entity.FeedbackDetail;
import com.hackathon.entity.FeedbackFieldType;
import com.hackathon.entity.FeedbackTemplate;
import com.hackathon.entity.Feedback;
import com.hackathon.entity.FeedbackType;
import com.hackathon.entity.Panelist;
import com.hackathon.exception.BadRequestException;
import com.hackathon.exception.DuplicateSubmissionException;
import com.hackathon.entity.Participant;
import com.hackathon.entity.ParticipantStatus;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.FeedbackDetailRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.FeedbackTemplateRepository;
import com.hackathon.repository.PanelistRepository;
import com.hackathon.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackTemplateRepository feedbackTemplateRepository;
    private final FeedbackDetailRepository feedbackDetailRepository;
    private final ParticipantRepository participantRepository;
    private final PanelistRepository panelistRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            FeedbackTemplateRepository feedbackTemplateRepository,
            FeedbackDetailRepository feedbackDetailRepository,
            ParticipantRepository participantRepository,
            PanelistRepository panelistRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackTemplateRepository = feedbackTemplateRepository;
        this.feedbackDetailRepository = feedbackDetailRepository;
        this.participantRepository = participantRepository;
        this.panelistRepository = panelistRepository;
    }

    public FeedbackTemplateResponse getTemplate(com.hackathon.entity.FeedbackType feedbackType) {
        List<FeedbackTemplate> templates = sanitizeTemplates(
                feedbackTemplateRepository.findByFeedbackTypeOrderByIdAsc(feedbackType));
        if (templates.isEmpty()) {
            throw new ResourceNotFoundException("No feedback template found for type: " + feedbackType);
        }

        List<FeedbackTemplateFieldResponse> fields = templates.stream()
                .map(template -> new FeedbackTemplateFieldResponse(
                        template.getFieldName(),
                        template.getFieldType().name(),
                        template.getIsRequired()
                ))
                .toList();

        return new FeedbackTemplateResponse(feedbackType.name(), fields);
    }

    public ParticipantFeedbackStatusResponse getParticipantFeedbackStatus(Long participantId) {
        participantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));

        List<ParticipantFeedbackTypeStatus> statusList = Arrays.stream(FeedbackType.values())
                .map(type -> new ParticipantFeedbackTypeStatus(
                        type.name(),
                        feedbackRepository.existsByParticipantIdAndFeedbackType(participantId, type)
                ))
                .toList();

        int totalSubmitted = (int) statusList.stream().filter(ParticipantFeedbackTypeStatus::submitted).count();
        int totalAllowed = FeedbackType.values().length;

        return new ParticipantFeedbackStatusResponse(
                participantId,
                totalSubmitted,
                totalAllowed,
                totalSubmitted == totalAllowed,
                statusList
        );
    }

    public List<ParticipantFeedbackDetailResponse> getParticipantFeedbackDetails(Long participantId) {
        participantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));

        List<Feedback> feedbacks = feedbackRepository.findByParticipantIdOrderBySubmittedAtAsc(participantId);

        return feedbacks.stream()
                .map(feedback -> {
                    Panelist panelist = panelistRepository.findById(feedback.getPanelistId())
                            .orElse(null);

                    List<FeedbackDetail> details = feedbackDetailRepository.findByFeedbackId(feedback.getId());
                    Map<String, String> fieldValues = details.stream()
                            .collect(Collectors.toMap(FeedbackDetail::getFieldName, FeedbackDetail::getFieldValue));

                    return new ParticipantFeedbackDetailResponse(
                            feedback.getId(),
                            feedback.getParticipantId(),
                            feedback.getPanelistId(),
                            panelist != null ? panelist.getName() : "Unknown",
                            panelist != null ? panelist.getEmail() : "unknown@example.com",
                            feedback.getFeedbackType(),
                            feedback.getSubmittedAt(),
                            fieldValues
                    );
                })
                .toList();
    }

    @Transactional
    public FeedbackSubmitResponse submit(FeedbackRequest request) {
        Participant participant = participantRepository.findById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + request.participantId()));
        panelistRepository.findById(request.panelistId())
                .orElseThrow(() -> new ResourceNotFoundException("Panelist not found: " + request.panelistId()));

        if (feedbackRepository.existsByParticipantIdAndFeedbackType(
                request.participantId(), request.feedbackType())) {
            throw new DuplicateSubmissionException(
                    "Feedback for this type is already submitted for participant: " + request.feedbackType());
        }

        List<FeedbackTemplate> templates = sanitizeTemplates(
            feedbackTemplateRepository.findByFeedbackTypeOrderByIdAsc(request.feedbackType()));
        if (templates.isEmpty()) {
            throw new ResourceNotFoundException("No feedback template found for type: " + request.feedbackType());
        }

        validateFieldValues(templates, request.fieldValues());

        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .participantId(request.participantId())
                .panelistId(request.panelistId())
                .feedbackType(request.feedbackType())
                .submittedAt(LocalDateTime.now())
                .build());

        List<FeedbackDetail> details = new ArrayList<>();
        for (Map.Entry<String, String> fieldEntry : request.fieldValues().entrySet()) {
            details.add(FeedbackDetail.builder()
                    .feedbackId(feedback.getId())
                    .fieldName(fieldEntry.getKey())
                    .fieldValue(fieldEntry.getValue())
                    .build());
        }
        feedbackDetailRepository.saveAll(details);

        participant.setStatus(ParticipantStatus.COMPLETED);
        participantRepository.save(participant);

        return new FeedbackSubmitResponse("SUCCESS", "Feedback submitted successfully");
    }

    private List<FeedbackTemplate> sanitizeTemplates(List<FeedbackTemplate> templates) {
        return templates.stream()
                .filter(template -> !"overallRecommendation".equals(template.getFieldName()))
                .toList();
    }

    private void validateFieldValues(List<FeedbackTemplate> templates, Map<String, String> fieldValues) {
        Set<String> allowedFields = new HashSet<>();
        for (FeedbackTemplate template : templates) {
            allowedFields.add(template.getFieldName());
        }

        for (String fieldName : fieldValues.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new BadRequestException("Unknown field submitted: " + fieldName);
            }
        }

        for (FeedbackTemplate template : templates) {
            String value = fieldValues.get(template.getFieldName());
            if (Boolean.TRUE.equals(template.getIsRequired()) && (value == null || value.isBlank())) {
                throw new BadRequestException("Required field missing: " + template.getFieldName());
            }
            if (value == null || value.isBlank()) {
                continue;
            }

            if (template.getFieldType() == FeedbackFieldType.RATING) {
                validateRating(template.getFieldName(), value);
            }
        }
    }

    private void validateRating(String fieldName, String value) {
        try {
            int rating = Integer.parseInt(value);
            if (rating < 1 || rating > 5) {
                throw new BadRequestException("Field " + fieldName + " must be a rating between 1 and 5");
            }
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Field " + fieldName + " must be a numeric rating between 1 and 5");
        }
    }

}
