package com.ticketbooking.performance.domain;

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
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    private String section;
    private String rowName;
    private String seatNumber;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Seat() {
    }

    public Seat(Performance performance, String section, String rowName, String seatNumber, BigDecimal price) {
        this.performance = performance;
        this.section = section;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
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

    public void markBooked() {
        status = SeatStatus.BOOKED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPerformanceId() {
        return performance.getId();
    }

    public String getSection() {
        return section;
    }

    public String getRowName() {
        return rowName;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public SeatStatus getStatus() {
        return status;
    }
}
