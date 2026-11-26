package edu.cga.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 读取模拟外部系统的演示数据（resources/integration/*.json）。
 * 每次调用都重新读文件：演示时改 JSON 不用重启，数据量很小，开销可以忽略。
 */
@Component
public class IntegrationDataLoader {

    private final ResourceLoader resourceLoader;
    private final JsonMapper json;

    public IntegrationDataLoader(ResourceLoader resourceLoader, JsonMapper json) {
        this.resourceLoader = resourceLoader;
        this.json = json;
    }

    /** 读取一个 JSON 数组文件，转成指定类型的列表；文件不存在或格式错误时抛 IllegalStateException */
    public <T> List<T> readList(String location, Class<T> type) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("模拟外部系统数据文件不存在：" + location);
        }
        JavaType listType = json.getTypeFactory().constructCollectionType(List.class, type);
        try (InputStream in = resource.getInputStream()) {
            return json.readValue(in, listType);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("模拟外部系统数据文件格式错误：" + location + "（" + e.getMessage() + "）", e);
        }
    }
}
