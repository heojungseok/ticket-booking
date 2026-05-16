package com.ticketbooking.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String provider;
    private String providerTransactionId;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    public Payment(Booking booking, BigDecimal amount) {
        this.booking = booking;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
        this.provider = "MOCK";
        this.requestedAt = LocalDateTime.now();
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

    public void succeed(String transactionId) {
        status = PaymentStatus.SUCCEEDED;
        providerTransactionId = transactionId;
        completedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        status = PaymentStatus.FAILED;
        failureReason = reason;
        completedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }
}
