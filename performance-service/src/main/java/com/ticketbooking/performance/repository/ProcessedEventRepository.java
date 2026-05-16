package com.ticketbooking.performance.repository;

import com.ticketbooking.performance.domain.ProcessedEvent;
import com.ticketbooking.performance.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
