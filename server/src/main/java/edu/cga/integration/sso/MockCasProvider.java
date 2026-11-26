package edu.cga.integration.sso;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import edu.cga.common.BizException;
import edu.cga.config.AppProperties;
import edu.cga.config.IntegrationProperties;
import edu.cga.integration.IntegrationDataLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟统一身份认证中心（演示用，CAS 协议的最小子集）。
 * <ul>
 *   <li>账号目录读 integration/cas-accounts.json（学工号 + 姓名），演示环境所有账号的认证中心密码都是 app.demo-password；</li>
 *   <li>登录成功签发 ST- 开头的一次性票据：60 秒有效（app.sso.ticket-ttl）、只能用一次、绑定签发时的 service；</li>
 *   <li>票据只存在进程内存里，单实例有效；重启后未使用的票据全部失效（符合票据短时有效的语义）。</li>
 * </ul>
 * 认证中心页面见 {@link MockCasController}。
 */
@Component
@ConditionalOnProperty(name = "app.sso.provider", havingValue = "mock", matchIfMissing = true)
public class MockCasProvider implements SsoProvider {

    public static final String TICKET_INVALID = "统一认证票据无效或已被使用，请重新登录";
    public static final String TICKET_EXPIRED = "统一认证票据已过期，请重新登录";
    public static final String SERVICE_MISMATCH = "统一认证票据与回跳地址不一致，请重新登录";

    /** 账号目录中的一条记录 */
    public record CasAccount(String uid, String name, String note) {
    }

    private record Ticket(SsoIdentity identity, String service, Instant issuedAt) {
    }

    private final IntegrationProperties props;
    private final AppProperties appProps;
    private final IntegrationDataLoader loader;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public MockCasProvider(IntegrationProperties props, AppProperties appProps, IntegrationDataLoader loader,
                           Clock clock) {
        this.props = props;
        this.appProps = appProps;
        this.loader = loader;
        this.clock = clock;
    }

    @Override
    public String loginUrl(String service) {
        String base = props.getSso().getMockServerUrl() == null ? "" : props.getSso().getMockServerUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/mock-cas/login?service=" + URLEncoder.encode(service, StandardCharsets.UTF_8);
    }

    @Override
    public SsoIdentity validate(String ticket, String service) {
        // 无论校验结果如何，票据都只能被使用一次（CAS 协议约定），所以先取出再判断
        Ticket t = ticket == null ? null : tickets.remove(ticket.trim());
        if (t == null) {
            throw new BizException(401, TICKET_INVALID);
        }
        if (clock.instant().isAfter(t.issuedAt().plus(props.getSso().getTicketTtl()))) {
            throw new BizException(401, TICKET_EXPIRED);
        }
        if (service == null || !t.service().equals(service.trim())) {
            throw new BizException(401, SERVICE_MISMATCH);
        }
        return t.identity();
    }

    // ------------------------------------------------------------------ 认证中心内部（供 MockCasController 使用）

    /** 账号目录 */
    public List<CasAccount> accounts() {
        return loader.readList(props.getSso().getMockAccounts(), CasAccount.class);
    }

    /** 用学工号 + 密码在认证中心登录；学工号不区分大小写 */
    public Optional<SsoIdentity> authenticate(String uid, String password) {
        if (uid == null || password == null) {
            return Optional.empty();
        }
        String q = uid.trim().toLowerCase(Locale.ROOT);
        Optional<CasAccount> account = accounts().stream()
                .filter(a -> a.uid() != null && a.uid().toLowerCase(Locale.ROOT).equals(q))
                .findFirst();
        // 先比对密码再看账号是否存在，避免通过响应差异判断账号是否存在
        boolean passwordOk = MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                String.valueOf(appProps.getDemoPassword()).getBytes(StandardCharsets.UTF_8));
        if (!passwordOk || account.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SsoIdentity(account.get().uid(), account.get().name()));
    }

    /** 签发一次性票据（签发时间取当前时钟） */
    public String issueTicket(SsoIdentity identity, String service) {
        return issueTicket(identity, service, clock.instant());
    }

    /** 签发一次性票据；issuedAt 可指定，便于测试过期场景 */
    public String issueTicket(SsoIdentity identity, String service, Instant issuedAt) {
        purgeExpired();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = "ST-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(identity, service.trim(), issuedAt));
        return ticket;
    }

    /** service 是否为允许回跳的前端地址（只比较 协议+主机+端口） */
    public boolean isAllowedService(String service) {
        String origin = originOf(service);
        if (origin == null) {
            return false;
        }
        List<String> allowed = props.getSso().getAllowedServiceOrigins();
        if (allowed == null || allowed.isEmpty()) {
            allowed = appProps.getCors().getAllowedOrigins();
        }
        return allowed.stream().map(MockCasProvider::originOf).anyMatch(origin::equals);
    }

    /** 在 service 后追加 ticket 参数；service 带 # 时插在 # 之前，保证前端 hash 路由下能从 location.search 取到 */
    public static String appendTicket(String service, String ticket) {
        String s = service.trim();
        String hash = "";
        int hashAt = s.indexOf('#');
        if (hashAt >= 0) {
            hash = s.substring(hashAt);
            s = s.substring(0, hashAt);
        }
        String sep = s.contains("?") ? "&" : "?";
        return s + sep + "ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8) + hash;
    }

    private static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            int port = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
            return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void purgeExpired() {
        Instant deadline = clock.instant().minus(props.getSso().getTicketTtl());
        tickets.values().removeIf(t -> t.issuedAt().isBefore(deadline));
    }
}
