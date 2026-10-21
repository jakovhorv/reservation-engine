package com.example.reservation_engine.scheduler;

import com.example.reservation_engine.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ReservationExpirationJob {
    private final ReservationRepository reservationRepository;
    private static final Logger log =
            LoggerFactory.getLogger(ReservationExpirationJob.class);

    public ReservationExpirationJob(
            ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireOldHolds(){

        int count = reservationRepository.expireOldHolds(Instant.now());
        if(count > 0) {
            log.info("Expired {} reservations", count);
        }
    }
}
