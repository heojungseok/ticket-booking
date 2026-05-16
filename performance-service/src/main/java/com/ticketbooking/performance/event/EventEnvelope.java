package com.ticketbooking.performance.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        LocalDateTime occurredAt,
        String producer,
        JsonNode payload
) {
}
