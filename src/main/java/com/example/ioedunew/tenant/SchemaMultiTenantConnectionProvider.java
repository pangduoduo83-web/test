package com.example.ioedunew.tenant;

import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hibernate 多租户连接提供者(SCHEMA 策略):所有租户共用一个 HikariCP 连接池,
 * 借出连接时按租户切到对应库(MySQL 中 schema 即 database),归还前切回默认库。
 * 实体、仓储与业务代码因此无需感知租户。
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider {

    private static final long serialVersionUID = 1L;

    private final transient DataSource dataSource;
    private final transient TenantRegistry registry;

    /** 连接串里的默认库,首次借出时记录,归还时统一切回(连接池不会自动重置 catalog) */
    private volatile String defaultCatalog;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource, TenantRegistry registry) {
        this.dataSource = dataSource;
        this.registry = registry;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (tenantIdentifier == null || TenantIdentifierResolver.UNBOUND.equals(tenantIdentifier)) {
            throw new HibernateException("当前线程未绑定租户,请通过 TenantContext.runAs 执行数据库操作");
        }
        Connection connection = dataSource.getConnection();
        try {
            if (defaultCatalog == null) {
                defaultCatalog = connection.getCatalog();
            }
            connection.setCatalog(registry.dbNameOf(tenantIdentifier));
            return connection;
        } catch (RuntimeException | SQLException e) {
            connection.close();
            throw new HibernateException("无法切换到租户库 [" + tenantIdentifier + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            if (defaultCatalog != null) {
                connection.setCatalog(defaultCatalog);
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
