package com.ticketbooking.performance.dto;

import com.ticketbooking.performance.domain.Performance;
import com.ticketbooking.performance.domain.PerformanceStatus;
import com.ticketbooking.performance.domain.SaleType;
import java.util.UUID;

public record PerformanceResponse(
        UUID id,
        String title,
        SaleType saleType,
        PerformanceStatus status
) {

    public static PerformanceResponse from(Performance performance) {
        return new PerformanceResponse(
                performance.getId(),
                performance.getTitle(),
                performance.getSaleType(),
                performance.getStatus()
        );
    }
}
