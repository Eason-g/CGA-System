package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Budget;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BudgetMapper extends BaseMapper<Budget> {

    /**
     * 锁住经费行：建岗冻结、驳回释放、薪酬核算、发放、考核改系数之前使用。
     * 同一经费池的金额检查与更新因此串行执行，不会出现两笔同时通过“可用额”检查而透支。
     */
    @Select("SELECT * FROM budgets WHERE id = #{id} FOR UPDATE")
    Budget lockById(@Param("id") String id);
}
