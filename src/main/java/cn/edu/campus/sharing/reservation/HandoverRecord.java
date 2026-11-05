package cn.edu.campus.sharing.reservation;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("handover_records")
public class HandoverRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private Integer attempts;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
}
