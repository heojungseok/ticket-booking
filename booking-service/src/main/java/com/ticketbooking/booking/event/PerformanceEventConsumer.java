package com.ticketbooking.booking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.booking.domain.BookingSeat;
import com.ticketbooking.booking.domain.BookingSeatStatus;
import com.ticketbooking.booking.domain.ProcessedEvent;
import com.ticketbooking.booking.domain.ProcessedEventId;
import com.ticketbooking.booking.repository.BookingSeatRepository;
import com.ticketbooking.booking.repository.ProcessedEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PerformanceEventConsumer {

    private static final String CONSUMER_NAME = "booking-seats-created-consumer";

    private final ObjectMapper objectMapper;
    private final BookingSeatRepository bookingSeatRepository;
    private final ProcessedEventRepository processedEventRepository;

    public PerformanceEventConsumer(
            ObjectMapper objectMapper,
            BookingSeatRepository bookingSeatRepository,
            ProcessedEventRepository processedEventRepository
    ) {
        this.objectMapper = objectMapper;
        this.bookingSeatRepository = bookingSeatRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = EventTopics.PERFORMANCE_EVENTS, groupId = "booking-service")
    @Transactional
    public void consume(String message) throws Exception {
        EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
        if (!"SEATS_CREATED".equals(envelope.eventType()) || alreadyProcessed(envelope)) {
            return;
        }
        SeatCreatedPayload payload = objectMapper.treeToValue(envelope.payload(), SeatCreatedPayload.class);
        for (SeatCreatedPayload.SeatPayload seatPayload : payload.seats()) {
            upsertSeat(payload, seatPayload);
        }
        processedEventRepository.save(new ProcessedEvent(envelope.eventId(), CONSUMER_NAME, envelope.eventType()));
    }

    private void upsertSeat(SeatCreatedPayload payload, SeatCreatedPayload.SeatPayload seatPayload) {
        BookingSeatStatus status = BookingSeatStatus.valueOf(seatPayload.status());
        BookingSeat seat = bookingSeatRepository.findById(seatPayload.seatId())
                .orElseGet(() -> new BookingSeat(
                        seatPayload.seatId(),
                        payload.performanceId(),
                        seatPayload.section(),
                        seatPayload.rowName(),
                        seatPayload.seatNumber(),
                        seatPayload.price(),
                        status
                ));
        seat.sync(seatPayload.section(), seatPayload.rowName(), seatPayload.seatNumber(), seatPayload.price(), status);
        bookingSeatRepository.save(seat);
    }

    private boolean alreadyProcessed(EventEnvelope envelope) {
        return processedEventRepository.existsById(new ProcessedEventId(envelope.eventId(), CONSUMER_NAME));
    }
}
