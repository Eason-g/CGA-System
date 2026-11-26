package edu.cga.domain.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.JobApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface JobApplicationMapper extends BaseMapper<JobApplication> {

    /** 锁住申请行：特批审核、安排面试、录用/不录用前使用，防止同一申请被重复处理 */
    @Select("SELECT * FROM applications WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_JobApplication")
    JobApplication lockById(@Param("id") String id);

    /** 某学生的全部申请（用于“是否已申请过该岗位”的判断） */
    @Select("SELECT * FROM applications WHERE student_id = #{studentId}")
    @ResultMap("mybatis-plus_JobApplication")
    List<JobApplication> selectByStudent(@Param("studentId") String studentId);
}
