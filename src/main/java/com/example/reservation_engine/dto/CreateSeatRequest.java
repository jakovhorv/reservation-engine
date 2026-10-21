package com.example.reservation_engine.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSeatRequest(
        @NotBlank String seatNumber
) {}