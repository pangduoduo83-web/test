package com.example.ioedunew.tenant;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.init.DataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 租户开通:建库 → 登记 → 迁移表结构 → 在该租户上下文中初始化管理员(可选演示数据)。
 * 每一步都幂等,中途失败可用相同参数重试。
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
    /** 租户编码同时作为子域名前缀与库名后缀:小写字母开头,字母数字与短横线,2~32 位 */
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final JdbcTemplate jdbc;
    private final TenantProperties props;
    private final TenantRegistry registry;
    private final TenantSchemaMigrator migrator;
    private final DataSeeder dataSeeder;
    private final String table;

    public TenantProvisioningService(JdbcTemplate jdbc, TenantProperties props, TenantRegistry registry,
                                     TenantSchemaMigrator migrator, DataSeeder dataSeeder) {
        this.jdbc = jdbc;
        this.props = props;
        this.registry = registry;
        this.migrator = migrator;
        this.dataSeeder = dataSeeder;
        this.table = "`" + props.getPlatformDb() + "`.`tenants`";
    }

    /**
     * 开通新租户。返回租户信息;若管理员密码由系统随机生成,结果里附带 initialAdminPassword(只返回这一次)。
     */
    public Map<String, Object> provision(String rawCode, String name, String customDomain,
                                         String adminEmail, String adminPassword, boolean seedDemo) {
        String code = normalizeCode(rawCode);
        if (registry.findByCode(code).isPresent()) {
            throw new BusinessException(409, "租户编码已存在: " + code);
        }
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) {
            throw new BusinessException("租户名称需为 1~100 字");
        }
        String domain = normalizeDomain(customDomain);
        String dbName = "ioedu_" + code.replace('-', '_');

        migrator.ensureDatabase(dbName);
        insertIfAbsent(code, name.trim(), dbName, domain);
        registry.refresh();
        migrator.migrateTenant(dbName);

        String generatedPassword;
        try {
            generatedPassword = TenantContext.runAs(code, () -> {
                try {
                    return dataSeeder.seedIfEmpty(adminEmail, adminPassword, seedDemo);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (RuntimeException e) {
            log.error("租户 {} 初始化数据失败,可用相同参数重试开通", code, e);
            throw new BusinessException(500, "租户库已创建但初始化数据失败: " + e.getMessage());
        }
        log.info("租户 {} 开通完成,库 {}", code, dbName);

        Tenant tenant = registry.require(code);
        Map<String, Object> result = view(tenant);
        boolean passwordGiven = adminPassword != null && !adminPassword.trim().isEmpty();
        if (generatedPassword != null && !passwordGiven) {
            result.put("initialAdminPassword", generatedPassword);
        }
        result.put("adminEmail", adminEmail == null || adminEmail.trim().isEmpty() ? "admin@ioedu.cn" : adminEmail.trim());
        if (!props.getBaseDomain().isEmpty()) {
            result.put("siteHost", code + "." + props.getBaseDomain());
        }
        return result;
    }

    /** 默认租户登记(库名指向老部署的 ioedu 库,数据零迁移);仅在注册表里没有默认租户时调用 */
    public void ensureDefaultTenant() {
        String code = props.getDefaultCode();
        if (registry.findByCode(code).isPresent()) {
            return;
        }
        migrator.ensureDatabase(props.getDefaultDb());
        insertIfAbsent(code, "默认站点", props.getDefaultDb(), null);
        registry.refresh();
        log.info("已登记默认租户 {} → 库 {}", code, props.getDefaultDb());
    }

    public Map<String, Object> updateStatus(String rawCode, String status) {
        String code = normalizeCode(rawCode);
        Tenant tenant = registry.findByCode(code)
                .orElseThrow(() -> new BusinessException(404, "租户不存在: " + code));
        if (!Tenant.STATUS_ACTIVE.equals(status) && !Tenant.STATUS_SUSPENDED.equals(status)) {
            throw new BusinessException("状态只能是 ACTIVE 或 SUSPENDED");
        }
        if (code.equals(props.getDefaultCode()) && Tenant.STATUS_SUSPENDED.equals(status)) {
            throw new BusinessException("默认站点不能停用");
        }
        jdbc.update("UPDATE " + table + " SET status = ?, updated_at = ? WHERE id = ?",
                status, Timestamp.valueOf(LocalDateTime.now()), tenant.getId());
        registry.refresh();
        return view(registry.require(code));
    }

    /**
     * 注销租户:从注册表删除;dropData=true 时连同租户库与上传目录一起删除(不可恢复,调用方必须显式确认)。
     */
    public void deprovision(String rawCode, boolean dropData, java.nio.file.Path uploadRoot) {
        String code = normalizeCode(rawCode);
        Tenant tenant = registry.findByCode(code)
                .orElseThrow(() -> new BusinessException(404, "租户不存在: " + code));
        if (code.equals(props.getDefaultCode())) {
            throw new BusinessException("默认站点不能注销");
        }
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", tenant.getId());
        registry.refresh();
        if (dropData) {
            if (!tenant.getDbName().matches("^[A-Za-z0-9_]{1,64}$") || tenant.getDbName().equals(props.getDefaultDb())
                    || tenant.getDbName().equals(props.getPlatformDb())) {
                throw new BusinessException(500, "拒绝删除库: " + tenant.getDbName());
            }
            jdbc.execute("DROP DATABASE IF EXISTS `" + tenant.getDbName() + "`");
            java.nio.file.Path dir = uploadRoot.resolve(code).normalize();
            if (dir.startsWith(uploadRoot) && dir.toFile().isDirectory()) {
                org.springframework.util.FileSystemUtils.deleteRecursively(dir.toFile());
            }
            log.warn("租户 {} 已注销并删除库 {} 与上传目录", code, tenant.getDbName());
        } else {
            log.warn("租户 {} 已从注册表移除,库 {} 保留", code, tenant.getDbName());
        }
    }

    public Map<String, Object> view(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("dbName", t.getDbName());
        m.put("customDomain", t.getCustomDomain());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        return m;
    }

    private void insertIfAbsent(String code, String name, String dbName, String domain) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE code = ?", Integer.class, code);
        if (exists != null && exists > 0) {
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO " + table + " (code, name, db_name, custom_domain, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                code, name, dbName, domain, Tenant.STATUS_ACTIVE, now, now);
    }

    private String normalizeCode(String raw) {
        if (raw == null) {
            throw new BusinessException("租户编码不能为空");
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException("租户编码须为小写字母开头的 2~32 位字母/数字/短横线,如 c001、shanghai-lab");
        }
        if ("www".equals(code) || "api".equals(code) || "hub".equals(code) || "admin".equals(code)) {
            throw new BusinessException("该编码为系统保留字: " + code);
        }
        return code;
    }

    private String normalizeDomain(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String domain = raw.trim().toLowerCase(Locale.ROOT);
        if (!DOMAIN.matcher(domain).matches()) {
            throw new BusinessException("自有域名格式不正确: " + raw);
        }
        return domain;
    }
}
