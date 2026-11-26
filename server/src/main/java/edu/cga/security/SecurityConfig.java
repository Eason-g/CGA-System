package edu.cga.security;

import java.io.IOException;

import edu.cga.common.ApiResponse;
import edu.cga.config.AppProperties;
import edu.cga.domain.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 安全配置：无状态 JWT 认证。
 * <ul>
 *   <li>POST /api/auth/login、/api/public/**（服务对象评价）公开，其余接口都需要登录；</li>
 *   <li>未登录返回 401、无权限返回 403，均为 { code, data, message } JSON；</li>
 *   <li>开启方法级 {@code @PreAuthorize}，角色以 ROLE_ 前缀授予，如 hasRole('SCHOOL_ADMIN')。</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserMapper userMapper,
                                                   JsonMapper jsonMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                // P2 D.2：安全响应头（X-Content-Type-Options、X-Frame-Options、Referrer-Policy、CSP）
                .headers(SecurityHeaders::configure)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // P1：服务对象评价的公开接口（评价码查询与提交，有进程内限流）
                        .requestMatchers("/api/public/**").permitAll()
                        // 财务系统回调：不带登录令牌，由 FinanceService 校验 HMAC-SHA256 签名（P1 第三批）
                        .requestMatchers(HttpMethod.POST, "/api/integration/finance/callback").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeJson(res, jsonMapper, 401, "未登录或登录已失效"))
                        .accessDeniedHandler((req, res, e) ->
                                writeJson(res, jsonMapper, 403, "无权限执行该操作")))
                .addFilterBefore(new JwtAuthFilter(jwtService, userMapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCors().getAllowedOrigins());
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // 报表导出：跨域时前端要能读到文件名（P1 第二批 2.1）
        config.addExposedHeader(HttpHeaders.CONTENT_DISPOSITION);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeJson(HttpServletResponse res, JsonMapper mapper, int code, String message)
            throws IOException {
        res.setStatus(code);
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiResponse.fail(code, message, null));
    }
}
