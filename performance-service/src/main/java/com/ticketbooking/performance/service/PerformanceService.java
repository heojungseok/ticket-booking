package com.ticketbooking.performance.service;

import com.ticketbooking.performance.domain.Performance;
import com.ticketbooking.performance.domain.PerformanceStatus;
import com.ticketbooking.performance.dto.CreatePerformanceRequest;
import com.ticketbooking.performance.dto.PerformanceResponse;
import com.ticketbooking.performance.repository.PerformanceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceService {

    private final PerformanceRepository performanceRepository;

    public PerformanceService(PerformanceRepository performanceRepository) {
        this.performanceRepository = performanceRepository;
    }

    @Transactional
    public PerformanceResponse create(CreatePerformanceRequest request) {
        Performance performance = new Performance(
                request.title(),
                request.description(),
                request.venueName(),
                request.startsAt(),
                request.saleType(),
                request.saleStartsAt(),
                request.saleEndsAt(),
                request.status() == null ? PerformanceStatus.DRAFT : request.status()
        );
        return PerformanceResponse.from(performanceRepository.save(performance));
    }

    @Transactional(readOnly = true)
    public Performance get(UUID performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new EntityNotFoundException("performance not found: " + performanceId));
    }
}
