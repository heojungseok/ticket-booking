package com.ticketbooking.booking.service;

import com.ticketbooking.booking.domain.Booking;
import com.ticketbooking.booking.domain.BookingSeat;
import com.ticketbooking.booking.domain.BookingSeatStatus;
import com.ticketbooking.booking.domain.OutboxEvent;
import com.ticketbooking.booking.domain.Payment;
import com.ticketbooking.booking.dto.BookingResponse;
import com.ticketbooking.booking.dto.CreateBookingRequest;
import com.ticketbooking.booking.event.BookingPaidPayload;
import com.ticketbooking.booking.exception.BookingException;
import com.ticketbooking.booking.outbox.OutboxEventFactory;
import com.ticketbooking.booking.payment.PaymentClient;
import com.ticketbooking.booking.payment.PaymentResult;
import com.ticketbooking.booking.repository.BookingRepository;
import com.ticketbooking.booking.repository.BookingSeatRepository;
import com.ticketbooking.booking.repository.OutboxEventRepository;
import com.ticketbooking.booking.repository.PaymentRepository;
import java.util.UUID;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private final BookingSeatRepository bookingSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final PaymentClient paymentClient;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public BookingService(
            BookingSeatRepository bookingSeatRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxEventFactory outboxEventFactory,
            PaymentClient paymentClient,
            RedissonClient redissonClient,
            TransactionTemplate transactionTemplate
    ) {
        this.bookingSeatRepository = bookingSeatRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.paymentClient = paymentClient;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
    }

    public BookingResponse create(UUID userId, CreateBookingRequest request) {
        RLock lock = redissonClient.getLock("seat:lock:" + request.seatId());
        try {
            if (!lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new BookingException("seat is busy");
            }
            PendingPayment pending = createPendingPayment(userId, request);
            PaymentResult paymentResult = paymentClient.pay(pending.bookingId(), request.amount());
            return completePayment(pending, paymentResult);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BookingException("failed to acquire seat lock");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private PendingPayment createPendingPayment(UUID userId, CreateBookingRequest request) {
        return transactionTemplate.execute(status -> {
            BookingSeat seat = bookingSeatRepository.findById(request.seatId())
                    .orElseThrow(() -> new BookingException("seat snapshot not found"));
            validateSeat(request, seat);
            Booking booking = bookingRepository.save(new Booking(
                    userId,
                    request.performanceId(),
                    request.seatId(),
                    request.amount()
            ));
            Payment payment = paymentRepository.save(new Payment(booking, request.amount()));
            return new PendingPayment(booking.getId(), payment.getId());
        });
    }

    private void validateSeat(CreateBookingRequest request, BookingSeat seat) {
        if (!seat.getPerformanceId().equals(request.performanceId())) {
            throw new BookingException("seat does not belong to performance");
        }
        if (!seat.isAvailable()) {
            throw new BookingException("seat is not available");
        }
        if (seat.getPrice().compareTo(request.amount()) != 0) {
            throw new BookingException("booking amount does not match seat price");
        }
    }

    private BookingResponse completePayment(PendingPayment pending, PaymentResult result) {
        return transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findById(pending.bookingId())
                    .orElseThrow(() -> new BookingException("booking not found"));
            Payment payment = paymentRepository.findById(pending.paymentId())
                    .orElseThrow(() -> new BookingException("payment not found"));
            if (result.success()) {
                return completeSuccess(booking, payment, result);
            }
            booking.markFailed();
            payment.fail(result.failureReason());
            return BookingResponse.from(booking);
        });
    }

    private BookingResponse completeSuccess(Booking booking, Payment payment, PaymentResult result) {
        BookingSeat seat = bookingSeatRepository.findById(booking.getSeatId())
                .orElseThrow(() -> new BookingException("seat snapshot not found"));
        booking.markPaid();
        payment.succeed(result.transactionId());
        seat.markBooked();
        outboxEventRepository.save(createBookingPaidEvent(booking));
        return BookingResponse.from(booking);
    }

    private OutboxEvent createBookingPaidEvent(Booking booking) {
        BookingPaidPayload payload = new BookingPaidPayload(
                booking.getId(),
                booking.getUserId(),
                booking.getPerformanceId(),
                booking.getSeatId()
        );
        return outboxEventFactory.create("BOOKING", booking.getId(), "BOOKING_PAID", payload);
    }

    private record PendingPayment(UUID bookingId, UUID paymentId) {
    }
}
