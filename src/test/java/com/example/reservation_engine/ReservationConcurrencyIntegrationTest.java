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
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class ReservationConcurrencyIntegrationTest {

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
    void concurrentHoldsAllowOnlyOneReservation() throws InterruptedException{
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,12,0));
        Seat seat = new Seat(event, "A1");
        eventRepository.save(event);
        seatRepository.save(seat);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try{
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch readyLatch = new CountDownLatch(2);

            Callable<Reservation> task = () -> {
                readyLatch.countDown();
                startLatch.await();
                return reservationService.holdSeat(seat.getId());
            };

            Future<Reservation> future1 = executor.submit(task);
            Future<Reservation> future2 = executor.submit(task);

            readyLatch.await();
            startLatch.countDown();

            int success = 0;
            int conflict = 0;
            for(Future<Reservation> future : List.of(future1, future2)){
                try {
                    future.get();
                    success++;
                }
                catch(ExecutionException e){
                    if(e.getCause() instanceof ResponseStatusException exception
                            && exception.getStatusCode() == HttpStatus.CONFLICT){
                        conflict++;
                    }
                    else{
                        throw new AssertionError("Unexpected error!", e);
                    }
                }
            }

            assertEquals(1, success);
            assertEquals(1, conflict);
            assertEquals(1, reservationRepository.count());
        }
        finally{
            executor.shutdown();
        }

    }

    @Test
    void concurrentConfirmationsAllowOnlyOneSuccess() throws InterruptedException{
        Event event = new Event("testEvent", LocalDateTime.of(2026,12,1,12,0));
        Seat seat = new Seat(event, "A1");
        eventRepository.save(event);
        seatRepository.save(seat);
        Reservation reservation = reservationService.holdSeat(seat.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try{
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch readyLatch = new CountDownLatch(2);

            Callable<Reservation> task = () -> {
                readyLatch.countDown();
                startLatch.await();
                return reservationService.confirmReservation(reservation.getId());
            };

            Future<Reservation> future1 = executor.submit(task);
            Future<Reservation> future2 = executor.submit(task);

            readyLatch.await();
            startLatch.countDown();

            int success = 0;
            int conflict = 0;
            for(Future<Reservation> future : List.of(future1, future2)) {
                try {
                    future.get();
                    success++;

                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ResponseStatusException exception
                            && exception.getStatusCode() == HttpStatus.CONFLICT) {
                        conflict++;
                    } else {
                        throw new AssertionError("Unexpected error!", e);
                    }
                }
            }

            Reservation saved = reservationRepository.findById(reservation.getId())
                    .orElseThrow();

            assertEquals(1, success);
            assertEquals(1, conflict);
            assertEquals(ReservationStatus.CONFIRMED, saved.getStatus());
        }
        finally{executor.shutdown();}
    }
}
