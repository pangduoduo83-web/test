package com.example.ioeduhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Data
@Component
@ConfigurationProperties(prefix = "hub")
public class HubProperties {

    private String jwtSecret = "";
    private long jwtExpireHours = 24;
    private String adminUsername = "platform";
    private String adminPassword = "";
    private String assetDir = "hub-assets";

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException("HUB_JWT_SECRET 未配置或少于 32 个字符(本地开发可激活 dev profile)");
        }
        jwtSecret = jwtSecret.trim();
    }
}
