package com.ticketbooking.booking.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentClient {

    PaymentResult pay(UUID bookingId, BigDecimal amount);
}
