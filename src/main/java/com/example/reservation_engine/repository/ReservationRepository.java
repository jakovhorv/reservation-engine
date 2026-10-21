package com.example.reservation_engine.repository;

import com.example.reservation_engine.entity.Reservation;
import com.example.reservation_engine.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
    SELECT COUNT(r) > 0
    FROM Reservation r
    WHERE r.seat = :seat
      AND (
          r.status = 'CONFIRMED'
          OR (r.status = 'HELD' AND r.expiresAt > :now)
      )
    """)
    boolean existsActiveReservation(
            @Param("seat") Seat seat,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
    UPDATE Reservation r
    SET r.status = EXPIRED
    WHERE r.status = HELD
        AND r.expiresAt <= :now
    """)
    int expireOldHolds(@Param("now") Instant now);
}