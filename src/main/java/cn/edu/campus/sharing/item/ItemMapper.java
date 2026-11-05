package cn.edu.campus.sharing.item;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
    String FILTER = """
            FROM items i WHERE i.deleted = FALSE
            AND (#{q.ownerId} IS NULL OR i.owner_id = #{q.ownerId})
            AND (#{q.categoryId} IS NULL OR i.category_id = #{q.categoryId})
            AND (#{q.reviewStatus} IS NULL OR i.review_status = #{q.reviewStatus})
            AND (#{q.status} IS NULL OR i.status = #{q.status})
            AND (#{q.publicOnly} = FALSE OR EXISTS (
                SELECT 1 FROM users u WHERE u.id = i.owner_id AND u.status = 'ACTIVE'))
            AND (i.title LIKE #{q.pattern} ESCAPE '!' OR i.description LIKE #{q.pattern} ESCAPE '!')
            """;

    @Select("SELECT i.* " + FILTER + " ORDER BY i.created_at DESC, i.id DESC LIMIT #{q.size} OFFSET #{q.offset}")
    List<Item> search(@Param("q") ItemQuery query);

    @Select("SELECT COUNT(*) " + FILTER)
    long countSearch(@Param("q") ItemQuery query);

    @Select("SELECT * FROM items WHERE id = #{id} FOR UPDATE")
    Item lockById(long id);

    @Update("UPDATE items SET status=#{status}, updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status=#{expected} "
            + "AND deleted=FALSE AND review_status='APPROVED'")
    int transition(@Param("id") long id, @Param("expected") String expected, @Param("status") String status);

    @Insert("INSERT INTO admin_audit_logs(admin_id, action, target_type, target_id) VALUES (#{adminId}, #{action}, 'ITEM', #{itemId})")
    int audit(@Param("adminId") long adminId, @Param("action") String action, @Param("itemId") long itemId);
}
