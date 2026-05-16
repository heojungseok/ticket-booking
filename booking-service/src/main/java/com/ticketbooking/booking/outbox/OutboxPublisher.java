package com.ticketbooking.booking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.booking.domain.OutboxEvent;
import com.ticketbooking.booking.domain.OutboxStatus;
import com.ticketbooking.booking.event.EventTopics;
import com.ticketbooking.booking.repository.OutboxEventRepository;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(EventTopics.BOOKING_EVENTS, event.getId().toString(), serialize(event)).get();
            event.markPublished();
        } catch (Exception e) {
            event.markFailed(e.getMessage());
        }
    }

    private String serialize(OutboxEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event.getPayload());
    }
}
