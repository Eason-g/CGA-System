package edu.cga.domain.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Job;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface JobMapper extends BaseMapper<Job> {

    /** 锁住岗位行：录用（名额 hired_count）、审批流转、里程碑更新前使用 */
    @Select("SELECT * FROM jobs WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_Job")
    Job lockById(@Param("id") String id);

    /** 某院系的全部岗位（院系名额占用 = 其中未驳回岗位的名额之和，由规则引擎计算） */
    @Select("SELECT * FROM jobs WHERE dept_id = #{deptId}")
    @ResultMap("mybatis-plus_Job")
    List<Job> selectByDept(@Param("deptId") String deptId);
}
