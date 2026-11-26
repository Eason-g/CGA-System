package edu.cga;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 三助岗位一体化管理平台后端入口。
 */
// 认证完全由 JWT 过滤器完成，不需要 Spring Boot 默认生成的内存用户
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@MapperScan("edu.cga.domain.mapper")
public class CgaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CgaApplication.class, args);
    }
}
