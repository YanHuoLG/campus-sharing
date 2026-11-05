package cn.edu.campus.sharing.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="app.reservations.expiry-enabled", havingValue="true", matchIfMissing=true)
public class ReservationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private final ReservationService service;
    public ReservationExpiryJob(ReservationService service) { this.service = service; }

    @Scheduled(fixedDelayString="${app.reservations.expiry-scan-ms:60000}", initialDelayString="${app.reservations.expiry-scan-ms:60000}")
    public void expireDue() {
        for (long id : service.due()) {
            try { service.expire(id); }
            catch (RuntimeException ex) { log.warn("Reservation {} expiry will be retried: {}", id, ex.getClass().getSimpleName()); }
        }
    }
}
