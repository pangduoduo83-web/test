package com.example.ioedunew.config;

import com.example.ioedunew.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * JWT 工具:签发与解析访问令牌,载荷为用户 id 与角色。
 * 密钥必须由环境变量 IOEDU_JWT_SECRET 注入且不少于 32 字符,否则启动失败:
 * 多套部署若共用默认密钥,一套系统签出的令牌在另一套也会被接受。
 */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${ioedu.jwt.secret:}")
    private String secret;

    @Value("${ioedu.jwt.expire-hours}")
    private long expireHours;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT 密钥未配置或过短:请通过环境变量 IOEDU_JWT_SECRET 设置一个不少于 "
                    + MIN_SECRET_LENGTH + " 个字符的随机字符串(本地开发可激活 dev profile 使用内置开发密钥)");
        }
        secret = secret.trim();
    }

    /** 签发令牌;tid 记录签发时所在租户,防止一个客户的令牌拿到另一个客户站点使用 */
    public String createToken(Long userId, String role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .claim("tid", TenantContext.require())
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /**
     * 解析令牌,非法或过期时抛出运行时异常,由调用方转成 401。
     */
    public AuthUser parseToken(String token) {
        Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        return new AuthUser(Long.valueOf(claims.getSubject()), claims.get("role", String.class),
                claims.get("tid", String.class));
    }
}
