package com.ticketbooking.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private UUID performanceId;
    private UUID seatId;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Booking() {
    }

    public Booking(UUID userId, UUID performanceId, UUID seatId, BigDecimal price) {
        this.userId = userId;
        this.performanceId = performanceId;
        this.seatId = seatId;
        this.price = price;
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markPaid() {
        status = BookingStatus.PAID;
        paidAt = LocalDateTime.now();
    }

    public void markFailed() {
        status = BookingStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPerformanceId() {
        return performanceId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
