package com.hackathon.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.dto.ParticipantCheckInRequest;
import com.hackathon.dto.ParticipantCheckInResponse;
import com.hackathon.dto.ParticipantRegistrationRequest;
import com.hackathon.entity.Participant;
import com.hackathon.entity.ParticipantStatus;
import com.hackathon.security.CustomUserDetailsService;
import com.hackathon.security.JwtAuthenticationFilter;
import com.hackathon.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParticipantController.class)
@AutoConfigureMockMvc(addFilters = false)
class ParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParticipantService participantService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void registerAcceptsMultipartFormDataWithFiles() throws Exception {
        ParticipantRegistrationRequest request = registrationRequest();
        MockMultipartFile resume = new MockMultipartFile(
                "resume", "resume.txt", MediaType.TEXT_PLAIN_VALUE, "Java Spring".getBytes());
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "photo".getBytes());

        when(participantService.register(refEq(request), refEq(resume), refEq(photo)))
                .thenReturn(participant());

        mockMvc.perform(multipart("/api/participants/register")
                        .file(resume)
                        .file(photo)
                        .param("eventId", "1")
                        .param("name", "Test User")
                        .param("email", "test@example.com")
                        .param("techStack", "Java, Spring")
                        .param("phone", "9876543210")
                        .param("experienceYears", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantCode").value("PART-0002"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(participantService).register(refEq(request), refEq(resume), refEq(photo));
    }

    @Test
    void registerAcceptsJsonWhenNoFilesAreUploaded() throws Exception {
        ParticipantRegistrationRequest request = registrationRequest();

        when(participantService.register(refEq(request), isNull(), isNull()))
                .thenReturn(participant());

        mockMvc.perform(post("/api/participants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantCode").value("PART-0002"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(participantService).register(refEq(request), isNull(), isNull());
    }

    @Test
    void verifyAndCheckInAcceptsJsonRequest() throws Exception {
        ParticipantCheckInRequest request = new ParticipantCheckInRequest("test@example.com", "PART-0002");
        ParticipantCheckInResponse response = new ParticipantCheckInResponse(
                2L,
                "PART-0002",
                "Test User",
                "test@example.com",
                "CHECKED_IN",
                "Check-in successful. Resume analysis has been queued.",
                "/participant/dashboard",
                "/support",
                true
        );

        when(participantService.verifyAndCheckIn(refEq(request))).thenReturn(response);

        mockMvc.perform(post("/api/participants/check-in/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.resumeAnalysisTriggered").value(true));

        verify(participantService).verifyAndCheckIn(refEq(request));
    }

    private ParticipantRegistrationRequest registrationRequest() {
        return new ParticipantRegistrationRequest(1L, "Test User", "test@example.com", "Java, Spring", "9876543210", 2);
    }

    private Participant participant() {
        return Participant.builder()
                .id(2L)
                .participantCode("PART-0002")
                .eventId(1L)
                .name("Test User")
                .email("test@example.com")
                .phone("9876543210")
                .experienceYears(2)
                .status(ParticipantStatus.REGISTERED)
                .build();
    }
}
