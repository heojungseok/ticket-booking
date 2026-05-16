package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.domain.Booking;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
}
