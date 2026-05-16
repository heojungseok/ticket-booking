package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.domain.ProcessedEvent;
import com.ticketbooking.booking.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
