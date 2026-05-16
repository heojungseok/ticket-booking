package com.ticketbooking.performance.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@IdClass(ProcessedEventId.class)
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Id
    private String consumerName;

    private String eventType;
    private LocalDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumerName, String eventType) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }
}
