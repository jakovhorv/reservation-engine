package com.example.reservation_engine.controller;

import com.example.reservation_engine.dto.ConfirmReservationResponse;
import com.example.reservation_engine.dto.CreateReservationResponse;
import com.example.reservation_engine.entity.Reservation;
import com.example.reservation_engine.repository.ReservationRepository;
import com.example.reservation_engine.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/seats/{seatId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReservationResponse createReservation(@PathVariable Long seatId){
        Reservation reservation = reservationService.holdSeat(seatId);

        return new CreateReservationResponse(reservation.getId(), reservation.getExpiresAt());
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ConfirmReservationResponse confirmReservation(@PathVariable Long reservationId){
        Reservation reservation = reservationService.confirmReservation(reservationId);

        return new ConfirmReservationResponse(reservation.getId(), reservation.getExpiresAt());
    }
}
