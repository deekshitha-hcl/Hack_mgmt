package com.hackathon.repository;

import com.hackathon.entity.Squad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadRepository extends JpaRepository<Squad, Long> {
    List<Squad> findByEventId(Long eventId);
}
