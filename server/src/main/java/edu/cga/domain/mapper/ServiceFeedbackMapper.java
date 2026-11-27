package edu.cga.domain.mapper;

import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.ServiceFeedback;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ServiceFeedbackMapper extends BaseMapper<ServiceFeedback> {

    @Select("SELECT COUNT(*) FROM service_feedback WHERE round_id = #{roundId} AND client_hash = #{clientHash}")
    long countByRoundAndClient(@Param("roundId") String roundId, @Param("clientHash") String clientHash);

    /** 某合同全部评价轮的条数与平均星级（cnt、avgRating） */
    @Select("SELECT COUNT(*) AS cnt, AVG(f.rating) AS avgRating FROM service_feedback f"
            + " JOIN feedback_rounds r ON r.id = f.round_id WHERE r.contract_id = #{contractId}")
    Map<String, Object> statsByContract(@Param("contractId") String contractId);
}
