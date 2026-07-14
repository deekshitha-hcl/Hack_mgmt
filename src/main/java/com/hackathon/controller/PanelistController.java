package com.hackathon.controller;

import com.hackathon.dto.PanelistInviteResponse;
import com.hackathon.dto.PanelistRegistrationRequest;
import com.hackathon.entity.Panelist;
import com.hackathon.entity.PanelistInvite;
import com.hackathon.service.PanelistService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/panelists")
public class PanelistController {

    private final PanelistService panelistService;

    public PanelistController(PanelistService panelistService) {
        this.panelistService = panelistService;
    }

    /**
     * Admin generates a one-time invite link.
     * Returns the token and full registration URL valid for 48 hours.
     */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PanelistInviteResponse generateInvite() {
        return panelistService.generateInvite();
    }

    /**
     * Returns all active (unused, non-expired) invite tokens — admin only.
     */
    @GetMapping("/invite/active")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PanelistInvite> getActiveInvites() {
        return panelistService.findActiveInvites();
    }

    /**
     * Public endpoint: validates an invite token before the panelist fills the form.
     * Returns 200 if valid, 404/400 with an error message otherwise.
     */
    @GetMapping("/invite/validate/{token}")
    public void validateInvite(@PathVariable String token) {
        panelistService.validateInviteToken(token);
    }

    /**
     * Public endpoint: panelist self-registers using the invite token.
     * Fields: name, email, domain, password, availability, customAvailabilityTime (required if CUSTOM).
     */
    @PostMapping("/register/{token}")
    @ResponseStatus(HttpStatus.CREATED)
    public Panelist register(@PathVariable String token,
            @Valid @RequestBody PanelistRegistrationRequest request) {
        return panelistService.registerWithToken(token, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Panelist> findAll() {
        return panelistService.findAll();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        panelistService.delete(id);
    }
}

