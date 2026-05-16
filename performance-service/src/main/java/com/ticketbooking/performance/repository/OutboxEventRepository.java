package com.ticketbooking.performance.repository;

import com.ticketbooking.performance.domain.OutboxEvent;
import com.ticketbooking.performance.domain.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
