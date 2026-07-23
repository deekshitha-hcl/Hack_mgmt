package com.hackathon.service;

import com.hackathon.dto.EventRequest;
import com.hackathon.dto.EventUpdateRequest;
import com.hackathon.entity.Event;
import com.hackathon.entity.EventStatus;
import com.hackathon.entity.Participant;
import com.hackathon.entity.Squad;
import com.hackathon.exception.BadRequestException;
import com.hackathon.exception.ResourceNotFoundException;
import com.hackathon.repository.EventRepository;
import com.hackathon.repository.FeedbackRepository;
import com.hackathon.repository.ParticipantRepository;
import com.hackathon.repository.SquadMemberRepository;
import com.hackathon.repository.SquadRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final SquadRepository squadRepository;
    private final SquadMemberRepository squadMemberRepository;
    private final FeedbackRepository feedbackRepository;
    private final QrCodeService qrCodeService;
    private final String baseUrl;

    public EventService(EventRepository eventRepository, ParticipantRepository participantRepository,
                        SquadRepository squadRepository, SquadMemberRepository squadMemberRepository,
                        FeedbackRepository feedbackRepository, QrCodeService qrCodeService,
                        @Value("${app.base-qr-url}") String baseUrl) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.squadRepository = squadRepository;
        this.squadMemberRepository = squadMemberRepository;
        this.feedbackRepository = feedbackRepository;
        this.qrCodeService = qrCodeService;
        this.baseUrl = baseUrl;
    }

    // -------------------------------------------------------------------------
    // Status computation
    // -------------------------------------------------------------------------

    /**
     * Derives the event status from the supplied date range using UTC today as
     * the reference point.  Rules:
     * <ul>
     *   <li>If {@code startDate} is null → {@link EventStatus#OPEN} (undated events default to open).</li>
     *   <li>today &lt; startDate → {@link EventStatus#UPCOMING}</li>
     *   <li>today &gt; effectiveEnd → {@link EventStatus#CLOSED}
     *       (effectiveEnd = endDate when provided, otherwise startDate).</li>
     *   <li>Otherwise → {@link EventStatus#OPEN}</li>
     * </ul>
     * Throws {@link BadRequestException} when {@code endDate} is before {@code startDate}.
     */
    EventStatus computeStatus(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            return EventStatus.OPEN;
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException(
                    "endDate (" + endDate + ") must not be before startDate (" + startDate + ")");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (today.isBefore(startDate)) {
            return EventStatus.UPCOMING;
        }
        LocalDate effectiveEnd = endDate != null ? endDate : startDate;
        if (today.isAfter(effectiveEnd)) {
            return EventStatus.CLOSED;
        }
        return EventStatus.OPEN;
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional
    public Event create(EventRequest request) {
        EventStatus status = computeStatus(request.startDate(), request.endDate());
        Event event = Event.builder()
                .name(request.name())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(status)
                .build();
        event = eventRepository.save(event);
        String registrationUrl = baseUrl + "/participants/register?eventId=" + event.getId();
        String checkInQrLandingUrl = baseUrl + "/api/participants/check-in/qr?eventId=" + event.getId();
        event.setRegistrationUrl(registrationUrl);
        event.setQrCodeUrl(qrCodeService.generateQrCode(checkInQrLandingUrl));
        return eventRepository.save(event);
    }

    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    public Event findById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    /**
     * Partially updates an event.  Only non-null fields in the request are
     * applied.  Whenever {@code startDate} or {@code endDate} is modified the
     * status is automatically recalculated from the resulting date range.
     * If {@code cancelled} is true, the event is moved to the terminal
     * {@link EventStatus#CANCELLED} state and stays there unless explicitly
     * changed by another edit flow.
     */
    @Transactional
    public Event update(Long id, EventUpdateRequest request) {
        Event event = findById(id);

        if (request.name() != null) {
            event.setName(request.name());
        }
        if (request.description() != null) {
            event.setDescription(request.description());
        }

        boolean datesChanged = request.startDate() != null || request.endDate() != null;
        if (request.startDate() != null) {
            event.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            event.setEndDate(request.endDate());
        }

        if (Boolean.TRUE.equals(request.cancelled())) {
            event.setStatus(EventStatus.CANCELLED);
        } else if (event.getStatus() != EventStatus.CANCELLED && datesChanged) {
            event.setStatus(computeStatus(event.getStartDate(), event.getEndDate()));
        } else if (event.getStatus() == null) {
            event.setStatus(computeStatus(event.getStartDate(), event.getEndDate()));
        }

        return eventRepository.save(event);
    }

    @Transactional
    public void delete(Long id) {
        Event event = findById(id);

        for (Squad squad : squadRepository.findByEventId(id)) {
            squadMemberRepository.deleteBySquadId(squad.getId());
            squadRepository.delete(squad);
        }

        for (Participant participant : participantRepository.findByEventId(id)) {
            Long participantId = participant.getId();
            feedbackRepository.deleteByParticipantId(participantId);
            squadMemberRepository.deleteByParticipantId(participantId);
            participantRepository.delete(participant);
        }

        eventRepository.delete(event);
    }
}

