package cn.edu.campus.sharing.reservation;

import java.time.LocalDateTime;

public record ReservationView(Long id, Long itemId, String itemTitle, Long ownerId, Long requesterId,
        String message, String status, String itemStatus, Boolean itemDeleted, LocalDateTime expiresAt,
        Integer attempts, LocalDateTime createdAt, LocalDateTime updatedAt) {}
