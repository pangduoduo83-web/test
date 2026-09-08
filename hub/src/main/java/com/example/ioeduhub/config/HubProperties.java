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
    /** 客户站点后端(多租户主系统)的地址与平台令牌,用于在商店后台一键开通客户站点;留空则「客户站点」页只读 */
    private String platformApiUrl = "";
    private String platformToken = "";
    /** 客户站点访问地址模板,{code} 会替换为站点编码,如 https://{code}.labcloud.com.cn:8443 */
    private String siteUrlTemplate = "";

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException("HUB_JWT_SECRET 未配置或少于 32 个字符(本地开发可激活 dev profile)");
        }
        jwtSecret = jwtSecret.trim();
        platformApiUrl = platformApiUrl == null ? "" : platformApiUrl.trim().replaceAll("/+$", "");
        platformToken = platformToken == null ? "" : platformToken.trim();
        siteUrlTemplate = siteUrlTemplate == null ? "" : siteUrlTemplate.trim();
    }

    public boolean platformConfigured() {
        return !platformApiUrl.isEmpty() && platformToken.length() >= 16;
    }
}
