package com.ticketbooking.performance.service;

import com.ticketbooking.performance.domain.OutboxEvent;
import com.ticketbooking.performance.domain.Performance;
import com.ticketbooking.performance.domain.Seat;
import com.ticketbooking.performance.dto.CreateSeatsBatchRequest;
import com.ticketbooking.performance.dto.SeatResponse;
import com.ticketbooking.performance.event.SeatCreatedPayload;
import com.ticketbooking.performance.outbox.OutboxEventFactory;
import com.ticketbooking.performance.repository.OutboxEventRepository;
import com.ticketbooking.performance.repository.SeatRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

    private final PerformanceService performanceService;
    private final SeatRepository seatRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    public SeatService(
            PerformanceService performanceService,
            SeatRepository seatRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxEventFactory outboxEventFactory
    ) {
        this.performanceService = performanceService;
        this.seatRepository = seatRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
    }

    @Transactional
    public List<SeatResponse> createBatch(UUID performanceId, CreateSeatsBatchRequest request) {
        validateRange(request);
        Performance performance = performanceService.get(performanceId);
        List<Seat> seats = new ArrayList<>();
        for (int number = request.startNumber(); number <= request.endNumber(); number++) {
            seats.add(new Seat(
                    performance,
                    request.section(),
                    request.rowName(),
                    String.valueOf(number),
                    request.price()
            ));
        }
        List<Seat> savedSeats = seatRepository.saveAll(seats);
        outboxEventRepository.save(createSeatsCreatedEvent(performanceId, savedSeats));
        return savedSeats.stream().map(SeatResponse::from).toList();
    }

    private void validateRange(CreateSeatsBatchRequest request) {
        if (request.endNumber() < request.startNumber()) {
            throw new IllegalArgumentException("endNumber must be greater than or equal to startNumber");
        }
    }

    private OutboxEvent createSeatsCreatedEvent(UUID performanceId, List<Seat> seats) {
        List<SeatCreatedPayload.SeatPayload> payloadSeats = seats.stream()
                .map(seat -> new SeatCreatedPayload.SeatPayload(
                        seat.getId(),
                        seat.getSection(),
                        seat.getRowName(),
                        seat.getSeatNumber(),
                        seat.getPrice(),
                        seat.getStatus().name()
                ))
                .toList();
        return outboxEventFactory.create(
                "SEAT",
                performanceId,
                "SEATS_CREATED",
                new SeatCreatedPayload(performanceId, payloadSeats)
        );
    }
}
