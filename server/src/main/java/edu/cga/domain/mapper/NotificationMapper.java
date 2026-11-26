package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface NotificationMapper extends BaseMapper<Notification> {

    /** 把本人的站内信全部标为已读 */
    @Update("UPDATE notifications SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(@Param("userId") String userId);
}
