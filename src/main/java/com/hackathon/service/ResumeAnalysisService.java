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
    private final GeminiCircuitBreaker circuitBreaker;

    public ResumeAnalysisService(RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${gemini.api-key:}") String apiKey,
                                 @Value("${gemini.model:gemini-2.5-flash}") String model,
                                 GeminiCircuitBreaker circuitBreaker) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.circuitBreaker = circuitBreaker;
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
        if (circuitBreaker.isOpen()) {
            throw new BadRequestException("Gemini API circuit breaker is open — too many recent failures. Will retry automatically.");
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
            // Retrieve as raw String to avoid content-type mismatch errors
            // (Gemini may return application/octet-stream even when the body is JSON)
            String rawBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawBody)) {
                throw new BadRequestException("Gemini returned an empty response.");
            }
            log.info("ResumeAnalysis phase=gemini_raw_response_received chars={}", rawBody.length());

            JsonNode response = objectMapper.readTree(rawBody);
            String jsonText = response.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text").asText(null);
            if (!StringUtils.hasText(jsonText)) {
                log.error("ResumeAnalysis gemini_response_body={}", rawBody);
                throw new BadRequestException("Gemini did not return resume analysis JSON.");
            }
            log.info("ResumeAnalysis phase=gemini_response_received chars={}", jsonText.length());
            JsonNode result = normalizeAnalysis(objectMapper.readTree(jsonText));
            circuitBreaker.recordSuccess();
            return result;
        } catch (IOException exception) {
            circuitBreaker.recordFailure();
            throw new BadRequestException("Gemini returned invalid resume analysis JSON: " + exception.getMessage());
        } catch (RestClientException exception) {
            circuitBreaker.recordFailure();
            throw new BadRequestException("Gemini resume analysis failed: " + exception.getMessage());
        }
    }

    private String prompt(String resumeText, Integer experienceYears) {
        int declaredExperience = experienceYears == null ? 0 : experienceYears;
        return """
                You are a strict technical recruiter evaluating a resume for a competitive hackathon/recruitment event.
                Be HARSH and OBJECTIVE. Do not give benefit of the doubt. Only reward what is clearly evidenced.

                Return ONLY valid JSON with this exact shape — no markdown, no explanation:
                {
                  "summary": "2-3 sentence objective assessment of the candidate",
                  "skills": ["skill1", "skill2"],
                  "recommendation": "STRONG_HIRE | HIRE | BORDERLINE | REJECT — followed by one sentence justification",
                  "strengths": ["specific strength backed by evidence in resume"],
                  "gaps": ["specific gap or concern"],
                  "redFlags": ["any of: buzzword stuffing, unexplained gaps, vague claims, inflated titles, no measurable outcomes"],
                  "experienceYearsDetected": 0,
                  "scores": {
                    "technicalDepth": 0,
                    "projectQuality": 0,
                    "achievementClarity": 0,
                    "skillsAuthenticity": 0,
                    "consistencyScore": 0
                  }
                }

                SCORING RULES — rate each dimension 0 to 10:
                technicalDepth: Depth of technical knowledge. 8-10 only if candidate demonstrates deep expertise
                  with architecture decisions, advanced concepts, or specialized knowledge — NOT just listing frameworks.
                  Listing React + Node + Python with no depth evidence = max 4.
                projectQuality: Complexity and real-world impact of projects. 8-10 only for production systems,
                  significant scale, or novel work. Tutorial/CRUD projects = max 3. Academic projects = max 5.
                achievementClarity: Are outcomes MEASURABLE? (e.g., "reduced latency by 40%%", "served 10k users").
                  Vague claims like "improved performance" = 2. No achievements at all = 0.
                skillsAuthenticity: Do the listed skills appear to be genuinely used in projects/work?
                  Skills listed with zero supporting evidence = 1 each. Penalize buzzword-only resumes heavily.
                consistencyScore: Does the timeline make sense? Do skills match claimed experience level?
                  Mismatch between declared (%d years) and what resume evidence suggests = deduct heavily.

                STRICT RULES:
                - skills: list only top 10 skills that are EVIDENCED in the resume. Do not list skills mentioned once with no context.
                - strengths: only add a strength if there is EXPLICIT evidence for it in the resume text.
                - gaps: be thorough — missing quantification, shallow projects, no leadership, narrow skillset etc.
                - redFlags: flag anything suspicious. Err on the side of caution.
                - experienceYearsDetected: infer from dates in resume ONLY. If no dates, return 0.

                Declared experience years: %d
                Resume text:
                %s
                """.formatted(declaredExperience, declaredExperience, resumeText);
    }

    private JsonNode normalizeAnalysis(JsonNode analysis) {
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("summary", text(analysis, "summary"));
        normalized.set("skills", array(analysis, "skills"));
        normalized.put("aiScore", 0);
        normalized.put("recommendation", text(analysis, "recommendation"));
        normalized.set("strengths", array(analysis, "strengths"));
        normalized.set("gaps", array(analysis, "gaps"));
        normalized.set("redFlags", array(analysis, "redFlags"));
        normalized.put("experienceYearsDetected", Math.max(0, analysis.path("experienceYearsDetected").asInt(0)));

        // Normalise scores sub-object; clamp each dimension to [0, 10]
        JsonNode rawScores = analysis.path("scores");
        ObjectNode scores = objectMapper.createObjectNode();
        scores.put("technicalDepth",     clamp(rawScores.path("technicalDepth").asInt(0), 0, 10));
        scores.put("projectQuality",     clamp(rawScores.path("projectQuality").asInt(0), 0, 10));
        scores.put("achievementClarity", clamp(rawScores.path("achievementClarity").asInt(0), 0, 10));
        scores.put("skillsAuthenticity", clamp(rawScores.path("skillsAuthenticity").asInt(0), 0, 10));
        scores.put("consistencyScore",   clamp(rawScores.path("consistencyScore").asInt(0), 0, 10));
        normalized.set("scores", scores);
        return normalized;
    }

    /**
     * Strict dimension-based scoring. No free base score — every point must be earned.
     *
     * Positive components (max 95):
     *   technicalDepth      0-10  × 2.0  = max 20
     *   projectQuality      0-10  × 2.0  = max 20
     *   achievementClarity  0-10  × 1.5  = max 15
     *   skillsAuthenticity  0-10  × 1.5  = max 15
     *   consistencyScore    0-10  × 1.0  = max 10
     *   experienceScore                  = max 10 (hard-capped, penalised for mismatch)
     *   strengthsBonus      1 per unique evidenced strength = max 5
     *
     * Penalties:
     *   redFlagPenalty  -6 per red flag  = max -30
     *   gapPenalty      -3 per gap       = max -15
     *   mismatchPenalty -5 if declared experience > detected by >2 years
     */
    private JsonNode addCalculatedScore(JsonNode analysis, Integer declaredExperienceYears) {
        ObjectNode scored = analysis.deepCopy();

        // Read Gemini-rated dimensions
        JsonNode s = scored.path("scores");
        int technicalDepth      = clamp(s.path("technicalDepth").asInt(0), 0, 10);
        int projectQuality      = clamp(s.path("projectQuality").asInt(0), 0, 10);
        int achievementClarity  = clamp(s.path("achievementClarity").asInt(0), 0, 10);
        int skillsAuthenticity  = clamp(s.path("skillsAuthenticity").asInt(0), 0, 10);
        int consistencyScore    = clamp(s.path("consistencyScore").asInt(0), 0, 10);

        int detectedExperience  = Math.max(0, scored.path("experienceYearsDetected").asInt(0));
        int declaredExperience  = declaredExperienceYears == null ? 0 : Math.max(0, declaredExperienceYears);
        int strengthsCount      = scored.path("strengths").size();
        int gapsCount           = scored.path("gaps").size();
        int redFlagsCount       = scored.path("redFlags").size();

        // Positive component scores
        int techScore        = (int) Math.round(technicalDepth     * 2.0);
        int projScore        = (int) Math.round(projectQuality     * 2.0);
        int achievScore      = (int) Math.round(achievementClarity * 1.5);
        int authScore        = (int) Math.round(skillsAuthenticity * 1.5);
        int consScore        = consistencyScore;
        // Experience: reward real detected years, cap at 10, penalise over-claiming
        int experienceScore  = Math.min(10, detectedExperience * 2);
        int strengthsBonus   = Math.min(5, strengthsCount);

        // Penalties
        int redFlagPenalty   = Math.min(30, redFlagsCount * 6);
        int gapPenalty       = Math.min(15, gapsCount * 3);
        // Mismatch penalty: declared years significantly exceed what resume evidence shows
        int mismatchPenalty  = (declaredExperience - detectedExperience > 2) ? 5 : 0;

        int rawScore = techScore + projScore + achievScore + authScore + consScore
                + experienceScore + strengthsBonus
                - redFlagPenalty - gapPenalty - mismatchPenalty;
        int aiScore = clamp(rawScore, 0, 95);

        scored.put("aiScore", aiScore);
        ObjectNode breakdown = scored.putObject("scoreBreakdown");
        breakdown.put("technicalDepthScore",   techScore);
        breakdown.put("projectQualityScore",   projScore);
        breakdown.put("achievementClarityScore", achievScore);
        breakdown.put("skillsAuthenticityScore", authScore);
        breakdown.put("consistencyScore",      consScore);
        breakdown.put("experienceScore",        experienceScore);
        breakdown.put("strengthsBonus",         strengthsBonus);
        breakdown.put("redFlagPenalty",        -redFlagPenalty);
        breakdown.put("gapPenalty",            -gapPenalty);
        breakdown.put("mismatchPenalty",       -mismatchPenalty);
        breakdown.put("declaredExperienceYears", declaredExperience);
        breakdown.put("detectedExperienceYears", detectedExperience);
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
