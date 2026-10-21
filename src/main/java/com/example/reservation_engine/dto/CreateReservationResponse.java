package com.example.reservation_engine.dto;

import java.time.Instant;

public record CreateReservationResponse(
        Long id,
        Instant expiresAt
) {}