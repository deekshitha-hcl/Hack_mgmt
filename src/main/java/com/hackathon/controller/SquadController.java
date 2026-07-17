package com.hackathon.controller;

import com.hackathon.dto.AutoSquadRequest;
import com.hackathon.dto.SquadRequest;
import com.hackathon.entity.Participant;
import com.hackathon.entity.Squad;
import com.hackathon.entity.SquadMember;
import com.hackathon.service.SquadService;
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
@RequestMapping("/api/squads")
public class SquadController {

    private final SquadService squadService;

    public SquadController(SquadService squadService) {
        this.squadService = squadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Squad create(@Valid @RequestBody SquadRequest request) {
        return squadService.create(request);
    }

    @PostMapping("/auto-generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Squad> autoGenerate(@Valid @RequestBody AutoSquadRequest request) {
        return squadService.autoGenerate(request);
    }

    @PostMapping("/{squadId}/members/{participantId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SquadMember addMember(@PathVariable Long squadId, @PathVariable Long participantId) {
        return squadService.addMember(squadId, participantId);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Squad> findByEvent(@PathVariable Long eventId) {
        return squadService.findByEvent(eventId);
    }

    @GetMapping("/{squadId}/members")
    @PreAuthorize("hasAnyRole('ADMIN','PANELIST')")
    public List<Participant> getMembers(@PathVariable Long squadId) {
        return squadService.getMembers(squadId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        squadService.delete(id);
    }
}
