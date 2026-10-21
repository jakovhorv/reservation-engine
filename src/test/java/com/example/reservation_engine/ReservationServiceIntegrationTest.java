package com.example.reservation_engine;

import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.entity.Reservation;
import com.example.reservation_engine.entity.ReservationStatus;
import com.example.reservation_engine.entity.Seat;
import com.example.reservation_engine.repository.EventRepository;
import com.example.reservation_engine.repository.ReservationRepository;
import com.example.reservation_engine.repository.SeatRepository;
import com.example.reservation_engine.scheduler.ReservationExpirationJob;
import com.example.reservation_engine.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class ReservationServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReservationExpirationJob reservationExpirationJob;

    @BeforeEach
    void cleanDatabase(){
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void holdSeatCreatesHeldReservation(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,18,0));
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());

        Reservation saved = reservationRepository.findById(reservation.getId())
                        .orElseThrow();

        assertEquals(
                ReservationStatus.HELD, saved.getStatus(),
                "Reservation status is NOT " + ReservationStatus.HELD);
    }

    @Test
    void holdSeatSetsExpirationFiveMinutesAhead(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Instant before = Instant.now();
        Reservation reservation = reservationService.holdSeat(seat.getId());
        Instant after = Instant.now();

        Reservation saved = reservationRepository.findById(reservation.getId())
                .orElseThrow();
        Instant expiresAt = saved.getExpiresAt();

        assertFalse(expiresAt.isBefore(
                before.plus(Duration.ofMinutes(5))),
                "Reservation expires too early!");
        assertFalse(expiresAt.isAfter(
                after.plus(Duration.ofMinutes(5)).plusSeconds(1)),
                "Reservation expires too late!");
    }

    @Test
    void holdSeatRejectsAlreadyHeldSeat(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        reservationService.holdSeat(seat.getId());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.holdSeat(seat.getId()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void holdSeatRejectsConfirmedSeat(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());
        reservationService.confirmReservation(reservation.getId());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.holdSeat(seat.getId()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void holdSeatAllowsExpiredSeat(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());
        jdbcTemplate.update(
                "UPDATE reservations SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)),
                reservation.getId()
        );

        Reservation newReservation = reservationService.holdSeat(seat.getId());

        assertEquals(ReservationStatus.HELD, newReservation.getStatus());
        assertNotEquals(reservation.getId(), newReservation.getId());
    }

    @Test
    void holdSeatRejectsNonexistentSeat(){
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.holdSeat((long) 1000)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void confirmReservationSucceeds(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());
        reservationService.confirmReservation(reservation.getId());

        Reservation saved = reservationRepository.findById(reservation.getId())
                .orElseThrow();

        assertEquals(ReservationStatus.CONFIRMED, saved.getStatus());
    }

    @Test
    void confirmReservationRejectsExpiredHold(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());
        jdbcTemplate.update(
                "UPDATE reservations SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)),
                reservation.getId()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.confirmReservation(reservation.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void confirmReservationRejectsAlreadyConfirmedReservation(){
        LocalDateTime time = LocalDateTime.of(2026,12,1,18,0);

        Event event = new Event("testEvent", time);
        eventRepository.save(event);
        Seat seat = new Seat(event, "A1");
        seatRepository.save(seat);

        Reservation reservation = reservationService.holdSeat(seat.getId());
        reservationService.confirmReservation(reservation.getId());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.confirmReservation(reservation.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void confirmReservationRejectsNonexistentReservation(){
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.confirmReservation( (long) 1000)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

}
