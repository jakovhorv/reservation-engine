package com.example.reservation_engine.dto;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        LocalDateTime startsAt
) {}