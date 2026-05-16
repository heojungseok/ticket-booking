package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.domain.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
