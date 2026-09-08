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

    public WebConfig(AuthInterceptor authInterceptor, PlatformTokenInterceptor platformTokenInterceptor) {
        this.authInterceptor = authInterceptor;
        this.platformTokenInterceptor = platformTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(platformTokenInterceptor).addPathPatterns("/api/platform/**");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/platform/**");
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
