package com.example.reservation_engine.controller;

import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.entity.Seat;
import com.example.reservation_engine.repository.EventRepository;
import com.example.reservation_engine.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class SeatControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SeatRepository seatRepository;

    @BeforeEach
    void cleanDatabase(){
        seatRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void postCreatesValidSeat() throws Exception{
        Event event = new Event("testEvent", LocalDateTime.now().plusDays(10));
        eventRepository.save(event);

        String postPath = "/events/%s/seats".formatted(event.getId());
        String json = """
                      {
                        "seatNumber": "A1"
                      }
                      """;

        MvcResult result = mockMvc.perform(post(postPath)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                                    .andExpect(status().isCreated())
                                    .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Long returnedId = jsonNode.get("id").asLong();


        Seat savedSeat = seatRepository.findAll().getFirst();
        Long savedEventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM seats WHERE id = ?",
                Long.class,
                savedSeat.getId()
        );

        assertEquals(1, seatRepository.count());
        assertEquals(returnedId, savedSeat.getId());
        assertEquals(event.getId(), savedEventId);
        assertEquals("A1", savedSeat.getSeatNumber());
    }

    @Test
    void postSeatWithNonexistentEventReturnsNotFound() throws Exception {
        String postPath = "/events/7777/seats";
        String json = """
                      {
                        "seatNumber": "A1"
                      }
                      """;

        mockMvc.perform(post(postPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());

        assertEquals(0, seatRepository.count());
    }

    @Test
    void getGetsValidSeat() throws Exception{
        Event event1 =eventRepository.save(
                new Event("Event 1", LocalDateTime.now().plusDays(10))
        );
        Event event2 = eventRepository.save(
                new Event("Event 2", LocalDateTime.now().plusDays(10))
        );
        Seat seat1 = seatRepository.save(new Seat(event1, "A1"));
        Seat seat2 = seatRepository.save(new Seat(event1, "A2"));
        seatRepository.save(new Seat(event2, "A1"));

        mockMvc.perform(get("/events/" + event1.getId() + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].seatNumber",
                        containsInAnyOrder("A1", "A2")))
                .andExpect(jsonPath(
                        "$[?(@.seatNumber == 'A1')].id",
                        contains(seat1.getId().intValue()
                )))
                .andExpect(jsonPath(
                        "$[?(@.seatNumber == 'A2')].id",
                        contains(seat2.getId().intValue())
                ));
    }
}
