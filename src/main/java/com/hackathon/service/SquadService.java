package com.hackathon.service;

import com.hackathon.dto.AutoSquadRequest;
import com.hackathon.dto.SquadRequest;
import com.hackathon.entity.Participant;
import com.hackathon.entity.Squad;
import com.hackathon.entity.SquadMember;
import com.hackathon.exception.BadRequestException;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.ParticipantRepository;
import com.hackathon.repository.SquadMemberRepository;
import com.hackathon.repository.SquadRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
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
    public List<Squad> autoGenerate(AutoSquadRequest request) {
        eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));

        if (request.maxMembers() < request.minMembers()) {
            throw new BadRequestException("maxMembers must be >= minMembers");
        }

        // Load participants sorted by experience desc for balanced distribution
        List<Participant> all = participantRepository
                .findByEventIdOrderByExperienceYearsDesc(request.eventId());

        // Optional tech-stack filter (comma-separated keywords, any match)
        if (request.techStackFilter() != null && !request.techStackFilter().isBlank()) {
            String filter = request.techStackFilter().toLowerCase();
            all = all.stream()
                    .filter(p -> p.getTechStack() != null
                            && p.getTechStack().toLowerCase().contains(filter))
                    .toList();
        }

        if (all.isEmpty()) {
            throw new BadRequestException("No participants found for the given criteria");
        }

        int total = all.size();
        // Number of full squads: floor(total / minMembers)
        int numSquads = Math.max(1, total / request.minMembers());
        int remainder = total % request.minMembers();

        // Create named squads
        String prefix = (request.squadNamePrefix() != null && !request.squadNamePrefix().isBlank())
                ? request.squadNamePrefix() : "Squad";
        List<Squad> squads = new ArrayList<>();
        for (int i = 0; i < numSquads; i++) {
            squads.add(squadRepository.save(Squad.builder()
                    .eventId(request.eventId())
                    .name(prefix + " " + (i + 1))
                    .build()));
        }

        // Round-robin distribution over experience-sorted list balances seniority across squads
        // e.g. 3 squads: rank-1 → squad-0, rank-2 → squad-1, rank-3 → squad-2, rank-4 → squad-0 ...
        List<List<Long>> buckets = new ArrayList<>();
        List<Integer> expSums = new ArrayList<>();
        for (int i = 0; i < numSquads; i++) {
            buckets.add(new ArrayList<>());
            expSums.add(0);
        }

        int fullCount = total - remainder;
        for (int i = 0; i < fullCount; i++) {
            Participant p = all.get(i);
            int idx = i % numSquads;
            buckets.get(idx).add(p.getId());
            expSums.set(idx, expSums.get(idx) + (p.getExperienceYears() != null ? p.getExperienceYears() : 0));
        }

        // Remainder participants go to the squad(s) with the lowest total experience sum
        for (int r = 0; r < remainder; r++) {
            int minIdx = IntStream.range(0, numSquads)
                    .boxed()
                    .min(Comparator.comparingInt(expSums::get))
                    .orElse(0);
            Participant p = all.get(fullCount + r);
            buckets.get(minIdx).add(p.getId());
            expSums.set(minIdx, expSums.get(minIdx) + (p.getExperienceYears() != null ? p.getExperienceYears() : 0));
        }

        // Persist squad memberships
        for (int s = 0; s < numSquads; s++) {
            Long squadId = squads.get(s).getId();
            for (Long participantId : buckets.get(s)) {
                squadMemberRepository.save(SquadMember.builder()
                        .squadId(squadId)
                        .participantId(participantId)
                        .build());
            }
        }

        return squads;
    }

    @Transactional
    public void delete(Long id) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Squad not found: " + id));
        squadMemberRepository.deleteBySquadId(id);
        squadRepository.delete(squad);
    }
}
