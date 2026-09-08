package com.example.ioeduhub.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

/** 平台管理员登录令牌 */
@Component
public class AdminJwt {

    private final HubProperties props;

    public AdminJwt(HubProperties props) {
        this.props = props;
    }

    public String create(String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + props.getJwtExpireHours() * 3600_000L))
                .signWith(SignatureAlgorithm.HS256, props.getJwtSecret())
                .compact();
    }

    /** 返回用户名,非法或过期抛异常 */
    public String parse(String token) {
        Claims claims = Jwts.parser().setSigningKey(props.getJwtSecret()).parseClaimsJws(token).getBody();
        return claims.getSubject();
    }
}
