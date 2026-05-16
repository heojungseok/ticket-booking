package com.ticketbooking.booking.event;

import java.util.UUID;

public record BookingPaidPayload(
        UUID bookingId,
        UUID userId,
        UUID performanceId,
        UUID seatId
) {
}
