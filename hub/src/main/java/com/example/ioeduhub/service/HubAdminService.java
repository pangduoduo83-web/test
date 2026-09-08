package com.example.ioeduhub.service;

import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.config.AdminJwt;
import com.example.ioeduhub.config.HubAuthFilter;
import com.example.ioeduhub.config.HubProperties;
import com.example.ioeduhub.entity.HubAdmin;
import com.example.ioeduhub.entity.HubInstall;
import com.example.ioeduhub.entity.HubItem;
import com.example.ioeduhub.entity.HubItemGrant;
import com.example.ioeduhub.entity.HubItemVersion;
import com.example.ioeduhub.entity.HubReviewLog;
import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.repository.AdminRepo;
import com.example.ioeduhub.repository.GrantRepo;
import com.example.ioeduhub.repository.InstallRepo;
import com.example.ioeduhub.repository.ItemRepo;
import com.example.ioeduhub.repository.ReviewLogRepo;
import com.example.ioeduhub.repository.TenantRepo;
import com.example.ioeduhub.repository.VersionRepo;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 平台管理员能力:登录、审核上架/驳回/下架、定向分享、租户接入(签发 API Key)。
 * 首次启动按配置创建平台管理员(密码留空则随机生成并打印一次)。
 */
@Service
public class HubAdminService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(HubAdminService.class);
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HubProperties props;
    private final AdminRepo adminRepo;
    private final TenantRepo tenantRepo;
    private final ItemRepo itemRepo;
    private final VersionRepo versionRepo;
    private final GrantRepo grantRepo;
    private final ReviewLogRepo reviewLogRepo;
    private final InstallRepo installRepo;
    private final AdminJwt adminJwt;
    private final StoreService storeService;

    public HubAdminService(HubProperties props, AdminRepo adminRepo, TenantRepo tenantRepo, ItemRepo itemRepo,
                           VersionRepo versionRepo, GrantRepo grantRepo, ReviewLogRepo reviewLogRepo,
                           InstallRepo installRepo, AdminJwt adminJwt, StoreService storeService) {
        this.props = props;
        this.adminRepo = adminRepo;
        this.tenantRepo = tenantRepo;
        this.itemRepo = itemRepo;
        this.versionRepo = versionRepo;
        this.grantRepo = grantRepo;
        this.reviewLogRepo = reviewLogRepo;
        this.installRepo = installRepo;
        this.adminJwt = adminJwt;
        this.storeService = storeService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (adminRepo.count() > 0) {
            return;
        }
        String username = props.getAdminUsername() == null || props.getAdminUsername().trim().isEmpty()
                ? "platform" : props.getAdminUsername().trim();
        boolean generated = props.getAdminPassword() == null || props.getAdminPassword().trim().isEmpty();
        String password = generated ? random(16) : props.getAdminPassword().trim();
        HubAdmin admin = new HubAdmin();
        admin.setUsername(username);
        admin.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        adminRepo.save(admin);
        if (generated) {
            log.warn("已创建平台管理员 {},随机初始密码为: {}  —— 请立即登录修改,本密码只显示这一次", username, password);
        } else {
            log.info("已创建平台管理员 {}(密码来自配置)", username);
        }
    }

    // ---------- 登录 ----------

    public Map<String, Object> login(String username, String password) {
        HubAdmin admin = adminRepo.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new BusinessException("账号或密码错误"));
        if (password == null || !BCrypt.checkpw(password, admin.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", adminJwt.create(admin.getUsername()));
        m.put("username", admin.getUsername());
        return m;
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        HubAdmin admin = adminRepo.findByUsername(username).orElseThrow(() -> new BusinessException(401, "未登录"));
        if (oldPassword == null || !BCrypt.checkpw(oldPassword, admin.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("新密码不少于 8 位");
        }
        admin.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        adminRepo.save(admin);
    }

    // ---------- 条目审核 ----------

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", itemRepo.countByReviewStatus(HubItem.ST_PENDING));
        m.put("approved", itemRepo.countByReviewStatus(HubItem.ST_APPROVED));
        m.put("rejected", itemRepo.countByReviewStatus(HubItem.ST_REJECTED));
        m.put("offline", itemRepo.countByReviewStatus(HubItem.ST_OFFLINE));
        m.put("featured", itemRepo.countByFeaturedTrue());
        m.put("tenants", tenantRepo.count());
        m.put("installs", installRepo.count());
        return m;
    }

    public List<Map<String, Object>> items(String status) {
        List<HubItem> items = status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)
                ? itemRepo.findAllByOrderByUpdatedAtDesc()
                : itemRepo.findByReviewStatusOrderByUpdatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        List<Map<String, Object>> list = new ArrayList<>();
        for (HubItem item : items) {
            list.add(storeService.view(item));
        }
        return list;
    }

    public Map<String, Object> item(Long id) {
        HubItem item = itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        Map<String, Object> m = storeService.view(item);
        List<Map<String, Object>> versions = new ArrayList<>();
        for (HubItemVersion v : versionRepo.findByItemIdOrderByVersionNoDesc(id)) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", v.getId());
            vm.put("versionNo", v.getVersionNo());
            vm.put("changelog", v.getChangelog());
            vm.put("createdBy", v.getCreatedBy());
            vm.put("createdAt", v.getCreatedAt());
            vm.put("current", v.getId().equals(item.getCurrentVersionId()));
            versions.add(vm);
        }
        m.put("versions", versions);
        List<Map<String, Object>> grants = new ArrayList<>();
        for (HubItemGrant g : grantRepo.findByItemId(id)) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("tenantId", g.getTenantId());
            tenantRepo.findById(g.getTenantId()).ifPresent(t -> {
                gm.put("tenantCode", t.getCode());
                gm.put("tenantName", t.getName());
            });
            gm.put("grantedBy", g.getGrantedBy());
            gm.put("grantedAt", g.getGrantedAt());
            grants.add(gm);
        }
        m.put("grants", grants);
        m.put("reviewLogs", reviewLogRepo.findByItemIdOrderByReviewedAtDesc(id));
        return m;
    }

    /** 按客户站点汇总的安装情况:每个站点装过几次、最近装的是哪个版本、谁装的 */
    public Map<String, Object> installsByTenant(Long id) {
        itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        Map<Long, Integer> versionNos = new LinkedHashMap<>();
        for (HubItemVersion v : versionRepo.findByItemIdOrderByVersionNoDesc(id)) {
            versionNos.put(v.getId(), v.getVersionNo());
        }
        Map<Long, Map<String, Object>> byTenant = new LinkedHashMap<>();
        List<Map<String, Object>> recent = new ArrayList<>();
        List<HubInstall> installs = installRepo.findByItemIdOrderByInstalledAtDesc(id);
        for (HubInstall in : installs) {
            Map<String, Object> row = byTenant.get(in.getTenantId());
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("tenantId", in.getTenantId());
                HubTenant t = tenantRepo.findById(in.getTenantId()).orElse(null);
                row.put("tenantCode", t == null ? null : t.getCode());
                row.put("tenantName", t == null ? "已注销客户 #" + in.getTenantId() : t.getName());
                row.put("tenantStatus", t == null ? null : t.getStatus());
                row.put("count", 0);
                row.put("lastVersionNo", versionNos.get(in.getVersionId()));
                row.put("lastInstalledAt", in.getInstalledAt());
                row.put("lastInstalledBy", in.getInstalledBy());
                byTenant.put(in.getTenantId(), row);
            }
            row.put("count", (Integer) row.get("count") + 1);
            if (recent.size() < 20) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("tenantName", row.get("tenantName"));
                r.put("versionNo", versionNos.get(in.getVersionId()));
                r.put("installedBy", in.getInstalledBy());
                r.put("installedAt", in.getInstalledAt());
                recent.add(r);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", installs.size());
        m.put("tenantCount", byTenant.size());
        m.put("byTenant", new ArrayList<>(byTenant.values()));
        m.put("recent", recent);
        return m;
    }

    /** 最新版本的完整内容(审核时预览用) */
    public Object latestPayload(Long id) {
        List<HubItemVersion> versions = versionRepo.findByItemIdOrderByVersionNoDesc(id);
        if (versions.isEmpty()) {
            throw new BusinessException(404, "条目没有版本");
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(versions.get(0).getPayload());
        } catch (Exception e) {
            throw new BusinessException(500, "条目内容损坏");
        }
    }

    /**
     * decision: APPROVED 上架最新版本 / REJECTED 驳回最新版本(已上架的老版本继续展示) / OFFLINE 整体下架。
     * 下架后再次 APPROVED 即可恢复。
     */
    @Transactional
    public Map<String, Object> review(Long id, String decision, String comment, String reviewer) {
        HubItem item = itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        List<HubItemVersion> versions = versionRepo.findByItemIdOrderByVersionNoDesc(id);
        HubItemVersion latest = versions.isEmpty() ? null : versions.get(0);
        String d = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        switch (d) {
            case HubItem.ST_APPROVED:
                if (latest == null) {
                    throw new BusinessException("条目没有可上架的版本");
                }
                item.setCurrentVersionId(latest.getId());
                item.setReviewStatus(HubItem.ST_APPROVED);
                break;
            case HubItem.ST_REJECTED:
                if (comment == null || comment.trim().isEmpty()) {
                    throw new BusinessException("驳回请填写原因");
                }
                item.setReviewStatus(HubItem.ST_REJECTED);
                break;
            case HubItem.ST_OFFLINE:
                item.setReviewStatus(HubItem.ST_OFFLINE);
                break;
            default:
                throw new BusinessException("未知审核动作: " + decision);
        }
        item.setReviewComment(comment == null ? null : comment.trim());
        item.setUpdatedAt(LocalDateTime.now());
        itemRepo.save(item);

        HubReviewLog logEntry = new HubReviewLog();
        logEntry.setItemId(id);
        logEntry.setVersionId(latest == null ? null : latest.getId());
        logEntry.setReviewer(reviewer);
        logEntry.setDecision(d);
        logEntry.setComment(item.getReviewComment());
        reviewLogRepo.save(logEntry);
        return storeService.view(item);
    }

    /** 批量审核:逐条执行,单条失败不影响其他,返回成功数与失败明细 */
    @Transactional
    public Map<String, Object> batchReview(List<Long> ids, String decision, String comment, String reviewer) {
        int ok = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(ids)) {
            try {
                review(id, decision, comment, reviewer);
                ok++;
            } catch (BusinessException e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", id);
                f.put("message", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("failed", failed);
        return m;
    }

    @Transactional
    public Map<String, Object> setFeatured(Long id, boolean featured) {
        HubItem item = itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        if (featured && item.getCurrentVersionId() == null) {
            throw new BusinessException("条目还没有上架版本,先通过审核再推荐");
        }
        item.setFeatured(featured);
        item.setFeaturedAt(featured ? LocalDateTime.now() : null);
        itemRepo.save(item);
        return storeService.view(item);
    }

    @Transactional
    public Map<String, Object> updateVisibility(Long id, String visibility) {
        HubItem item = itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        String v = visibility == null ? "" : visibility.trim().toUpperCase(Locale.ROOT);
        if (!HubItem.VIS_PUBLIC.equals(v) && !HubItem.VIS_RESTRICTED.equals(v)) {
            throw new BusinessException("可见范围只能是 PUBLIC 或 RESTRICTED");
        }
        item.setVisibility(v);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepo.save(item);
        return storeService.view(item);
    }

    /** 用给定租户列表整体替换定向分享名单 */
    @Transactional
    public Map<String, Object> replaceGrants(Long id, List<Long> tenantIds, String grantedBy) {
        itemRepo.findById(id).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        grantRepo.deleteByItemId(id);
        if (tenantIds != null) {
            for (Long tenantId : new java.util.LinkedHashSet<>(tenantIds)) {
                if (!tenantRepo.existsById(tenantId)) {
                    throw new BusinessException("租户不存在: " + tenantId);
                }
                HubItemGrant g = new HubItemGrant();
                g.setItemId(id);
                g.setTenantId(tenantId);
                g.setGrantedBy(grantedBy);
                grantRepo.save(g);
            }
        }
        return item(id);
    }

    // ---------- 租户接入 ----------

    public List<Map<String, Object>> tenants() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (HubTenant t : tenantRepo.findAll()) {
            list.add(tenantView(t));
        }
        return list;
    }

    /** 接入客户并签发 API Key(明文只返回这一次) */
    @Transactional
    public Map<String, Object> createTenant(String rawCode, String name) {
        String code = rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException("客户编码须为小写字母开头的 2~32 位字母/数字/短横线");
        }
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) {
            throw new BusinessException("客户名称需为 1~100 字");
        }
        if (tenantRepo.findByCode(code).isPresent()) {
            throw new BusinessException(409, "客户编码已存在: " + code);
        }
        String apiKey = "hk_" + random(40);
        HubTenant t = new HubTenant();
        t.setCode(code);
        t.setName(name.trim());
        t.setApiKeyHash(HubAuthFilter.sha256(apiKey));
        t = tenantRepo.save(t);
        Map<String, Object> m = tenantView(t);
        m.put("apiKey", apiKey);
        return m;
    }

    @Transactional
    public Map<String, Object> rotateKey(Long id) {
        HubTenant t = tenantRepo.findById(id).orElseThrow(() -> new BusinessException(404, "客户不存在"));
        String apiKey = "hk_" + random(40);
        t.setApiKeyHash(HubAuthFilter.sha256(apiKey));
        tenantRepo.save(t);
        Map<String, Object> m = tenantView(t);
        m.put("apiKey", apiKey);
        return m;
    }

    @Transactional
    public Map<String, Object> updateTenantStatus(Long id, String status) {
        HubTenant t = tenantRepo.findById(id).orElseThrow(() -> new BusinessException(404, "客户不存在"));
        if (!"ACTIVE".equals(status) && !"SUSPENDED".equals(status)) {
            throw new BusinessException("状态只能是 ACTIVE 或 SUSPENDED");
        }
        t.setStatus(status);
        tenantRepo.save(t);
        return tenantView(t);
    }

    private Map<String, Object> tenantView(HubTenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    private static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
