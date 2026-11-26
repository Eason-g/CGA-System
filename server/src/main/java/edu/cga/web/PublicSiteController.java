package edu.cga.web;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.config.IntegrationProperties;
import edu.cga.integration.sso.SsoProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/public/site（公开，P2 B.4）：站点信息，供登录页、侧栏、归档页统一读取校名，
 * 并告诉登录页是否显示“统一身份认证登录”。
 */
@RestController
public class PublicSiteController {

    private final IntegrationProperties props;
    private final ObjectProvider<SsoProvider> ssoProvider;

    public PublicSiteController(IntegrationProperties props, ObjectProvider<SsoProvider> ssoProvider) {
        this.props = props;
        this.ssoProvider = ssoProvider;
    }

    @GetMapping("/api/public/site")
    public ApiResponse<Map<String, Object>> site() {
        IntegrationProperties.Sso sso = props.getSso();
        Map<String, Object> ssoInfo = new LinkedHashMap<>();
        ssoInfo.put("enabled", sso.isEnabled() && ssoProvider.getIfAvailable() != null);
        ssoInfo.put("provider", sso.getProvider());
        ssoInfo.put("displayName", sso.getDisplayName());
        ssoInfo.put("mock", "mock".equals(sso.getProvider()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schoolName", props.getSchoolName());
        data.put("sso", ssoInfo);
        return ApiResponse.ok(data);
    }
}
