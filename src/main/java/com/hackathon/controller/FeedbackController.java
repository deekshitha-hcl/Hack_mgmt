package com.hackathon.controller;

import com.hackathon.dto.FeedbackRequest;
import com.hackathon.dto.FeedbackSubmitResponse;
import com.hackathon.dto.FeedbackTemplateResponse;
import com.hackathon.dto.ParticipantFeedbackStatusResponse;
import com.hackathon.dto.ParticipantFeedbackDetailResponse;
import com.hackathon.entity.FeedbackType;
import com.hackathon.service.FeedbackService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/templates/{feedbackType}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public FeedbackTemplateResponse getTemplate(@PathVariable FeedbackType feedbackType) {
        return feedbackService.getTemplate(feedbackType);
    }

    @GetMapping("/status/{participantId}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public ParticipantFeedbackStatusResponse getParticipantFeedbackStatus(@PathVariable Long participantId) {
        return feedbackService.getParticipantFeedbackStatus(participantId);
    }

    @GetMapping("/participant/{participantId}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<ParticipantFeedbackDetailResponse> getParticipantFeedbackDetails(@PathVariable Long participantId) {
        return feedbackService.getParticipantFeedbackDetails(participantId);
    }

    @GetMapping("/by-participant/{participantId}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<ParticipantFeedbackDetailResponse> getParticipantFeedbackDetailsAlias(@PathVariable Long participantId) {
        return feedbackService.getParticipantFeedbackDetails(participantId);
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public FeedbackSubmitResponse submit(@Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(request);
    }
}
