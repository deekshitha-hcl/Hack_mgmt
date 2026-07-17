package com.hackathon.repository;

import com.hackathon.entity.Participant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByEventId(Long eventId);

    List<Participant> findByEventIdOrderByExperienceYearsDesc(Long eventId);

    Optional<Participant> findByParticipantCode(String participantCode);

    Optional<Participant> findTopByOrderByIdDesc();

    Optional<Participant> findByEmail(String email);
}
