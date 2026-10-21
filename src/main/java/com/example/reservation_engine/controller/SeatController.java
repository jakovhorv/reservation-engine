package com.example.reservation_engine.controller;

import com.example.reservation_engine.dto.CreateSeatRequest;
import com.example.reservation_engine.dto.CreateSeatResponse;
import com.example.reservation_engine.dto.SeatResponse;
import com.example.reservation_engine.entity.Event;
import com.example.reservation_engine.entity.Seat;
import com.example.reservation_engine.repository.EventRepository;
import com.example.reservation_engine.repository.SeatRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/events/{eventId}/seats")
public class SeatController {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public SeatController(EventRepository eventRepository,
                          SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSeatResponse createSeat(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateSeatRequest request){
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Seat seat = new Seat(event, request.seatNumber());
        try {
            seatRepository.saveAndFlush(seat);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seat already exists for this event"
            );
        }

        return new CreateSeatResponse(seat.getId());
    }

    @GetMapping
    public List<SeatResponse> getSeatByEvent(@PathVariable Long eventId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Seat> seats = seatRepository.findAllByEvent(event);

        List<SeatResponse> responses = seats.stream()
                .map(seat -> new SeatResponse(seat.getId(), seat.getSeatNumber()))
                .toList();

        return responses;
    }
}