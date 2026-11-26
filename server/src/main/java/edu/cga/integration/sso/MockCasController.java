package edu.cga.integration.sso;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import edu.cga.config.IntegrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * 模拟统一身份认证中心的页面（演示用，由后端直接输出 HTML，路径 /mock-cas/** 免登录）。
 * <ul>
 *   <li>GET /mock-cas/login?service=…：显示登录表单；</li>
 *   <li>POST /mock-cas/login（表单 uid、password、service）：校验通过后 302 回跳 service?ticket=ST-…，失败则重新显示表单。</li>
 * </ul>
 * service 必须是允许的前端地址，否则拒绝（防止被当作任意跳转的跳板）。
 */
@RestController
@RequestMapping("/mock-cas")
@ConditionalOnProperty(name = "app.sso.provider", havingValue = "mock", matchIfMissing = true)
public class MockCasController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final MockCasProvider cas;
    private final IntegrationProperties props;

    public MockCasController(MockCasProvider cas, IntegrationProperties props) {
        this.cas = cas;
        this.props = props;
    }

    @GetMapping("/login")
    public ResponseEntity<String> loginPage(@RequestParam(required = false) String service) {
        ResponseEntity<String> rejected = rejectIfUnavailable(service);
        if (rejected != null) {
            return rejected;
        }
        return html(HttpStatus.OK, page(service, "", null));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> login(@RequestParam(required = false) String uid,
                                        @RequestParam(required = false) String password,
                                        @RequestParam(required = false) String service) {
        ResponseEntity<String> rejected = rejectIfUnavailable(service);
        if (rejected != null) {
            return rejected;
        }
        Optional<SsoIdentity> identity = cas.authenticate(uid, password);
        if (identity.isEmpty()) {
            return html(HttpStatus.UNAUTHORIZED, page(service, uid, "学工号或密码错误"));
        }
        String ticket = cas.issueTicket(identity.get(), service);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(MockCasProvider.appendTicket(service, ticket)).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private ResponseEntity<String> rejectIfUnavailable(String service) {
        if (!props.getSso().isEnabled()) {
            return html(HttpStatus.NOT_FOUND, message("统一身份认证未启用"));
        }
        if (!cas.isAllowedService(service)) {
            return html(HttpStatus.BAD_REQUEST, message("service 参数缺失或不在允许的回跳地址范围内"));
        }
        return null;
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(HTML).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private static String message(String text) {
        return shell("<p class=\"err\">" + HtmlUtils.htmlEscape(text) + "</p>");
    }

    private static String page(String service, String uid, String error) {
        String err = error == null ? "" : "<p class=\"err\">" + HtmlUtils.htmlEscape(error) + "</p>";
        return shell(err
                + "<form method=\"post\" action=\"login\">"
                + "<input type=\"hidden\" name=\"service\" value=\"" + HtmlUtils.htmlEscape(service) + "\">"
                + "<label for=\"uid\">学工号</label>"
                + "<input id=\"uid\" name=\"uid\" autocomplete=\"username\" required autofocus value=\""
                + HtmlUtils.htmlEscape(uid == null ? "" : uid) + "\" placeholder=\"学生填学号，教职工填工号\">"
                + "<label for=\"password\">密码</label>"
                + "<input id=\"password\" name=\"password\" type=\"password\" autocomplete=\"current-password\" required>"
                + "<button type=\"submit\">登录</button>"
                + "</form>"
                + "<p class=\"tip\">演示说明：这是本系统自带的模拟认证中心，不连接任何真实学校的统一身份认证。"
                + "账号目录见 server/src/main/resources/integration/cas-accounts.json，密码为演示密码。"
                + "登录成功后会带一次性票据（60 秒内有效）跳回三助平台。</p>"
                + "<p class=\"tip\">回跳地址：" + HtmlUtils.htmlEscape(service) + "</p>");
    }

    private static String shell(String inner) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>模拟统一身份认证中心（演示）</title><style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#eef2f7;font:14px/1.6 system-ui,-apple-system,'Microsoft YaHei',sans-serif;color:#1f2937}"
                + ".box{width:100%;max-width:380px;margin:16px;background:#fff;border:1px solid #d6dde8;border-radius:10px;"
                + "padding:28px 28px 20px;box-shadow:0 10px 30px -12px rgba(15,23,42,.18)}"
                + "h1{font-size:18px;margin:0 0 4px}.badge{display:inline-block;font-size:12px;color:#9a3412;"
                + "background:#ffedd5;border-radius:4px;padding:1px 8px;margin-bottom:16px}"
                + "label{display:block;margin:12px 0 4px;font-weight:600;font-size:13px}"
                + "input{box-sizing:border-box;width:100%;height:40px;padding:0 10px;border:1px solid #cbd5e1;"
                + "border-radius:6px;font-size:14px}button{margin-top:18px;width:100%;height:42px;border:0;"
                + "border-radius:6px;background:#2f5d8a;color:#fff;font-size:15px;cursor:pointer}"
                + ".err{color:#b91c1c;background:#fef2f2;border-radius:6px;padding:8px 10px;margin:8px 0}"
                + ".tip{font-size:12px;color:#64748b;margin:14px 0 0;word-break:break-all}"
                + "</style></head><body><main class=\"box\"><h1>模拟统一身份认证中心（演示）</h1>"
                + "<span class=\"badge\">模拟 CAS · 仅供演示</span>" + inner + "</main></body></html>";
    }
}
