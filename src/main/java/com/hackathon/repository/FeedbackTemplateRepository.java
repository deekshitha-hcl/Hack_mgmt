package com.hackathon.repository;

import com.hackathon.entity.FeedbackTemplate;
import com.hackathon.entity.FeedbackType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackTemplateRepository extends JpaRepository<FeedbackTemplate, Long> {

    List<FeedbackTemplate> findByFeedbackTypeOrderByIdAsc(FeedbackType feedbackType);

    boolean existsByFeedbackTypeAndFieldName(FeedbackType feedbackType, String fieldName);
}
