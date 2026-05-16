package com.ticketbooking.performance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateSeatsBatchRequest(
        @NotBlank String section,
        @NotBlank String rowName,
        @Min(1) int startNumber,
        @Min(1) int endNumber,
        @NotNull @Positive BigDecimal price
) {
}
