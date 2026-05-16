package com.ticketbooking.performance.repository;

import com.ticketbooking.performance.domain.Performance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceRepository extends JpaRepository<Performance, UUID> {
}
