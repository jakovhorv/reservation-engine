package com.example.reservation_engine.controller;

import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.entity.Reservation;
import com.example.reservation_engine.entity.ReservationStatus;
import com.example.reservation_engine.entity.Seat;
import com.example.reservation_engine.repository.EventRepository;
import com.example.reservation_engine.repository.ReservationRepository;
import com.example.reservation_engine.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class ReservationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void cleanDatabase(){
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void postCreatesValidReservation() throws Exception{
        Event event = eventRepository.save(
                new Event("testEvent", LocalDateTime.now().plusDays(10))
        );
        Seat seat = seatRepository.save(
                new Seat(event, "A1")
        );


        MvcResult result = mockMvc.perform(post("/seats/" + seat.getId() + "/reservations"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Long id = jsonNode.get("id").asLong();
        Instant expiresAt =
                Instant.parse(jsonNode.get("expiresAt").asString());


        Reservation savedReservation = reservationRepository.findById(id)
                .orElseThrow();
        assertEquals(1, reservationRepository.count());
        assertEquals(id, savedReservation.getId());
        assertEquals(expiresAt, savedReservation.getExpiresAt());
        assertEquals(ReservationStatus.HELD, savedReservation.getStatus());
    }

    @Test
    void postWithNonexistentSeatIdReturnsNotFound() throws Exception{
        MvcResult result = mockMvc.perform(post("/seats/9999/reservations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Seat not found!"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        assertDoesNotThrow(
                () -> Instant.parse(jsonNode.get("timestamp").asString()),
                "API error response contains an invalid timestamp!");
        assertEquals(0, reservationRepository.count());
    }

    @Test
    void postWithReservedSeatReturnsConflict() throws Exception{
        Event event = eventRepository.save(
                new Event("testEvent", LocalDateTime.now().plusDays(10))
        );
        Seat seat = seatRepository.save(
                new Seat(event, "A1")
        );
        Reservation reservation = reservationRepository.save(
                new Reservation(seat, Instant.now().plusSeconds(360))
        );

        mockMvc.perform(post("/seats/" + seat.getId() +"/reservations"))
                .andExpect(status().isConflict());
        assertEquals(1, reservationRepository.count());
    }

    @Test
    void postConfirmConfirmsReservation() throws Exception{
        Event event = eventRepository.save(
                new Event("testEvent", LocalDateTime.now().plusDays(10))
        );
        Seat seat = seatRepository.save(
                new Seat(event, "A1")
        );
        Reservation reservation = reservationRepository.save(
                new Reservation(seat, Instant.now().plusSeconds(360))
        );

        mockMvc.perform(post("/reservations/" + reservation.getId() + "/confirm"))
                .andExpect(status().isOk());

        Reservation savedReservation = reservationRepository.findById(reservation.getId())
                .orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, savedReservation.getStatus());
    }

    @Test
    void postConfirmWithNonexistentReservationIdReturnsNotFound() throws Exception{
        mockMvc.perform(post("/reservations/9999/confirm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postConfirmWithAlreadyConfirmedReservationReturnsConflict() throws Exception{
        Event event = eventRepository.save(
                new Event("testEvent", LocalDateTime.now().plusDays(10))
        );
        Seat seat = seatRepository.save(
                new Seat(event, "A1")
        );
        Reservation reservation = reservationRepository.save(
                new Reservation(seat, Instant.now().plusSeconds(360))
        );

        mockMvc.perform(post("/reservations/" + reservation.getId() + "/confirm"));
        mockMvc.perform(post("/reservations/" + reservation.getId() +"/confirm"))
                .andExpect(status().isConflict());
    }
}
