package com.example.reservation_engine.exception;

import java.time.Instant;

public record ApiError(
        int status,
        String message,
        Instant timestamp
) {
}
