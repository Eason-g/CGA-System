package edu.cga.domain.mapper;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Contract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ContractMapper extends BaseMapper<Contract> {

    /** 锁住合同行：签约前使用 */
    @Select("SELECT * FROM contracts WHERE id = #{id} FOR UPDATE")
    Contract lockById(@Param("id") String id);

    /**
     * 某学生占用周工时的合同：生效（ACTIVE）和待签（PENDING_SIGN）。
     * 不受查看人数据范围限制——跨岗累计工时必须按该生在全校的全部合同计算。
     */
    @Select("SELECT * FROM contracts WHERE student_id = #{studentId} AND status IN ('ACTIVE', 'PENDING_SIGN')")
    List<Contract> selectAllocatingByStudent(@Param("studentId") String studentId);

}
