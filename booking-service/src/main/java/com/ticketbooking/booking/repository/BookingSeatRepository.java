package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.domain.BookingSeat;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {
}
