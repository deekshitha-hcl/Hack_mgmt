package com.hackathon.repository;

import com.hackathon.entity.PanelistInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PanelistInviteRepository extends JpaRepository<PanelistInvite, Long> {

    Optional<PanelistInvite> findByToken(String token);

    List<PanelistInvite> findByUsedFalseAndExpiresAtAfter(LocalDateTime now);
}
