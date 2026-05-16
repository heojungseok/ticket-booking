package com.ticketbooking.booking.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    private String aggregateType;
    private UUID aggregateId;
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    private int retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime leaseExpiresAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, JsonNode payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = OutboxStatus.FAILED;
        retryCount++;
        errorMessage = message;
    }

    public UUID getId() {
        return id;
    }

    public JsonNode getPayload() {
        return payload;
    }
}
