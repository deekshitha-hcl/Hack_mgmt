package com.hackathon.repository;

import com.hackathon.entity.Feedback;
import com.hackathon.entity.FeedbackType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    boolean existsByParticipantIdAndFeedbackType(Long participantId, FeedbackType feedbackType);

    List<Feedback> findByPanelistIdOrderBySubmittedAtAsc(Long panelistId);

    List<Feedback> findByParticipantIdOrderBySubmittedAtAsc(Long participantId);

    void deleteByParticipantId(Long participantId);

    void deleteByPanelistId(Long panelistId);
}
