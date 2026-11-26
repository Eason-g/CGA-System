package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.FeedbackRound;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeedbackRoundMapper extends BaseMapper<FeedbackRound> {

    @Select("SELECT * FROM feedback_rounds WHERE code = #{code}")
    FeedbackRound selectByCode(@Param("code") String code);

    /** 锁住评价轮：提交评价、关闭评价轮前使用，保证“截止/关闭/重复提交”判断与写入串行 */
    @Select("SELECT * FROM feedback_rounds WHERE code = #{code} FOR UPDATE")
    FeedbackRound lockByCode(@Param("code") String code);

    @Select("SELECT * FROM feedback_rounds WHERE id = #{id} FOR UPDATE")
    FeedbackRound lockById(@Param("id") String id);

    /** 某合同未关闭且未过截止日的评价轮数量（同一合同同时只允许一轮进行中） */
    @Select("SELECT COUNT(*) FROM feedback_rounds WHERE contract_id = #{contractId} AND status = 'OPEN'"
            + " AND deadline >= #{today}")
    long countActiveByContract(@Param("contractId") String contractId, @Param("today") java.time.LocalDate today);
}
