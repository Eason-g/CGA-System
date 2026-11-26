package edu.cga.domain.mapper;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Evaluation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /** 某合同最近一次考核的薪酬系数；没有考核返回 null（核算时按 1.0） */
    @Select("SELECT coefficient FROM evaluations WHERE contract_id = #{contractId}"
            + " ORDER BY evaluated_at DESC, id DESC LIMIT 1")
    BigDecimal selectLatestCoefficient(@Param("contractId") String contractId);
}
