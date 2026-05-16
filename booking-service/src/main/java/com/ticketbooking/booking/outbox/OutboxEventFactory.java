package com.ticketbooking.booking.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.booking.domain.OutboxEvent;
import com.ticketbooking.booking.event.EventEnvelope;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent create(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType,
                LocalDateTime.now(),
                "booking-service",
                objectMapper.valueToTree(payload)
        );
        return new OutboxEvent(eventId, aggregateType, aggregateId, eventType, objectMapper.valueToTree(envelope));
    }
}
