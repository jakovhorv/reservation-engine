package com.example.reservation_engine.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception
    ){
        ApiError error = new ApiError(
                exception.getStatusCode().value(),
                exception.getReason(),
                Instant.now()
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ){
        ApiError error = new ApiError(
                exception.getStatusCode().value(),
                exception.getBindingResult()
                        .getAllErrors()
                        .getFirst()
                        .getDefaultMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(error);
    }
}
