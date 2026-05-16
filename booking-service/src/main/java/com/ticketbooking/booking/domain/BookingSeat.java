package com.ticketbooking.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    private UUID seatId;

    private UUID performanceId;
    private String section;
    private String rowName;
    private String seatNumber;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private BookingSeatStatus status;

    private LocalDateTime syncedAt;

    protected BookingSeat() {
    }

    public BookingSeat(
            UUID seatId,
            UUID performanceId,
            String section,
            String rowName,
            String seatNumber,
            BigDecimal price,
            BookingSeatStatus status
    ) {
        this.seatId = seatId;
        this.performanceId = performanceId;
        this.section = section;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = status;
        this.syncedAt = LocalDateTime.now();
    }

    public void sync(String section, String rowName, String seatNumber, BigDecimal price, BookingSeatStatus status) {
        this.section = section;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = status;
        this.syncedAt = LocalDateTime.now();
    }

    public void markBooked() {
        status = BookingSeatStatus.BOOKED;
        syncedAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        return status == BookingSeatStatus.AVAILABLE;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public UUID getPerformanceId() {
        return performanceId;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
