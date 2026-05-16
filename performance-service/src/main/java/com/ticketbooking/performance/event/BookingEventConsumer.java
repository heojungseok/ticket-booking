package com.ticketbooking.performance.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.performance.domain.ProcessedEvent;
import com.ticketbooking.performance.domain.ProcessedEventId;
import com.ticketbooking.performance.domain.Seat;
import com.ticketbooking.performance.repository.ProcessedEventRepository;
import com.ticketbooking.performance.repository.SeatRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingEventConsumer {

    private static final String CONSUMER_NAME = "performance-booking-paid-consumer";

    private final ObjectMapper objectMapper;
    private final SeatRepository seatRepository;
    private final ProcessedEventRepository processedEventRepository;

    public BookingEventConsumer(
            ObjectMapper objectMapper,
            SeatRepository seatRepository,
            ProcessedEventRepository processedEventRepository
    ) {
        this.objectMapper = objectMapper;
        this.seatRepository = seatRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = EventTopics.BOOKING_EVENTS, groupId = "performance-service")
    @Transactional
    public void consume(String message) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
        if (!"BOOKING_PAID".equals(envelope.eventType()) || alreadyProcessed(envelope)) {
            return;
        }
        BookingPaidPayload payload = objectMapper.treeToValue(envelope.payload(), BookingPaidPayload.class);
        Seat seat = seatRepository.findById(payload.seatId())
                .orElseThrow(() -> new EntityNotFoundException("seat not found: " + payload.seatId()));
        seat.markBooked();
        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), CONSUMER_NAME, envelope.eventType()));
    }

    private boolean alreadyProcessed(EventEnvelope envelope) {
        return processedEventRepository.existsById(new ProcessedEventId(envelope.eventId(), CONSUMER_NAME));
    }
}
