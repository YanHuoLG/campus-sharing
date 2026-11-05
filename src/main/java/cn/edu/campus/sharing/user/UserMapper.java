package cn.edu.campus.sharing.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM users WHERE phone = #{phone}")
    UserAccount findByPhone(String phone);

    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    UserAccount lockById(long id);

    @Update("UPDATE users SET nickname = #{nickname}, contact_info = #{contactInfo} WHERE id = #{id} AND status = 'ACTIVE'")
    int updateProfile(@Param("id") long id, @Param("nickname") String nickname,
                      @Param("contactInfo") String contactInfo);

    @Select("SELECT * FROM users WHERE phone LIKE #{pattern} ESCAPE '!' OR nickname LIKE #{pattern} ESCAPE '!' ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<UserAccount> search(@Param("pattern") String pattern, @Param("size") int size,
                             @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM users WHERE phone LIKE #{pattern} ESCAPE '!' OR nickname LIKE #{pattern} ESCAPE '!'")
    long countSearch(String pattern);

    @Update("UPDATE users SET status = #{status} WHERE id = #{id} AND role = 'STUDENT'")
    int setStatus(@Param("id") long id, @Param("status") String status);

    @Insert("INSERT INTO admin_audit_logs(admin_id, action, target_type, target_id) VALUES (#{adminId}, #{action}, 'USER', #{userId})")
    int audit(@Param("adminId") long adminId, @Param("action") String action,
              @Param("userId") long userId);
}
