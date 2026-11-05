package cn.edu.campus.sharing.reservation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HandoverMapper extends BaseMapper<HandoverRecord> {
    @Select("SELECT * FROM handover_records WHERE reservation_id=#{reservationId} FOR UPDATE")
    HandoverRecord lockForReservation(long reservationId);
}
