package com.example.reservation_engine.repository;

import com.example.reservation_engine.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}