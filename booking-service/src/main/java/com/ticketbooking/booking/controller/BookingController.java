package com.ticketbooking.booking.controller;

import com.ticketbooking.booking.dto.BookingResponse;
import com.ticketbooking.booking.dto.CreateBookingRequest;
import com.ticketbooking.booking.service.BookingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return bookingService.create(userId, request);
    }
}
