package com.ticketbooking.performance.dto;

import com.ticketbooking.performance.domain.Seat;
import com.ticketbooking.performance.domain.SeatStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record SeatResponse(
        UUID id,
        UUID performanceId,
        String section,
        String rowName,
        String seatNumber,
        BigDecimal price,
        SeatStatus status
) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getPerformanceId(),
                seat.getSection(),
                seat.getRowName(),
                seat.getSeatNumber(),
                seat.getPrice(),
                seat.getStatus()
        );
    }
}
