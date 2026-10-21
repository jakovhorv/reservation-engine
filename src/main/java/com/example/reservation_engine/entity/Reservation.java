package com.example.reservation_engine.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Long getId(){
        return id;
    }

    public ReservationStatus getStatus(){
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }

    protected Reservation() {
    }

    public Reservation(Seat seat, Instant expiresAt) {
        this.seat = seat;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.HELD;
    }
}
