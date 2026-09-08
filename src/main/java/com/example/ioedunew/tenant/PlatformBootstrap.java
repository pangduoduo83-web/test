package com.example.ioedunew.tenant;

import com.example.ioedunew.init.DataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动引导:平台库建表 → 登记默认租户(老部署的 ioedu 库) → 逐个租户执行 Flyway 与初始数据。
 * 在所有单例 bean 就绪后、Web 服务器开始接受请求之前执行,因此不会有请求打到尚未迁移的库上。
 */
@Component
public class PlatformBootstrap implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final TenantProperties props;
    private final TenantSchemaMigrator migrator;
    private final TenantRegistry registry;
    private final TenantProvisioningService provisioning;
    private final DataSeeder dataSeeder;

    @Value("${ioedu.seed.admin-email:admin@ioedu.cn}")
    private String adminEmail;

    @Value("${ioedu.seed.admin-password:}")
    private String adminPassword;

    @Value("${ioedu.seed.demo:false}")
    private boolean seedDemo;

    public PlatformBootstrap(TenantProperties props, TenantSchemaMigrator migrator, TenantRegistry registry,
                             TenantProvisioningService provisioning, DataSeeder dataSeeder) {
        this.props = props;
        this.migrator = migrator;
        this.registry = registry;
        this.provisioning = provisioning;
        this.dataSeeder = dataSeeder;
    }

    @Override
    public void afterSingletonsInstantiated() {
        long start = System.currentTimeMillis();
        migrator.ensureDatabase(props.getPlatformDb());
        migrator.migratePlatform(props.getPlatformDb());
        registry.refresh();
        provisioning.ensureDefaultTenant();

        int migrated = 0;
        for (Tenant tenant : registry.active()) {
            migrator.ensureDatabase(tenant.getDbName());
            migrator.migrateTenant(tenant.getDbName());
            TenantContext.runAs(tenant.getCode(), () -> {
                try {
                    dataSeeder.seedIfEmpty(adminEmail, adminPassword, seedDemo);
                } catch (Exception e) {
                    throw new IllegalStateException("租户 " + tenant.getCode() + " 初始数据失败", e);
                }
            });
            migrated++;
        }
        log.info("多租户引导完成:{} 个活动租户,耗时 {} ms(默认租户 {} → 库 {}{})", migrated,
                System.currentTimeMillis() - start, props.getDefaultCode(), props.getDefaultDb(),
                props.getBaseDomain().isEmpty() ? ",未配置根域名,按默认租户服务全部 Host" : ",根域名 " + props.getBaseDomain());
    }
}
