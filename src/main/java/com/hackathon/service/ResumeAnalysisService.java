package com.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hackathon.dto.ResumeAnalysis;
import com.hackathon.exception.BadRequestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ResumeAnalysisService {

    private static final int MAX_RESUME_TEXT_CHARS = 20_000;
    private static final int MAX_SKILLS_SUMMARY_CHARS = 255;

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisService.class);

    private final Tika tika = new Tika();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ResumeAnalysisService(RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${gemini.api-key:}") String apiKey,
                                 @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public ResumeAnalysis analyze(MultipartFile resume, Integer experienceYears) {
        if (resume == null || resume.isEmpty()) {
            log.info("ResumeAnalysis phase=no_resume");
            return emptyAnalysis();
        }

        log.info("ResumeAnalysis phase=text_extraction_start filename={} size={}",
                resume.getOriginalFilename(), resume.getSize());
        String resumeText = extractText(resume);
        log.info("ResumeAnalysis phase=text_extraction_done chars={}", resumeText.length());

        log.info("ResumeAnalysis phase=gemini_start model={}", model);
        JsonNode analysis = analyzeWithGemini(resumeText, experienceYears);
        log.info("ResumeAnalysis phase=gemini_done skillsCount={} extractedExperience={}",
                analysis.path("skills").size(), analysis.path("experienceYearsDetected").asInt(0));

        log.info("ResumeAnalysis phase=score_calculation_start declaredExperience={}", experienceYears);
        JsonNode scoredAnalysis = addCalculatedScore(analysis, experienceYears);
        log.info("ResumeAnalysis phase=score_calculation_done aiScore={}", scoredAnalysis.path("aiScore").asInt(0));
        return toResumeAnalysis(scoredAnalysis);
    }

    private ResumeAnalysis emptyAnalysis() {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("summary", "No resume uploaded.");
        json.putArray("skills");
        json.put("aiScore", 0);
        json.putObject("scoreBreakdown")
                .put("skillScore", 0)
                .put("experienceScore", 0)
                .put("strengthScore", 0)
                .put("gapPenalty", 0);
        json.put("recommendation", "Resume required for AI analysis.");
        json.putArray("strengths");
        json.putArray("gaps");
        json.put("experienceYearsDetected", 0);
        return new ResumeAnalysis("", 0, json.toString());
    }

    private String extractText(MultipartFile resume) {
        try {
            String text = tika.parseToString(resume.getInputStream());
            if (!StringUtils.hasText(text)) {
                throw new BadRequestException("Could not extract text from resume.");
            }
            return text.length() > MAX_RESUME_TEXT_CHARS ? text.substring(0, MAX_RESUME_TEXT_CHARS) : text;
        } catch (IOException | TikaException exception) {
            throw new BadRequestException("Could not extract text from resume: " + exception.getMessage());
        }
    }

    private JsonNode analyzeWithGemini(String resumeText, Integer experienceYears) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BadRequestException("Gemini API key is not configured. Set GEMINI_API_KEY or gemini.api-key.");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent")
                .queryParam("key", apiKey)
                .buildAndExpand(model)
                .toUriString();

        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt(resumeText, experienceYears));

        ObjectNode generationConfig = request.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);

        try {
            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String jsonText = response == null ? null
                    : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (!StringUtils.hasText(jsonText)) {
                throw new BadRequestException("Gemini did not return resume analysis JSON.");
            }
            log.info("ResumeAnalysis phase=gemini_response_received chars={}", jsonText.length());
            return normalizeAnalysis(objectMapper.readTree(jsonText));
        } catch (IOException exception) {
            throw new BadRequestException("Gemini returned invalid resume analysis JSON: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new BadRequestException("Gemini resume analysis failed: " + exception.getMessage());
        }
    }

    private String prompt(String resumeText, Integer experienceYears) {
        int declaredExperience = experienceYears == null ? 0 : experienceYears;
        return """
                Analyze this candidate resume for a hackathon/recruitment event.
                Return only valid JSON with this exact shape:
                {
                  "summary": "short candidate summary",
                  "skills": ["skill"],
                  "recommendation": "short hiring/event recommendation",
                  "strengths": ["strength"],
                  "gaps": ["gap"],
                  "experienceYearsDetected": 0
                }
                Do not calculate aiScore. The backend will calculate aiScore.
                skills must contain only the top 12 concise technical or professional skills.
                experienceYearsDetected must be a non-negative integer inferred from the resume.
                Declared experience years: %d
                Resume text:
                %s
                """.formatted(declaredExperience, resumeText);
    }

    private JsonNode normalizeAnalysis(JsonNode analysis) {
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("summary", text(analysis, "summary"));
        normalized.set("skills", array(analysis, "skills"));
        normalized.put("aiScore", 0);
        normalized.put("recommendation", text(analysis, "recommendation"));
        normalized.set("strengths", array(analysis, "strengths"));
        normalized.set("gaps", array(analysis, "gaps"));
        normalized.put("experienceYearsDetected", Math.max(0, analysis.path("experienceYearsDetected").asInt(0)));
        return normalized;
    }

    private JsonNode addCalculatedScore(JsonNode analysis, Integer declaredExperienceYears) {
        ObjectNode scored = analysis.deepCopy();
        int skillsCount = scored.path("skills").size();
        int strengthsCount = scored.path("strengths").size();
        int gapsCount = scored.path("gaps").size();
        int detectedExperience = scored.path("experienceYearsDetected").asInt(0);
        int declaredExperience = declaredExperienceYears == null ? 0 : Math.max(0, declaredExperienceYears);
        int bestExperience = Math.max(detectedExperience, declaredExperience);

        int skillScore = Math.min(45, skillsCount * 5);
        int experienceScore = Math.min(30, bestExperience * 6);
        int strengthScore = Math.min(15, strengthsCount * 5);
        int gapPenalty = Math.min(20, gapsCount * 4);
        int aiScore = clamp(35 + skillScore + experienceScore + strengthScore - gapPenalty, 0, 100);

        scored.put("aiScore", aiScore);
        ObjectNode breakdown = scored.putObject("scoreBreakdown");
        breakdown.put("baseScore", 35);
        breakdown.put("skillScore", skillScore);
        breakdown.put("experienceScore", experienceScore);
        breakdown.put("strengthScore", strengthScore);
        breakdown.put("gapPenalty", gapPenalty);
        breakdown.put("declaredExperienceYears", declaredExperience);
        breakdown.put("experienceYearsUsed", bestExperience);
        return scored;
    }

    private ResumeAnalysis toResumeAnalysis(JsonNode analysis) {
        List<String> skills = new ArrayList<>();
        analysis.path("skills").forEach(skill -> {
            if (StringUtils.hasText(skill.asText())) {
                skills.add(skill.asText());
            }
        });
        String skillsSummary = truncate(String.join(", ", skills), MAX_SKILLS_SUMMARY_CHARS);
        return new ResumeAnalysis(
                skillsSummary,
                analysis.path("aiScore").asInt(0),
                analysis.toString()
        );
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value : "";
    }

    private ArrayNode array(JsonNode node, String field) {
        ArrayNode values = objectMapper.createArrayNode();
        JsonNode source = node.path(field);
        if (source.isArray()) {
            source.forEach(value -> {
                if (StringUtils.hasText(value.asText())) {
                    values.add(value.asText());
                }
            });
        }
        return values;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
