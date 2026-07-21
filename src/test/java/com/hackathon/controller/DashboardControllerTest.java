package com.hackathon.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.dto.PanelistDashboardEventSummary;
import com.hackathon.dto.PanelistDashboardResponse;
import com.hackathon.security.CustomUserDetailsService;
import com.hackathon.security.JwtAuthenticationFilter;
import com.hackathon.service.DashboardService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private CorsConfigurationSource corsConfigurationSource;

    @MockBean
    private SecurityFilterChain securityFilterChain;

    @Test
    void panelistMeEndpointReturnsDashboard() throws Exception {
        when(dashboardService.panelistDashboardByEmail("deekshi@example.com")).thenReturn(
                new PanelistDashboardResponse(
                        5L,
                        "Deekshi",
                        "deekshi@example.com",
                        1,
                        3,
                        3,
                        List.of(new PanelistDashboardEventSummary(
                                101L,
                                "Buildathon",
                                3,
                                3,
                                List.of(com.hackathon.entity.FeedbackType.DESIGN),
                                LocalDateTime.parse("2026-07-20T11:00:00")))));

        mockMvc.perform(get("/api/dashboard/panelist/me")
                        .with(user("deekshi@example.com").roles("PANELIST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panelistEmail").value("deekshi@example.com"))
                .andExpect(jsonPath("$.eventsHandled").value(1));
    }
}