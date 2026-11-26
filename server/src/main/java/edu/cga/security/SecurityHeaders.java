package edu.cga.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 安全响应头（P2 D.2），由 {@link SecurityConfig} 引用：
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}（Spring Security 默认开启，这里显式保留）；</li>
 *   <li>{@code X-Frame-Options: DENY}：本系统没有需要被 iframe 嵌入的页面，模拟认证页也只需要直接打开；</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}：跨站只带来源域名，不带路径和查询串（如评价码）；</li>
 *   <li>基础 CSP：接口返回 JSON / 文件，只允许同源资源、禁止被嵌入、禁止插件。
 *       {@code /mock-cas/**}（统一认证模拟页，服务端渲染的小页面）放宽为允许内联脚本和样式，
 *       但同样禁止被嵌入；不限制 form-action，因为登录后要跳回前端地址。</li>
 * </ul>
 * HSTS 由 Spring Security 默认处理（只在 HTTPS 请求上输出）。
 */
public final class SecurityHeaders {

    static final String API_CSP = "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";
    static final String MOCK_CAS_CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    private SecurityHeaders() {
    }

    public static void configure(HeadersConfigurer<HttpSecurity> headers) {
        RequestMatcher mockCas = PathPatternRequestMatcher.withDefaults().matcher("/mock-cas/**");
        headers
                .contentTypeOptions(c -> { })
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(mockCas,
                        new StaticHeadersWriter("Content-Security-Policy", MOCK_CAS_CSP)))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(mockCas),
                        new StaticHeadersWriter("Content-Security-Policy", API_CSP)));
    }
}
