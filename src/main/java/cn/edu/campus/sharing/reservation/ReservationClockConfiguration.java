package cn.edu.campus.sharing.reservation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReservationClockConfiguration {
    @Bean
    Clock reservationClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
}
