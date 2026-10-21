package com.example.reservation_engine.controller;

import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.repository.EventRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase(){
        eventRepository.deleteAll();
    }

    @Test
    void postCreatesValidEvent() throws Exception{
        LocalDateTime startsAt = LocalDateTime.now().plusDays(10).withNano(0);
        String json = """
                      {
                          "name": "Concert",
                          "startsAt": "%s"
                      }
                      """.formatted(startsAt);

        MvcResult result = mockMvc.perform(post("/events")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                                    .andExpect(status().isCreated())
                                    .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Long returnedId = jsonNode.get("id").asLong();

        Event savedEvent = eventRepository.findAll().getFirst();

        assertEquals(1, eventRepository.count());
        assertEquals(returnedId, savedEvent.getId());
        assertEquals("Concert", savedEvent.getName());
        assertEquals(startsAt, savedEvent.getStartsAt());
    }

    @Test
    void postWithBlankNameIsBadRequest() throws Exception{
        LocalDateTime startsAt = LocalDateTime.now().plusDays(10);
        String json = """
                      {
                          "name": "",
                          "startsAt": "%s"
                      }
                      """.formatted(startsAt);

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("must not be blank"))
                .andExpect(jsonPath("$.timestamp").isString());

        assertEquals(0, eventRepository.count());
    }

    @Test
    void postWithPastDateIsBadRequest() throws Exception{
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Concert",
                            "startsAt": "1906-12-01T00:00:00"
                        }
                        """))
                .andExpect(status().isBadRequest());

        assertEquals(0, eventRepository.count());
    }

    @Test
    void getGetsValidEvent() throws Exception{
        Event event = eventRepository.save(
                new Event("testEvent", LocalDateTime.now().plusDays(10).withNano(0))
        );

        mockMvc.perform(get("/events/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.name").value(event.getName()))
                .andExpect(jsonPath("$.startsAt").value(event.getStartsAt().toString()));
    }

    @Test
    void getEventWithNonexistentIdReturnsNotFound() throws Exception{
        mockMvc.perform(get("/events/7777")).andExpect(status().isNotFound());
    }
}
