package com.example.ioedunew.tenant;

import com.example.ioedunew.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点配额:用量统计(用户数 / 7 天活跃 / 上传占用 / 本月 AI Token)与三处硬限制的检查。
 * 统计全部用带库名前缀的 SQL 直接查各租户库,不依赖当前租户上下文,平台侧可以一次拉全站。
 */
@Service
public class TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaService.class);
    private static final long DIR_SIZE_CACHE_MS = 5 * 60_000L;

    private final JdbcTemplate jdbc;
    private final TenantRegistry registry;
    private final TenantProperties props;
    private final Map<String, long[]> dirSizeCache = new ConcurrentHashMap<>();

    @Value("${ioedu.upload-dir}")
    private String uploadDir;

    public TenantQuotaService(JdbcTemplate jdbc, TenantRegistry registry, TenantProperties props) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.props = props;
    }

    /** 某站点的用量快照 */
    public Map<String, Object> usage(Tenant t) {
        String db = "`" + t.getDbName() + "`";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", t.getCode());
        m.put("users", count("SELECT COUNT(*) FROM " + db + ".users"));
        m.put("students", count("SELECT COUNT(*) FROM " + db + ".users WHERE role = 'STUDENT'"));
        m.put("activeUsers7d", count("SELECT COUNT(DISTINCT user_id) FROM " + db + ".learning_activities WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"));
        m.put("projects", count("SELECT COUNT(*) FROM " + db + ".projects"));
        m.put("enrollments", count("SELECT COUNT(*) FROM " + db + ".enrollments"));
        m.put("aiTokensMonth", count("SELECT IFNULL(SUM(prompt_tokens + completion_tokens),0) FROM " + db + ".ai_usage_daily WHERE day >= ?",
                java.sql.Date.valueOf(LocalDate.now().withDayOfMonth(1))));
        m.put("aiRunsMonth", count("SELECT IFNULL(SUM(runs),0) FROM " + db + ".ai_usage_daily WHERE day >= ?",
                java.sql.Date.valueOf(LocalDate.now().withDayOfMonth(1))));
        m.put("storageMb", Math.round(storageSize(t) / 1024.0 / 1024.0));
        m.put("maxUsers", t.getMaxUsers());
        m.put("storageLimitMb", t.getStorageLimitMb());
        m.put("aiMonthlyTokens", t.getAiMonthlyTokens());
        m.put("expiresAt", t.getExpiresAt() == null ? null : t.getExpiresAt().toString());
        m.put("plan", t.getPlan());
        return m;
    }

    /** 当前租户新增用户前检查 */
    public void checkUserQuota(long currentUsers) {
        Tenant t = current();
        if (t != null && t.getMaxUsers() != null && currentUsers >= t.getMaxUsers()) {
            throw new BusinessException(409, "本站用户数已达套餐上限(" + t.getMaxUsers() + " 人),请联系平台提升额度");
        }
    }

    /** 当前租户上传前检查(OSS账本 + 未迁移本地文件 + 本次大小)。 */
    public void checkStorageQuota(long additionalBytes) {
        Tenant t = current();
        if (t == null || t.getStorageLimitMb() == null) {
            return;
        }
        invalidateStorage(t.getCode());
        Long reserved;
        try {
            reserved = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM `" + t.getDbName()
                    + "`.direct_uploads WHERE completed=0 AND expires_at > ?", Long.class, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        } catch (Exception e) { throw new BusinessException(503, "上传配额暂不可用，请确认数据库迁移已完成"); }
        long used = storageSize(t) + additionalBytes + (reserved == null ? 0 : reserved);
        if (used > t.getStorageLimitMb() * 1024L * 1024L) {
            throw new BusinessException(409, "本站存储空间已达上限(" + t.getStorageLimitMb() + " MB),请清理文件或联系平台扩容");
        }
        dirSizeCache.remove(t.getCode());
    }

    private Tenant current() {
        String code = TenantContext.get();
        return code == null ? null : registry.findByCode(code).orElse(null);
    }

    private long count(String sql, Object... args) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            log.debug("统计失败 {}: {}", sql, e.getMessage());
            return 0;
        }
    }

    /** 租户上传目录大小(默认租户还要算上多租户之前的旧目录),5 分钟缓存 */
    public void invalidateStorage(String code) { dirSizeCache.remove(code); }

    private long storageSize(Tenant tenant) {
        String code = tenant.getCode();
        long[] cached = dirSizeCache.get(code);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached[1] < DIR_SIZE_CACHE_MS) {
            return cached[0];
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        // Fail closed when the ledger cannot be read; never silently treat OSS usage as zero.
        List<Map<String, Object>> cloud;
        try { cloud = jdbc.queryForList("SELECT relative_path, size_bytes FROM `" + tenant.getDbName() + "`.stored_files"); }
        catch (Exception e) { throw new BusinessException(503, "存储用量暂不可用，请确认数据库迁移已完成"); }
        Set<String> stored = new HashSet<>(); long size = 0;
        for (Map<String, Object> row : cloud) { stored.add((String) row.get("relative_path")); size += ((Number) row.get("size_bytes")).longValue(); }
        // Local copies kept after migration must not be billed a second time.
        size += sizeOf(root.resolve(code), root.resolve(code), stored);
        if (code.equals(props.getDefaultCode())) {
            // 旧目录:root 下直接的 yyyyMM 子目录
            try (java.util.stream.Stream<Path> s = Files.list(root)) {
                size += s.filter(p -> Files.isDirectory(p) && p.getFileName().toString().matches("\\d{6}"))
                        .mapToLong(p -> sizeOf(p, root, stored)).sum();
            } catch (IOException ignored) {
            }
        }
        dirSizeCache.put(code, new long[]{size, now});
        return size;
    }

    private long sizeOf(Path dir, Path base, Set<String> stored) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> !stored.contains(base.relativize(p).toString().replace('\\', '/'))).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
