package edu.cga.domain;

import java.lang.reflect.Field;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 列与 Java 对象互转。
 * <p>
 * Spring Boot 4 默认使用 Jackson 3（tools.jackson），这里直接用 Jackson 3 实现，
 * 按实体字段的泛型类型反序列化（如 List&lt;String&gt;、Map&lt;String,Object&gt;）。
 * 实体上需 {@code @TableName(autoResultMap = true)}，字段上 {@code @TableField(typeHandler = JsonColumnTypeHandler.class)}。
 */
@MappedTypes(Object.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonColumnTypeHandler extends AbstractJsonTypeHandler<Object> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public JsonColumnTypeHandler(Class<?> type) {
        super(type);
    }

    public JsonColumnTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public Object parse(String json) {
        JavaType javaType = MAPPER.getTypeFactory().constructType(getFieldType());
        return MAPPER.readValue(json, javaType);
    }

    @Override
    public String toJson(Object obj) {
        return MAPPER.writeValueAsString(obj);
    }
}
