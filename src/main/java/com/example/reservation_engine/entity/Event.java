package com.example.reservation_engine.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getStartsAt(){
        return startsAt;
    }

    protected Event() {
    }

    public Event(String name, LocalDateTime startsAt) {
        this.name = name;
        this.startsAt = startsAt;
    }
}