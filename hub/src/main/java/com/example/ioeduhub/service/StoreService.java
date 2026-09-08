package com.example.ioeduhub.service;

import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.entity.HubInstall;
import com.example.ioeduhub.entity.HubItem;
import com.example.ioeduhub.entity.HubItemVersion;
import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.repository.GrantRepo;
import com.example.ioeduhub.repository.InstallRepo;
import com.example.ioeduhub.repository.ItemRepo;
import com.example.ioeduhub.repository.VersionRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 面向客户实例的商店能力:浏览可见条目、安装(取 payload 并记录)、发布(新条目或新版本)、我的发布。
 */
@Service
public class StoreService {

    private final ItemRepo itemRepo;
    private final VersionRepo versionRepo;
    private final GrantRepo grantRepo;
    private final InstallRepo installRepo;
    private final ObjectMapper objectMapper;

    public StoreService(ItemRepo itemRepo, VersionRepo versionRepo, GrantRepo grantRepo,
                        InstallRepo installRepo, ObjectMapper objectMapper) {
        this.itemRepo = itemRepo;
        this.versionRepo = versionRepo;
        this.grantRepo = grantRepo;
        this.installRepo = installRepo;
        this.objectMapper = objectMapper;
    }

    /** 可见条目列表,支持关键字与分类过滤,并标注该租户已安装的版本 */
    public List<Map<String, Object>> visibleItems(HubTenant tenant, String keyword, String category) {
        Map<Long, Integer> installed = installedVersions(tenant.getId());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> list = new ArrayList<>();
        for (HubItem item : itemRepo.findVisibleTo(tenant.getId())) {
            if (category != null && !category.trim().isEmpty() && !category.trim().equals(item.getCategory())) {
                continue;
            }
            if (!kw.isEmpty()) {
                String hay = (item.getTitle() + " " + nullSafe(item.getSummary()) + " " + nullSafe(item.getTags())).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            Map<String, Object> m = view(item);
            m.put("installedVersionNo", installed.get(item.getId()));
            m.put("mine", item.getPublisherTenantId().equals(tenant.getId()));
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> visibleItem(HubTenant tenant, Long itemId) {
        HubItem item = requireVisible(tenant, itemId);
        Map<String, Object> m = view(item);
        m.put("installedVersionNo", installedVersions(tenant.getId()).get(item.getId()));
        m.put("mine", item.getPublisherTenantId().equals(tenant.getId()));
        List<Map<String, Object>> versions = new ArrayList<>();
        for (HubItemVersion v : versionRepo.findByItemIdOrderByVersionNoDesc(itemId)) {
            if (item.getCurrentVersionId() != null && v.getVersionNo() <= currentVersionNo(item)) {
                Map<String, Object> vm = new LinkedHashMap<>();
                vm.put("versionNo", v.getVersionNo());
                vm.put("changelog", v.getChangelog());
                vm.put("createdAt", v.getCreatedAt());
                versions.add(vm);
            }
        }
        m.put("versions", versions);
        return m;
    }

    /** 安装:返回当前上架版本的 payload,并记录安装 */
    @Transactional
    public Map<String, Object> install(HubTenant tenant, Long itemId, String actingUser) {
        HubItem item = requireVisible(tenant, itemId);
        HubItemVersion version = versionRepo.findById(item.getCurrentVersionId())
                .orElseThrow(() -> new BusinessException(404, "条目版本不存在"));
        HubInstall install = new HubInstall();
        install.setItemId(itemId);
        install.setVersionId(version.getId());
        install.setTenantId(tenant.getId());
        install.setInstalledBy(actingUser);
        installRepo.save(install);
        item.setInstallCount(item.getInstallCount() + 1);
        itemRepo.save(item);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item", view(item));
        m.put("versionNo", version.getVersionNo());
        try {
            m.put("payload", objectMapper.readTree(version.getPayload()));
        } catch (Exception e) {
            throw new BusinessException(500, "条目内容损坏");
        }
        return m;
    }

    /**
     * 发布:itemId 为空或不属于本租户 → 新条目(待审核);属于本租户 → 追加新版本并重新进入待审核,
     * 审核通过前商店里仍展示上一个已通过版本(可见性只看 currentVersionId 与是否下架)。
     */
    @Transactional
    public Map<String, Object> publish(HubTenant tenant, String actingUser, JsonNode body) {
        String title = text(body, "title");
        if (title.isEmpty() || title.length() > 100) {
            throw new BusinessException("标题需为 1~100 字");
        }
        JsonNode payload = body.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new BusinessException("缺少项目内容 payload");
        }
        Long itemId = body.hasNonNull("itemId") ? body.get("itemId").asLong() : null;
        HubItem item = itemId == null ? null : itemRepo.findById(itemId).orElse(null);
        boolean newItem = item == null || !item.getPublisherTenantId().equals(tenant.getId());
        if (newItem) {
            item = new HubItem();
            item.setType(HubItem.TYPE_PROJECT);
            item.setPublisherTenantId(tenant.getId());
            item.setPublisherTenantName(tenant.getName());
            item.setLatestVersionNo(0);
            item.setVisibility(HubItem.VIS_PUBLIC);
        }
        item.setTitle(title);
        item.setSummary(cut(text(body, "summary"), 300));
        item.setCategory(cut(text(body, "category"), 30));
        item.setTags(body.hasNonNull("tags") && body.get("tags").isArray() ? body.get("tags").toString() : "[]");
        item.setCoverUrl(cut(text(body, "coverUrl"), 255));
        item.setPublisherUserName(cut(actingUser, 50));
        item.setReviewStatus(HubItem.ST_PENDING);
        item.setReviewComment(null);
        item.setLatestVersionNo(item.getLatestVersionNo() + 1);
        item.setUpdatedAt(LocalDateTime.now());
        item = itemRepo.save(item);

        HubItemVersion version = new HubItemVersion();
        version.setItemId(item.getId());
        version.setVersionNo(item.getLatestVersionNo());
        version.setPayload(payload.toString());
        version.setChangelog(cut(text(body, "changelog"), 500));
        version.setCreatedBy(cut(actingUser, 50));
        versionRepo.save(version);

        Map<String, Object> m = view(item);
        m.put("newItem", newItem);
        return m;
    }

    public List<Map<String, Object>> mine(HubTenant tenant) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (HubItem item : itemRepo.findByPublisherTenantIdOrderByUpdatedAtDesc(tenant.getId())) {
            list.add(view(item));
        }
        return list;
    }

    public Map<String, Object> view(HubItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("type", item.getType());
        m.put("title", item.getTitle());
        m.put("summary", item.getSummary());
        m.put("category", item.getCategory());
        m.put("tags", parseArray(item.getTags()));
        m.put("coverUrl", item.getCoverUrl());
        m.put("publisherTenantId", item.getPublisherTenantId());
        m.put("publisherTenantName", item.getPublisherTenantName());
        m.put("publisherUserName", item.getPublisherUserName());
        m.put("visibility", item.getVisibility());
        m.put("reviewStatus", item.getReviewStatus());
        m.put("reviewComment", item.getReviewComment());
        m.put("currentVersionNo", currentVersionNo(item));
        m.put("latestVersionNo", item.getLatestVersionNo());
        m.put("installCount", item.getInstallCount());
        m.put("createdAt", item.getCreatedAt());
        m.put("updatedAt", item.getUpdatedAt());
        return m;
    }

    public Integer currentVersionNo(HubItem item) {
        if (item.getCurrentVersionId() == null) {
            return null;
        }
        return versionRepo.findById(item.getCurrentVersionId()).map(HubItemVersion::getVersionNo).orElse(null);
    }

    private HubItem requireVisible(HubTenant tenant, Long itemId) {
        HubItem item = itemRepo.findById(itemId).orElseThrow(() -> new BusinessException(404, "条目不存在"));
        boolean approved = item.getCurrentVersionId() != null && !HubItem.ST_OFFLINE.equals(item.getReviewStatus());
        boolean visible = HubItem.VIS_PUBLIC.equals(item.getVisibility())
                || grantRepo.existsByItemIdAndTenantId(itemId, tenant.getId())
                || item.getPublisherTenantId().equals(tenant.getId());
        if (!approved || !visible) {
            throw new BusinessException(404, "条目不存在或不可见");
        }
        return item;
    }

    private Map<Long, Integer> installedVersions(Long tenantId) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (HubInstall in : installRepo.findByTenantIdOrderByInstalledAtDesc(tenantId)) {
            if (seen.add(in.getItemId())) {
                versionRepo.findById(in.getVersionId()).ifPresent(v -> result.put(in.getItemId(), v.getVersionNo()));
            }
        }
        return result;
    }

    private List<String> parseArray(String json) {
        List<String> list = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(json == null ? "[]" : json);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    list.add(n.asText());
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? "" : n.asText("").trim();
    }

    private static String cut(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
