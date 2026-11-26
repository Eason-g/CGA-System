package edu.cga.domain.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Timesheet;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TimesheetMapper extends BaseMapper<Timesheet> {

    /** 锁住工时单：审核前使用，防止重复审核 */
    @Select("SELECT * FROM timesheets WHERE id = #{id} FOR UPDATE")
    Timesheet lockById(@Param("id") String id);

    /** 某学生某周的全部工时单（跨岗累计，规则引擎内再排除已驳回的） */
    @Select("SELECT * FROM timesheets WHERE student_id = #{studentId} AND week_no = #{weekNo}")
    List<Timesheet> selectByStudentAndWeek(@Param("studentId") String studentId, @Param("weekNo") int weekNo);

    /** 某合同某月已审核通过的工时单（薪酬核算的依据） */
    @Select("SELECT * FROM timesheets WHERE contract_id = #{contractId} AND month = #{month} AND status = 'APPROVED'")
    List<Timesheet> selectApprovedByContractAndMonth(@Param("contractId") String contractId,
                                                     @Param("month") String month);
}
