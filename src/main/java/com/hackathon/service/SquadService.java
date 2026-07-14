package com.hackathon.service;

import com.hackathon.dto.SquadRequest;
import com.hackathon.entity.Participant;
import com.hackathon.entity.Squad;
import com.hackathon.entity.SquadMember;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.ParticipantRepository;
import com.hackathon.repository.SquadMemberRepository;
import com.hackathon.repository.SquadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SquadService {

    private final SquadRepository squadRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public SquadService(SquadRepository squadRepository, SquadMemberRepository squadMemberRepository,
                        EventRepository eventRepository, ParticipantRepository participantRepository) {
        this.squadRepository = squadRepository;
        this.squadMemberRepository = squadMemberRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
    }

    public Squad create(SquadRequest request) {
        eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));
        return squadRepository.save(Squad.builder()
                .eventId(request.eventId())
                .name(request.name())
                .build());
    }

    public SquadMember addMember(Long squadId, Long participantId) {
        squadRepository.findById(squadId)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found: " + squadId));
        participantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));
        return squadMemberRepository.save(SquadMember.builder()
                .squadId(squadId)
                .participantId(participantId)
                .build());
    }

    public List<Squad> findByEvent(Long eventId) {
        return squadRepository.findByEventId(eventId);
    }

    public List<Participant> getMembers(Long squadId) {
        squadRepository.findById(squadId)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found: " + squadId));
        List<Long> participantIds = squadMemberRepository.findBySquadId(squadId).stream()
                .map(SquadMember::getParticipantId)
                .toList();
        return participantRepository.findAllById(participantIds);
    }

    @Transactional
    public void delete(Long id) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found: " + id));
        squadMemberRepository.deleteBySquadId(id);
        squadRepository.delete(squad);
    }
}
