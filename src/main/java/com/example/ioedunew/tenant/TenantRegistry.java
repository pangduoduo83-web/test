package com.example.ioedunew.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户注册表:读平台库 tenants 表并缓存在内存,负责 Host → 租户 的解析。
 * 只用 JdbcTemplate 且表名带库名前缀,因此不依赖 JPA,也不受当前连接切到哪个租户库影响。
 */
@Service
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);
    /** 未命中时最多每 5 秒回源刷新一次,兼顾多副本下的新租户可见性与防刷 */
    private static final long MISS_REFRESH_INTERVAL_MS = 5_000L;

    private final JdbcTemplate jdbc;
    private final TenantProperties props;
    private final String table;

    private volatile Map<String, Tenant> byCode = Collections.emptyMap();
    private volatile Map<String, Tenant> byDomain = Collections.emptyMap();
    private volatile long lastMissRefreshAt = 0L;

    private static final RowMapper<Tenant> ROW_MAPPER = (rs, i) -> {
        Tenant t = new Tenant();
        t.setId(rs.getLong("id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setDbName(rs.getString("db_name"));
        t.setCustomDomain(rs.getString("custom_domain"));
        t.setStatus(rs.getString("status"));
        t.setCreatedAt(toLocal(rs.getTimestamp("created_at")));
        t.setUpdatedAt(toLocal(rs.getTimestamp("updated_at")));
        return t;
    };

    public TenantRegistry(JdbcTemplate jdbc, TenantProperties props) {
        this.jdbc = jdbc;
        this.props = props;
        this.table = "`" + props.getPlatformDb() + "`.`tenants`";
    }

    /** 从平台库重新加载全部租户(启动、开通、状态变更后调用) */
    public synchronized void refresh() {
        List<Tenant> list = jdbc.query("SELECT id, code, name, db_name, custom_domain, status, created_at, updated_at FROM "
                + table + " ORDER BY id", ROW_MAPPER);
        Map<String, Tenant> codes = new HashMap<>();
        Map<String, Tenant> domains = new HashMap<>();
        for (Tenant t : list) {
            codes.put(t.getCode().toLowerCase(Locale.ROOT), t);
            if (t.getCustomDomain() != null && !t.getCustomDomain().trim().isEmpty()) {
                domains.put(t.getCustomDomain().trim().toLowerCase(Locale.ROOT), t);
            }
        }
        byCode = codes;
        byDomain = domains;
        log.info("租户注册表已加载 {} 个租户", list.size());
    }

    public List<Tenant> all() {
        return byCode.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    public List<Tenant> active() {
        List<Tenant> list = new ArrayList<>();
        for (Tenant t : all()) {
            if (t.isActive()) {
                list.add(t);
            }
        }
        return list;
    }

    public Optional<Tenant> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String key = code.trim().toLowerCase(Locale.ROOT);
        Tenant t = byCode.get(key);
        if (t == null && refreshOnMiss()) {
            t = byCode.get(key);
        }
        return Optional.ofNullable(t);
    }

    public Tenant require(String code) {
        return findByCode(code).orElseThrow(() -> new IllegalStateException("租户不存在: " + code));
    }

    /** 租户编码 → 库名,供 Hibernate 连接提供者切库 */
    public String dbNameOf(String code) {
        return require(code).getDbName();
    }

    public Optional<Tenant> defaultTenant() {
        return findByCode(props.getDefaultCode());
    }

    /**
     * 按请求 Host 解析租户:自有域名精确匹配 → 根域/www 为默认租户 → 子域名前缀为租户编码。
     * 返回空表示 Host 不属于本平台任何已知形式(localhost、IP、陌生域名),由调用方决定落默认租户还是拒绝。
     */
    public Optional<Tenant> resolveByHost(String hostHeader) {
        if (hostHeader == null || hostHeader.trim().isEmpty()) {
            return Optional.empty();
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        host = host.replaceAll("\\.+$", "");

        Tenant byOwnDomain = byDomain.get(host);
        if (byOwnDomain != null) {
            return Optional.of(byOwnDomain);
        }
        String base = props.getBaseDomain();
        if (base.isEmpty()) {
            return Optional.empty();
        }
        if (host.equals(base) || host.equals("www." + base)) {
            return defaultTenant();
        }
        if (host.endsWith("." + base)) {
            String sub = host.substring(0, host.length() - base.length() - 1);
            int lastDot = sub.lastIndexOf('.');
            String code = lastDot >= 0 ? sub.substring(lastDot + 1) : sub;
            if ("www".equals(code)) {
                return defaultTenant();
            }
            return findByCode(code);
        }
        return Optional.empty();
    }

    private boolean refreshOnMiss() {
        long now = System.currentTimeMillis();
        if (now - lastMissRefreshAt < MISS_REFRESH_INTERVAL_MS) {
            return false;
        }
        lastMissRefreshAt = now;
        try {
            refresh();
            return true;
        } catch (Exception e) {
            log.warn("刷新租户注册表失败: {}", e.getMessage());
            return false;
        }
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
