package com.example.ioedunew.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具:签发与解析访问令牌,载荷为用户 id 与角色。
 */
@Component
public class JwtUtil {

    @Value("${ioedu.jwt.secret}")
    private String secret;

    @Value("${ioedu.jwt.expire-hours}")
    private long expireHours;

    public String createToken(Long userId, String role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
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
        return new AuthUser(Long.valueOf(claims.getSubject()), claims.get("role", String.class));
    }
}
