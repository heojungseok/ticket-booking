package com.ticketbooking.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID performanceId,
        @NotNull UUID seatId,
        @NotNull @Positive BigDecimal amount
) {
}
