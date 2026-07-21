package com.hackathon.config;

import com.hackathon.entity.FeedbackFieldType;
import com.hackathon.entity.FeedbackTemplate;
import com.hackathon.entity.FeedbackType;
import com.hackathon.repository.FeedbackTemplateRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FeedbackTemplateSeeder {

    private final FeedbackTemplateRepository feedbackTemplateRepository;

    public FeedbackTemplateSeeder(FeedbackTemplateRepository feedbackTemplateRepository) {
        this.feedbackTemplateRepository = feedbackTemplateRepository;
    }

    @PostConstruct
    public void seedDefaults() {
        seedByType(FeedbackType.DESIGN, List.of(
                field("uiUxUnderstanding", FeedbackFieldType.RATING, true),
                field("wireframingSkills", FeedbackFieldType.RATING, true),
                field("creativity", FeedbackFieldType.RATING, true),
                field("designToolsKnowledge", FeedbackFieldType.RATING, true),
                field("comments", FeedbackFieldType.TEXT, false)
        ));

        seedByType(FeedbackType.DEVELOPMENT, List.of(
                field("codingSkills", FeedbackFieldType.RATING, true),
                field("problemSolving", FeedbackFieldType.RATING, true),
                field("databaseKnowledge", FeedbackFieldType.RATING, true),
                field("apiDevelopment", FeedbackFieldType.RATING, true),
                field("codeQuality", FeedbackFieldType.RATING, true),
                field("comments", FeedbackFieldType.TEXT, false)
        ));

        seedByType(FeedbackType.FINAL_REVIEW, List.of(
                field("projectCompletionStatus", FeedbackFieldType.RATING, true),
                field("featureImplementation", FeedbackFieldType.RATING, true),
                field("presentationSkills", FeedbackFieldType.RATING, true),
                field("teamCollaboration", FeedbackFieldType.RATING, true),
                field("finalComments", FeedbackFieldType.TEXT, false)
        ));
    }

    private void seedByType(FeedbackType feedbackType, List<FeedbackTemplate> templates) {
        for (FeedbackTemplate template : templates) {
            if (!feedbackTemplateRepository.existsByFeedbackTypeAndFieldName(feedbackType, template.getFieldName())) {
                template.setFeedbackType(feedbackType);
                feedbackTemplateRepository.save(template);
            }
        }
    }

    private FeedbackTemplate field(String fieldName, FeedbackFieldType fieldType, boolean required) {
        return FeedbackTemplate.builder()
                .fieldName(fieldName)
                .fieldType(fieldType)
                .isRequired(required)
                .build();
    }
}
