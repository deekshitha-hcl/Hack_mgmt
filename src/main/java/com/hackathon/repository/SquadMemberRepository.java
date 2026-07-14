package com.hackathon.repository;

import com.hackathon.entity.SquadMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadMemberRepository extends JpaRepository<SquadMember, Long> {
    List<SquadMember> findBySquadId(Long squadId);

    void deleteBySquadId(Long squadId);

    void deleteByParticipantId(Long participantId);
}
