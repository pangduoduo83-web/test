package com.example.ioedunew.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.regex.Pattern;

/**
 * 按库执行 Flyway:平台库用 db/platform,每个租户库用 db/migration。
 * Boot 自带的 Flyway 自动配置已关闭(spring.flyway.enabled=false),迁移统一由这里在启动与开通时触发。
 */
@Component
public class TenantSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrator.class);
    private static final Pattern DB_NAME = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public TenantSchemaMigrator(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    /** 建库(已存在则跳过),需要连接账号具备 CREATE 权限 */
    public void ensureDatabase(String dbName) {
        checkName(dbName);
        jdbc.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4");
    }

    public void migratePlatform(String dbName) {
        checkName(dbName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(dbName)
                .defaultSchema(dbName)
                .locations("classpath:db/platform")
                .load();
        MigrateResult result = migrateWithRepair(flyway, dbName);
        if (result.migrationsExecuted > 0) {
            log.info("平台库 {} 已执行 {} 个迁移", dbName, result.migrationsExecuted);
        }
    }

    /** 老库(ddl-auto 时代建表)首次接入会被标记为基线 V1,之后只跑增量脚本 */
    public void migrateTenant(String dbName) {
        checkName(dbName);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(dbName)
                .defaultSchema(dbName)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        MigrateResult result = migrateWithRepair(flyway, dbName);
        if (result.migrationsExecuted > 0) {
            log.info("租户库 {} 已执行 {} 个迁移,当前版本 {}", dbName, result.migrationsExecuted, result.targetSchemaVersion);
        }
    }

    /**
     * 校验失败(某次迁移执行失败留下 success=0 的记录,或已应用脚本被修正导致校验和变化)时,
     * 先 repair 清理失败记录并对齐校验和,再重试一次。脚本都按幂等写法编写,失败后修正脚本再启动即可自愈,
     * 不需要人工进库改 flyway_schema_history。
     */
    private MigrateResult migrateWithRepair(Flyway flyway, String dbName) {
        try {
            return flyway.migrate();
        } catch (FlywayValidateException e) {
            log.warn("库 {} 迁移校验失败,执行 repair 后重试: {}", dbName, e.getMessage());
            flyway.repair();
            return flyway.migrate();
        }
    }

    private void checkName(String dbName) {
        if (dbName == null || !DB_NAME.matcher(dbName).matches()) {
            throw new IllegalArgumentException("非法库名: " + dbName);
        }
    }
}
