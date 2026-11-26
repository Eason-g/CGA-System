package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 锁住学生行（行级排他锁，事务结束释放）。
     * 录用、申请、工时填报都先锁学生行，同一学生的工时累计检查因此串行执行：
     * 两位导师同时录用同一学生时，后到的一方要等前者提交，再按最新合同重新累计。
     */
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_User")
    User lockById(@Param("id") String id);
}
