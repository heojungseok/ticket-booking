package com.ticketbooking.booking.controller;

import com.ticketbooking.booking.exception.BookingException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(BookingException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleBookingException(BookingException exception) {
        return new ApiErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("invalid request");
        return new ApiErrorResponse(message);
    }

    public record ApiErrorResponse(String message) {
    }
}
