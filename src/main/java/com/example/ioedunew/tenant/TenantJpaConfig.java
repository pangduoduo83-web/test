package com.example.ioedunew.tenant;

import org.hibernate.MultiTenancyStrategy;
import org.hibernate.cfg.Environment;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把多租户连接提供者与租户解析器注入 Hibernate。
 * 开启多租户后 Hibernate 不再做 ddl-auto 的建表/校验,表结构完全交给 TenantSchemaMigrator(Flyway)。
 */
@Configuration
public class TenantJpaConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantHibernateProperties(SchemaMultiTenantConnectionProvider connectionProvider,
                                                                   TenantIdentifierResolver identifierResolver) {
        return props -> {
            props.put(Environment.MULTI_TENANT, MultiTenancyStrategy.SCHEMA);
            props.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            props.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
        };
    }
}
