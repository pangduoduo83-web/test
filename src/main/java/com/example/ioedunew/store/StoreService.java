package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地系统侧的商店能力:浏览(附带本地安装状态)、安装为本地项目、把本地项目发布到商店。
 * 安装采用复制:本地项目独立存在并记住上游指针(hubItemId/hubVersionNo),商店不可用不影响教学。
 */
@Service
public class StoreService {

    private final HubClient hubClient;
    private final ProjectPackager packager;
    private final ProjectRepository projectRepository;
    private final StoreSettingsService settings;
    private final ObjectMapper objectMapper;

    public StoreService(HubClient hubClient, ProjectPackager packager, ProjectRepository projectRepository,
                        StoreSettingsService settings, ObjectMapper objectMapper) {
        this.hubClient = hubClient;
        this.packager = packager;
        this.projectRepository = projectRepository;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", settings.isConfigured());
        m.put("baseUrl", settings.baseUrl());
        if (!settings.isConfigured()) {
            m.put("connected", false);
            return m;
        }
        try {
            JsonNode me = hubClient.me();
            m.put("connected", true);
            m.put("tenantCode", me.path("code").asText());
            m.put("tenantName", me.path("name").asText());
        } catch (BusinessException e) {
            m.put("connected", false);
            m.put("error", e.getMessage());
        }
        return m;
    }

    /** 商店条目列表 + 本地安装情况(localProjectId / 是否有新版本) */
    public JsonNode items(String q, String category) {
        JsonNode list = hubClient.items(q, category);
        Map<Long, Project> local = localByHubItem();
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode item : list) {
            ObjectNode n = item.deepCopy();
            decorate(n, local.get(item.path("id").asLong()));
            out.add(n);
        }
        return out;
    }

    public JsonNode item(long id) {
        ObjectNode n = hubClient.item(id).deepCopy();
        decorate(n, localByHubItem().get(id));
        return n;
    }

    /**
     * 安装:拉取当前版本 → 下载附件 → 建本地项目(或按 overwriteProjectId 覆盖已安装项目的教学内容)。
     */
    @Transactional
    public Project install(long itemId, String status, Long overwriteProjectId, String actingUser) {
        JsonNode result = hubClient.install(itemId, actingUser);
        JsonNode payload = packager.downloadAssets(result.path("payload"));
        int versionNo = result.path("versionNo").asInt();

        Project project;
        if (overwriteProjectId != null) {
            project = projectRepository.findById(overwriteProjectId)
                    .orElseThrow(() -> new BusinessException(404, "要更新的本地项目不存在"));
            if (project.getHubItemId() == null || project.getHubItemId() != itemId) {
                throw new BusinessException("该本地项目不是从此商店条目安装的,不能覆盖更新");
            }
            packager.apply(payload, project);
        } else {
            project = packager.toProject(payload);
            project.setStatus("DRAFT".equalsIgnoreCase(status) ? "DRAFT" : "PUBLISHED");
        }
        project.setHubItemId(itemId);
        project.setHubVersionNo(versionNo);
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    /** 发布本地项目:导出 → 上传附件 → 提交商店(已来自商店且由本租户发布的条目会追加为新版本) */
    @Transactional
    public JsonNode publish(long projectId, String changelog, String actingUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        ObjectNode payload = packager.uploadAssets(packager.export(project));

        ObjectNode body = objectMapper.createObjectNode();
        if (project.getHubItemId() != null) {
            body.put("itemId", project.getHubItemId());
        }
        body.put("title", project.getTitle());
        body.put("summary", project.getSummary());
        body.put("category", project.getCategory());
        body.set("tags", payload.path("tags"));
        body.put("coverUrl", payload.path("coverUrl").asText(null));
        body.put("changelog", changelog == null ? "" : changelog.trim());
        body.set("payload", payload);

        JsonNode item = hubClient.publish(body, actingUser);
        project.setHubItemId(item.path("id").asLong());
        project.setHubVersionNo(item.path("latestVersionNo").asInt());
        projectRepository.save(project);
        return item;
    }

    public JsonNode mine() {
        JsonNode list = hubClient.mine();
        Map<Long, Project> local = localByHubItem();
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode item : list) {
            ObjectNode n = item.deepCopy();
            decorate(n, local.get(item.path("id").asLong()));
            out.add(n);
        }
        return out;
    }

    private void decorate(ObjectNode n, Project local) {
        if (local == null) {
            n.put("localProjectId", (Long) null);
            n.put("updateAvailable", false);
            return;
        }
        n.put("localProjectId", local.getId());
        n.put("localProjectTitle", local.getTitle());
        n.put("localVersionNo", local.getHubVersionNo());
        Integer current = n.hasNonNull("currentVersionNo") ? n.get("currentVersionNo").asInt() : null;
        n.put("updateAvailable", current != null && local.getHubVersionNo() != null && current > local.getHubVersionNo());
    }

    private Map<Long, Project> localByHubItem() {
        Map<Long, Project> map = new HashMap<>();
        List<Project> all = projectRepository.findAll();
        for (Project p : all) {
            if (p.getHubItemId() != null && !map.containsKey(p.getHubItemId())) {
                map.put(p.getHubItemId(), p);
            }
        }
        return map;
    }
}
