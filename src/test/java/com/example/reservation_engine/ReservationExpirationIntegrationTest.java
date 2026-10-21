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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class ReservationExpirationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    SeatRepository seatRepository;
    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationExpirationJob expirationJob;

    @BeforeEach
    void databaseCleanup(){
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void expirationJobChangesHeldToExpired(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,0,0));
        Seat seat = new Seat(event, "A1");
        Reservation reservation = new Reservation(seat, Instant.now().minusSeconds(60));
        eventRepository.save(event);
        seatRepository.save(seat);
        reservationRepository.save(reservation);

        expirationJob.expireOldHolds();
        Reservation updatedReservation = reservationRepository.findById(reservation.getId())
                .orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, updatedReservation.getStatus());
    }

    @Test
    void expirationJobDoesNotExpireActiveHold(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,0,0));
        Seat seat = new Seat(event, "A1");
        Reservation reservation = new Reservation(seat, Instant.now().plusSeconds(1000));
        eventRepository.save(event);
        seatRepository.save(seat);
        reservationRepository.save(reservation);

        expirationJob.expireOldHolds();
        Reservation updatedReservation = reservationRepository.findById(reservation.getId())
                .orElseThrow();
        assertEquals(ReservationStatus.HELD, updatedReservation.getStatus());
    }

    @Test
    void expirationJobDoesNotExpireConfirmedReservation(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,0,0));
        Seat seat1 = new Seat(event, "A1");
        Seat seat2 = new Seat(event, "A2");
        eventRepository.save(event);
        seatRepository.save(seat1);
        seatRepository.save(seat2);
        Reservation reservation1 = reservationService.holdSeat(seat1.getId());
        Reservation reservation2 = new Reservation(seat2, Instant.now().minusSeconds(60));

        reservationService.confirmReservation(reservation1.getId());
        reservation2.confirm();
        reservationRepository.save(reservation2);

        expirationJob.expireOldHolds();
        Reservation updatedReservation1 = reservationRepository.findById(reservation1.getId())
                .orElseThrow();
        Reservation updatedReservation2 = reservationRepository.findById(reservation2.getId())
                .orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, updatedReservation1.getStatus());
        assertEquals(ReservationStatus.CONFIRMED, updatedReservation2.getStatus());
    }

    @Test
    void expiredSeatCanBeHeldAgain(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,0,0));
        Seat seat = new Seat(event, "A1");
        Reservation reservation = new Reservation(seat, Instant.now().minusSeconds(60));
        eventRepository.save(event);
        seatRepository.save(seat);
        reservationRepository.save(reservation);

        expirationJob.expireOldHolds();

        Reservation updatedReservation = reservationService.holdSeat(seat.getId());
        assertEquals(ReservationStatus.HELD, updatedReservation.getStatus());
        assertNotEquals(reservation.getId(), updatedReservation.getId());
    }

    @Test
    void expiredReservationCannotBeConfirmed(){
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,0,0));
        Seat seat = new Seat(event, "A1");
        Reservation reservation = new Reservation(seat, Instant.now().minusSeconds(60));
        eventRepository.save(event);
        seatRepository.save(seat);
        reservationRepository.save(reservation);

        expirationJob.expireOldHolds();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reservationService.confirmReservation(reservation.getId()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
