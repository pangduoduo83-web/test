package com.example.ioedunew.config;

import com.example.ioedunew.tenant.PlatformTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置:注册认证拦截器、平台令牌拦截器与跨域规则。
 * 上传文件不再用静态资源映射,而由 UploadController 按租户目录提供(见 /uploads/**)。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PlatformTokenInterceptor platformTokenInterceptor;
    private final AuditInterceptor auditInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, PlatformTokenInterceptor platformTokenInterceptor,
                     AuditInterceptor auditInterceptor) {
        this.authInterceptor = authInterceptor;
        this.platformTokenInterceptor = platformTokenInterceptor;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(platformTokenInterceptor).addPathPatterns("/api/platform/**");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/platform/**");
        // 审计放在认证之后,能拿到已解析的 AuthUser;只覆盖管理 / 教师 / 平台 / 商店发布这些有权限意义的写接口
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/api/admin/**", "/api/teacher/**", "/api/platform/**", "/api/store/publish/**",
                        "/api/store/items/*/install", "/api/ai/skills/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
