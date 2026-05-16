package com.ticketbooking.booking.dto;

import com.ticketbooking.booking.domain.Booking;
import com.ticketbooking.booking.domain.BookingStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record BookingResponse(
        UUID bookingId,
        UUID userId,
        UUID performanceId,
        UUID seatId,
        BigDecimal price,
        BookingStatus status
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getPerformanceId(),
                booking.getSeatId(),
                booking.getPrice(),
                booking.getStatus()
        );
    }
}
