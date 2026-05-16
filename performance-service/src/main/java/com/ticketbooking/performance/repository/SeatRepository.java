package com.ticketbooking.performance.repository;

import com.ticketbooking.performance.domain.Seat;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
}
