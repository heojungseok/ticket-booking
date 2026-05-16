package com.ticketbooking.performance.controller;

import com.ticketbooking.performance.dto.CreatePerformanceRequest;
import com.ticketbooking.performance.dto.CreateSeatsBatchRequest;
import com.ticketbooking.performance.dto.PerformanceResponse;
import com.ticketbooking.performance.dto.SeatResponse;
import com.ticketbooking.performance.service.PerformanceService;
import com.ticketbooking.performance.service.SeatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/performances")
public class PerformanceController {

    private final PerformanceService performanceService;
    private final SeatService seatService;

    public PerformanceController(PerformanceService performanceService, SeatService seatService) {
        this.performanceService = performanceService;
        this.seatService = seatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceResponse create(@Valid @RequestBody CreatePerformanceRequest request) {
        return performanceService.create(request);
    }

    @PostMapping("/{performanceId}/seats/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> createSeats(
            @PathVariable UUID performanceId,
            @Valid @RequestBody CreateSeatsBatchRequest request
    ) {
        return seatService.createBatch(performanceId, request);
    }
}
