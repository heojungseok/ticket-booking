package com.ticketbooking.performance.dto;

import com.ticketbooking.performance.domain.PerformanceStatus;
import com.ticketbooking.performance.domain.SaleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreatePerformanceRequest(
        @NotBlank String title,
        String description,
        @NotBlank String venueName,
        @NotNull LocalDateTime startsAt,
        @NotNull SaleType saleType,
        LocalDateTime saleStartsAt,
        LocalDateTime saleEndsAt,
        PerformanceStatus status
) {
}
