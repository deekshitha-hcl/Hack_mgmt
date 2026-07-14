package com.hackathon.repository;

import com.hackathon.entity.Panelist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelistRepository extends JpaRepository<Panelist, Long> {

    boolean existsByEmail(String email);
}
