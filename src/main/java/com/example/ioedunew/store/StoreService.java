package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

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
     * 教师安装的项目自动指派给自己做讲师,进入其教学工作台。
     */
    @Transactional
    public Project install(long itemId, String status, Long overwriteProjectId, AuthUser user, String userName) {
        String actingUser = userName + "(" + user.getRole() + ")";
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
            requireOwnership(project, user);
            packager.apply(payload, project);
        } else {
            project = packager.toProject(payload);
            project.setStatus("DRAFT".equalsIgnoreCase(status) ? "DRAFT" : "PUBLISHED");
            if (user.isTeacher()) {
                project.setMentorId(user.getId());
                project.setMentor(userName);
            }
        }
        project.setHubItemId(itemId);
        project.setHubVersionNo(versionNo);
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    /** 当前用户可发布的本地项目(管理员全部,教师仅自己指导的),附带商店状态 */
    public List<Map<String, Object>> localProjects(AuthUser user) {
        Map<Long, JsonNode> mine = new HashMap<>();
        if (settings.isConfigured()) {
            try {
                for (JsonNode item : hubClient.mine()) {
                    mine.put(item.path("id").asLong(), item);
                }
            } catch (BusinessException e) {
                log.warn("读取本站已发布条目失败: {}", e.getMessage());
            }
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Project p : projectRepository.findAll()) {
            if (!user.isAdmin() && !user.getId().equals(p.getMentorId())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("category", p.getCategory());
            m.put("difficulty", p.getDifficulty());
            m.put("status", p.getStatus());
            m.put("coverUrl", p.getCoverUrl());
            m.put("mentor", p.getMentor());
            m.put("updatedAt", p.getUpdatedAt());
            m.put("hubItemId", p.getHubItemId());
            m.put("hubVersionNo", p.getHubVersionNo());
            JsonNode item = p.getHubItemId() == null ? null : mine.get(p.getHubItemId());
            m.put("publishedByUs", item != null);
            m.put("hubReviewStatus", item == null ? null : item.path("reviewStatus").asText(null));
            m.put("hubCurrentVersionNo", item == null || !item.hasNonNull("currentVersionNo") ? null : item.get("currentVersionNo").asInt());
            m.put("hubLatestVersionNo", item == null ? null : item.path("latestVersionNo").asInt());
            m.put("hubReviewComment", item == null ? null : item.path("reviewComment").asText(null));
            m.put("hubInstallCount", item == null ? null : item.path("installCount").asInt());
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(b.get("updatedAt")).compareTo(String.valueOf(a.get("updatedAt"))));
        return out;
    }

    /** 发布本地项目:导出 → 上传附件 → 提交商店(已来自商店且由本租户发布的条目会追加为新版本) */
    @Transactional
    public JsonNode publish(long projectId, String changelog, AuthUser user, String userName) {
        String actingUser = userName + "(" + user.getRole() + ")";
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        requireOwnership(project, user);
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

    /** 管理员可操作全部项目;教师只能发布/更新自己指导的项目 */
    private void requireOwnership(Project project, AuthUser user) {
        if (user.isAdmin()) {
            return;
        }
        if (project.getMentorId() == null || !project.getMentorId().equals(user.getId())) {
            throw new BusinessException(403, "只能发布或更新自己指导的项目");
        }
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
