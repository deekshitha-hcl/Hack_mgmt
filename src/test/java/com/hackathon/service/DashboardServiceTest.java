package com.hackathon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.hackathon.entity.Event;
import com.hackathon.entity.Feedback;
import com.hackathon.entity.FeedbackType;
import com.hackathon.entity.Panelist;
import com.hackathon.entity.Participant;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EmailLogRepository;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.PanelistRepository;
import com.hackathon.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private PanelistRepository panelistRepository;

    @Mock
    private EmailLogRepository emailLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void panelistDashboardReturnsExpectedSummary() {
        Panelist panelist = Panelist.builder().id(5L).name("Deekshi").email("deekshi@example.com").build();
        Participant participant1 = Participant.builder().id(12L).eventId(101L).build();
        Participant participant2 = Participant.builder().id(13L).eventId(101L).build();
        Event event = Event.builder().id(101L).name("Buildathon").build();

        when(panelistRepository.findById(5L)).thenReturn(Optional.of(panelist));
        when(participantRepository.findAllById(List.of(12L, 13L))).thenReturn(List.of(participant1, participant2));
        when(eventRepository.findAllById(List.of(101L))).thenReturn(List.of(event));
        when(feedbackRepository.findByPanelistIdOrderBySubmittedAtAsc(5L)).thenReturn(List.of(
                Feedback.builder().id(1L).panelistId(5L).participantId(12L).feedbackType(FeedbackType.DESIGN)
                        .submittedAt(LocalDateTime.parse("2026-07-20T10:30:00")).build(),
                Feedback.builder().id(2L).panelistId(5L).participantId(13L).feedbackType(FeedbackType.DEVELOPMENT)
                        .submittedAt(LocalDateTime.parse("2026-07-20T11:00:00")).build()));

        var response = dashboardService.panelistDashboard(5L);

        assertThat(response.panelistId()).isEqualTo(5L);
        assertThat(response.eventsHandled()).isEqualTo(1L);
        assertThat(response.participantsFeedbackGiven()).isEqualTo(2L);
        assertThat(response.feedbackSubmitted()).isEqualTo(2L);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).eventId()).isEqualTo(101L);
        assertThat(response.events().get(0).participantCount()).isEqualTo(2L);
    }

    @Test
    void panelistDashboardThrowsWhenPanelistMissing() {
        when(panelistRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dashboardService.panelistDashboard(5L));
    }
}