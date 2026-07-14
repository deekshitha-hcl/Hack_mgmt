package com.hackathon.controller;

import com.hackathon.dto.ParticipantRegistrationRequest;
import com.hackathon.entity.Participant;
import com.hackathon.service.ParticipantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Participant register(@Valid @ModelAttribute ParticipantRegistrationRequest request,
            @RequestPart(name = "resume", required = false) MultipartFile resume,
            @RequestPart(name = "photo", required = false) MultipartFile photo) {
        return participantService.register(request, resume, photo);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Participant register(@Valid @RequestBody ParticipantRegistrationRequest request) {
        return participantService.register(request, null, null);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Participant> findAll() {
        return participantService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public Participant findById(@PathVariable Long id) {
        return participantService.findById(id);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Participant> findByEvent(@PathVariable Long eventId) {
        return participantService.findByEvent(eventId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        participantService.delete(id);
    }
}
