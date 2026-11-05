package cn.edu.campus.sharing.reservation;

import java.time.LocalDateTime;

public record HandoverCodeResponse(ReservationView reservation, String code, LocalDateTime expiresAt) {}
