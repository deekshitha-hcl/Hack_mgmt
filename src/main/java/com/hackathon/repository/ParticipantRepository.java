package com.hackathon.repository;

import com.hackathon.entity.AiAnalysisStatus;
import com.hackathon.entity.Participant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByEventId(Long eventId);

    List<Participant> findByEventIdOrderByExperienceYearsDesc(Long eventId);

    Optional<Participant> findByParticipantCode(String participantCode);

    Optional<Participant> findTopByOrderByIdDesc();

    Optional<Participant> findByEmail(String email);

    Optional<Participant> findByEmailAndParticipantCode(String email, String participantCode);

    @Query(value = "SELECT * FROM participants WHERE ai_analysis_pending = true LIMIT ?1", nativeQuery = true)
    List<Participant> findPendingAiAnalysisBatch(int batchSize);

    @Query(value = """
            SELECT * FROM participants
            WHERE ai_analysis_pending = true
              AND (ai_analysis_attempt_count IS NULL OR ai_analysis_attempt_count < ?1)
              AND (ai_analysis_status IS NULL OR ai_analysis_status NOT IN ('PROCESSING'))
            ORDER BY
              COALESCE(ai_analysis_attempt_count, 0) ASC,
              COALESCE(experience_years, 0) DESC,
              id DESC
            LIMIT ?2
            """, nativeQuery = true)
    List<Participant> findPendingAiAnalysisWithRetry(int maxRetries, int batchSize);

    long countByAiAnalysisStatus(AiAnalysisStatus status);

    long countByAiAnalysisPendingTrue();

    @Query(value = "SELECT COUNT(*) FROM participants WHERE ai_analysis_status = 'FAILED' AND ai_analysis_pending = false AND ai_analysis_attempt_count >= ?1", nativeQuery = true)
    long countAbandoned(int maxRetries);
}
