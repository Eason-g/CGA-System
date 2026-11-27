package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Department;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DepartmentMapper extends BaseMapper<Department> {

    /** 锁住院系行：同一院系的建岗名额检查串行执行 */
    @Select("SELECT * FROM departments WHERE id = #{id} FOR UPDATE")
    Department lockById(@Param("id") String id);
}
