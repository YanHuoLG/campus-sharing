package cn.edu.campus.sharing.reservation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("SELECT * FROM reservations WHERE id=#{id} FOR UPDATE")
    Reservation lockById(long id);

    @Select("SELECT * FROM reservations WHERE item_id=#{itemId} AND status='ACCEPTED' FOR UPDATE")
    Reservation lockAccepted(long itemId);

    // Locking reads see the latest committed state after waiting for the item lock.
    @Select("SELECT id FROM reservations WHERE item_id=#{itemId} AND requester_id=#{requesterId} AND status IN ('PENDING','ACCEPTED') FOR UPDATE")
    List<Long> activeRequests(@Param("itemId") long itemId, @Param("requesterId") long requesterId);

    @Update("UPDATE reservations SET status=#{status}, updated_at=#{now} WHERE id=#{id} AND status=#{expected}")
    int transition(@Param("id") long id, @Param("expected") String expected, @Param("status") String status, @Param("now") LocalDateTime now);

    @Update("UPDATE reservations SET status=#{status}, updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND status='PENDING'")
    int closePending(@Param("itemId") long itemId, @Param("status") String status);

    @Update("UPDATE reservations SET status='CANCELLED', updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND status IN ('PENDING','ACCEPTED')")
    int cancelActive(long itemId);

    String COLUMNS = "r.id, r.item_id, i.title AS item_title, i.owner_id, r.requester_id, r.message, r.status, "
            + "i.status AS item_status, i.deleted AS item_deleted, h.expires_at, h.attempts, r.created_at, r.updated_at";
    String JOIN = " FROM reservations r JOIN items i ON i.id=r.item_id LEFT JOIN handover_records h ON h.reservation_id=r.id ";
    String FILTER = " WHERE ((#{received}=TRUE AND i.owner_id=#{actorId}) OR (#{received}=FALSE AND r.requester_id=#{actorId})) "
            + "AND (#{status} IS NULL OR r.status=#{status}) ";

    @Select("SELECT " + COLUMNS + JOIN + " WHERE r.id=#{id}")
    ReservationView view(long id);

    @Select("SELECT " + COLUMNS + JOIN + FILTER + "ORDER BY r.created_at DESC, r.id DESC LIMIT #{size} OFFSET #{offset}")
    List<ReservationView> search(@Param("actorId") long actorId, @Param("received") boolean received,
            @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

    @Select("SELECT COUNT(*)" + JOIN + FILTER)
    long countSearch(@Param("actorId") long actorId, @Param("received") boolean received, @Param("status") String status);

    @Select("SELECT r.id FROM reservations r JOIN handover_records h ON h.reservation_id=r.id "
            + "WHERE r.status='ACCEPTED' AND h.expires_at<=#{now} ORDER BY h.expires_at, r.id LIMIT 100")
    List<Long> due(LocalDateTime now);
}
