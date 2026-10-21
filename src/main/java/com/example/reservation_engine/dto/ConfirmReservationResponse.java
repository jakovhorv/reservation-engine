package com.example.reservation_engine.dto;

import java.time.Instant;

public record ConfirmReservationResponse(
        Long id,
        Instant expiresAt
) {}