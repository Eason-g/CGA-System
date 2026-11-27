package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Attachment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AttachmentMapper extends BaseMapper<Attachment> {

    /** 锁住附件行：核验前使用，避免两人同时核验同一份材料 */
    @Select("SELECT * FROM attachments WHERE id = #{id} FOR UPDATE")
    Attachment lockById(@Param("id") String id);
}
