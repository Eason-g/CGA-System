package edu.cga.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.SysConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface SysConfigMapper extends BaseMapper<SysConfig> {

    /** 锁住一项配置：保存配置时要与原值合并，加锁防止两次保存互相覆盖 */
    @Select("SELECT * FROM sys_config WHERE config_key = #{key} FOR UPDATE")
    @ResultMap("mybatis-plus_SysConfig")
    SysConfig lockByKey(@Param("key") String key);
}
