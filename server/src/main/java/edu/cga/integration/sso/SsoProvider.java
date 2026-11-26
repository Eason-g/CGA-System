package edu.cga.integration.sso;

/**
 * 统一身份认证（CAS 风格）对接接口（P2 B.1）。
 * <p>
 * 流程：前端跳到 {@link #loginUrl(String)} → 用户在认证中心登录 → 认证中心带一次性票据回跳 service →
 * 前端把票据交给 POST /api/auth/sso/exchange → 本系统调用 {@link #validate(String, String)} 换出身份，
 * 再按学工号映射到本系统用户并签发 JWT。
 * <p>
 * 默认实现为 {@link MockCasProvider}（模拟认证中心，演示用）；对接真实 CAS 时新增一个实现类
 * （validate 里调用认证中心的 /serviceValidate），并把 app.sso.provider 改成对应的值即可。
 */
public interface SsoProvider {

    /** 认证中心登录页地址（已带上编码后的 service 参数） */
    String loginUrl(String service);

    /**
     * 校验票据并返回身份。票据无效、过期、已使用或与 service 不一致时抛出 401 的 BizException。
     */
    SsoIdentity validate(String ticket, String service);
}
