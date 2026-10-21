package com.example.reservation_engine.service;

import com.example.reservation_engine.entity.Reservation;
import com.example.reservation_engine.entity.ReservationStatus;
import com.example.reservation_engine.entity.Seat;
import com.example.reservation_engine.repository.SeatRepository;
import com.example.reservation_engine.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ReservationService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    private static final Logger log =
            LoggerFactory.getLogger(ReservationService.class);

    public ReservationService(
            SeatRepository seatRepository,
            ReservationRepository reservationRepository) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Reservation holdSeat(Long seatId){
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Seat not found!"
                ));

        Instant now = Instant.now();

        boolean occupied = reservationRepository.existsActiveReservation(seat, now);

        if (occupied) {
            log.debug("Seat {} is already occupied", seatId);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat is already reserved"
            );
        }

        Instant expiresAt = now.plus(Duration.ofMinutes(5))
                .plusNanos(999_999_999)
                .truncatedTo(ChronoUnit.SECONDS);

        Reservation reservation = new Reservation(seat, expiresAt);
        reservationRepository.save(reservation);
        log.info("Reservation {} for seat {} successfully held", reservation.getId(), seatId);
        return reservation;
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if(reservation.getStatus() == ReservationStatus.HELD
        && reservation.getExpiresAt().isAfter(Instant.now())){
            reservation.confirm();
        }
        else{
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The reservation's status is not: " + ReservationStatus.HELD + ", or its expiration date is <= now!");
        }

        log.info("Reservation {} successfully confirmed", reservation.getId());
        return reservation;
    }
}