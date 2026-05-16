package com.ticketbooking.booking.payment;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentClient implements PaymentClient {

    private final Random random = new Random();

    @Override
    public PaymentResult pay(UUID bookingId, BigDecimal amount) {
        sleep();
        if (random.nextInt(10) == 0) {
            return PaymentResult.failure("mock payment failed");
        }
        return PaymentResult.success("mock-" + bookingId);
    }

    private void sleep() {
        try {
            Thread.sleep(1000 + random.nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("payment interrupted", e);
        }
    }
}
