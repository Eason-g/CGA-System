package edu.cga.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import edu.cga.config.AppProperties;
import edu.cga.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * JWT 签发与校验（HS256）。token 只携带用户 ID（sub）与角色，其他信息每次从数据库读取。
 */
@Service
public class JwtService {

    private static final String ISSUER = "cga-server";

    private final SecretKey key;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        String secret = props.getJwt().getSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足 32 字节，无法用于 HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getJwt().getTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 校验签名与有效期，成功返回用户 ID；任何异常（过期、篡改、格式错）都返回空 */
    public Optional<String> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
