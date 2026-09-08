package com.example.ioedunew.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 多租户配置(前缀 ioedu.tenant):
 * - platform-db:平台库名,存放 tenants 注册表;
 * - default-code / default-db:默认租户及其库(老部署的 ioedu 库自动成为默认租户,数据零迁移);
 * - base-domain:子域名路由的根域,如 labcloud.com.cn → c001.labcloud.com.cn 解析为租户 c001;
 * - strict-host:未匹配到任何租户的 Host 是否拒绝(false 时落到默认租户,适合单租户/IP 直连);
 * - allow-header-override:是否允许 X-Tenant-Id 请求头指定租户(仅本地联调开启)。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ioedu.tenant")
public class TenantProperties {

    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private String platformDb = "ioedu_platform";
    private String defaultCode = "default";
    private String defaultDb = "ioedu";
    private String baseDomain = "";
    private boolean strictHost = false;
    private boolean allowHeaderOverride = false;

    @PostConstruct
    void normalize() {
        if (!DB_NAME.matcher(platformDb).matches() || !DB_NAME.matcher(defaultDb).matches()) {
            throw new IllegalStateException("ioedu.tenant.platform-db / default-db 只能包含字母数字下划线");
        }
        defaultCode = defaultCode.trim().toLowerCase(Locale.ROOT);
        baseDomain = baseDomain == null ? "" : baseDomain.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$", "");
    }
}
