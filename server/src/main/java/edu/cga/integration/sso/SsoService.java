package edu.cga.integration.sso;

import edu.cga.common.BizException;
import edu.cga.config.IntegrationProperties;
import edu.cga.security.CurrentUser;
import edu.cga.service.AuditService;
import edu.cga.service.AuthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一身份认证换票（P2 B.1）：票据 → 身份 → 本系统用户 → JWT，并写一条审计日志。
 */
@Service
public class SsoService {

    public static final String SSO_DISABLED = "统一身份认证未启用";

    private final IntegrationProperties props;
    private final ObjectProvider<SsoProvider> provider;
    private final AuthService authService;
    private final AuditService audit;

    public SsoService(IntegrationProperties props, ObjectProvider<SsoProvider> provider, AuthService authService,
                      AuditService audit) {
        this.props = props;
        this.provider = provider;
        this.authService = authService;
        this.audit = audit;
    }

    /** 当前认证中心实现；未启用或没有对应实现时 404 */
    public SsoProvider provider() {
        SsoProvider p = props.getSso().isEnabled() ? provider.getIfAvailable() : null;
        if (p == null) {
            throw BizException.notFound(SSO_DISABLED);
        }
        return p;
    }

    public String loginUrl(String service) {
        SsoProvider p = provider();
        if (service == null || service.isBlank()) {
            throw BizException.badRequest("缺少 service 参数");
        }
        return p.loginUrl(service.trim());
    }

    @Transactional
    public AuthService.LoginResult exchange(String ticket, String service) {
        SsoIdentity identity = provider().validate(ticket, service);
        AuthService.LoginResult result = authService.loginBySso(identity.uid());
        audit.log("USER", result.user().getId(), CurrentUser.from(result.user()), "统一身份认证登录",
                "通过" + props.getSso().getDisplayName() + "登录，认证中心账号 " + identity.uid(), "", "");
        return result;
    }
}
