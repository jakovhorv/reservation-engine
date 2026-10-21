package com.example.reservation_engine.controller;

import com.example.reservation_engine.dto.CreateEventRequest;
import com.example.reservation_engine.dto.CreateEventResponse;
import com.example.reservation_engine.dto.EventResponse;
import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.repository.EventRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/events")
public class EventController {

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = new Event(request.name(), request.startsAt());
        eventRepository.save(event);
        return new CreateEventResponse(event.getId());
    }

    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getStartsAt());
    }
}