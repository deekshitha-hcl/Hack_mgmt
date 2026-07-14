package com.hackathon.controller;

import com.hackathon.dto.FeedbackRequest;
import com.hackathon.entity.Feedback;
import com.hackathon.service.FeedbackService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public Feedback create(@Valid @RequestBody FeedbackRequest request) {
        return feedbackService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Feedback> findAll() {
        return feedbackService.findAll();
    }
}
