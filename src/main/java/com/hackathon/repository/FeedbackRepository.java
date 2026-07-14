package com.hackathon.repository;

import com.hackathon.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    void deleteByParticipantId(Long participantId);

    void deleteByPanelistId(Long panelistId);
}
