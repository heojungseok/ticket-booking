package com.ticketbooking.booking.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SeatCreatedPayload(
        UUID performanceId,
        List<SeatPayload> seats
) {

    public record SeatPayload(
            UUID seatId,
            String section,
            String rowName,
            String seatNumber,
            BigDecimal price,
            String status
    ) {
    }
}
