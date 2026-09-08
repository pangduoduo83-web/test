package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.service.UploadStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目 ↔ 商店 payload 的转换。
 * 导出只带教学内容字段,剔除租户私有数据(讲师、报名数、浏览量、评分、状态等);
 * 附件搬运用正则直接在 payload 文本上做:发布时把 /uploads/... 上传到商店改写成 /hub-assets/...,
 * 安装时再下载回本租户目录改写成 /uploads/...,因此富文本、封面、资源列表里的引用都能一并处理。
 */
@Component
public class ProjectPackager {

    private static final Logger log = LoggerFactory.getLogger(ProjectPackager.class);
    private static final Pattern LOCAL_UPLOAD = Pattern.compile("/uploads/(\\d{6}/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,10})");
    private static final Pattern HUB_ASSET = Pattern.compile("/hub-assets/([a-f0-9]{64})\\.([a-z0-9]{1,10})");
    private static final String[] TEXT_FIELDS = {"title", "summary", "description", "difficulty", "duration", "teamSize",
            "category", "icon", "coverUrl", "author", "license", "pcbSize"};
    private static final String[] JSON_FIELDS = {"tags", "features", "learningGoals", "prerequisites", "skillRequirements",
            "syllabus", "bom", "resources", "equipmentNames", "assessments"};

    private final ObjectMapper objectMapper;
    private final UploadStorage storage;
    private final HubClient hubClient;

    public ProjectPackager(ObjectMapper objectMapper, UploadStorage storage, HubClient hubClient) {
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.hubClient = hubClient;
    }

    /** 导出为 payload(附件 URL 仍是本地 /uploads/...,由 uploadAssets 改写) */
    public ObjectNode export(Project p) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("schema", "ioedu.project/1");
        n.put("title", p.getTitle());
        n.put("summary", p.getSummary());
        n.put("description", p.getDescription());
        n.put("difficulty", p.getDifficulty());
        n.put("duration", p.getDuration());
        n.put("teamSize", p.getTeamSize());
        n.put("category", p.getCategory());
        n.put("icon", p.getIcon());
        n.put("coverUrl", p.getCoverUrl());
        n.put("author", p.getAuthor());
        n.put("license", p.getLicense());
        n.put("verified", Boolean.TRUE.equals(p.getVerified()));
        if (p.getLayers() != null) {
            n.put("layers", p.getLayers());
        }
        n.put("pcbSize", p.getPcbSize());
        if (p.getCost() != null) {
            n.put("cost", p.getCost());
        }
        n.set("tags", parse(p.getTags()));
        n.set("features", parse(p.getFeatures()));
        n.set("learningGoals", parse(p.getLearningGoals()));
        n.set("prerequisites", parse(p.getPrerequisites()));
        n.set("skillRequirements", parse(p.getSkillRequirements()));
        n.set("syllabus", parse(p.getSyllabus()));
        n.set("bom", parse(p.getBom()));
        n.set("resources", parse(p.getResources()));
        n.set("equipmentNames", parse(p.getEquipmentNames()));
        n.set("assessments", parse(p.getAssessments()));
        return n;
    }

    /** 发布前:把 payload 里引用的本地附件上传到商店并改写地址;返回改写后的 payload */
    public ObjectNode uploadAssets(ObjectNode payload) {
        String text = payload.toString();
        Map<String, String> mapping = new LinkedHashMap<>();
        Matcher m = LOCAL_UPLOAD.matcher(text);
        while (m.find()) {
            String relative = m.group(1);
            if (mapping.containsKey(relative)) {
                continue;
            }
            Path file = storage.resolveForRead(relative);
            if (file == null || !file.toFile().isFile()) {
                log.warn("发布项目:附件 {} 不存在,保留原地址", relative);
                continue;
            }
            mapping.put(relative, hubClient.uploadAsset(file));
        }
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            text = text.replace("/uploads/" + e.getKey(), e.getValue());
        }
        return readObject(text);
    }

    /** 安装时:把 payload 里的商店附件下载到本租户目录并改写为本地地址 */
    public ObjectNode downloadAssets(JsonNode payload) {
        String text = payload.toString();
        Map<String, String> mapping = new LinkedHashMap<>();
        Matcher m = HUB_ASSET.matcher(text);
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        while (m.find()) {
            String assetPath = m.group(0);
            if (mapping.containsKey(assetPath)) {
                continue;
            }
            byte[] bytes = hubClient.downloadAsset(assetPath);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + m.group(2);
            Path dir = storage.tenantRoot().resolve(month);
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve(filename), bytes);
            } catch (IOException e) {
                throw new BusinessException(500, "保存商店附件失败: " + e.getMessage());
            }
            mapping.put(assetPath, "/uploads/" + month + "/" + filename);
        }
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return readObject(text);
    }

    /** payload → 本地项目(不含 id/讲师/统计字段) */
    public Project toProject(JsonNode payload) {
        Project p = new Project();
        apply(payload, p);
        return p;
    }

    /** 用 payload 覆盖已有项目的教学内容,保留讲师与统计数据 */
    public void apply(JsonNode payload, Project p) {
        String title = payload.path("title").asText("").trim();
        if (title.isEmpty()) {
            throw new BusinessException("商店条目缺少项目标题");
        }
        p.setTitle(cut(title, 100));
        p.setSummary(cut(textOrNull(payload, "summary"), 300));
        p.setDescription(textOrNull(payload, "description"));
        p.setDifficulty(cut(payload.path("difficulty").asText("入门"), 10));
        p.setDuration(cut(payload.path("duration").asText("2周"), 20));
        p.setTeamSize(cut(payload.path("teamSize").asText("1人"), 20));
        p.setCategory(cut(textOrNull(payload, "category"), 30));
        p.setIcon(cut(payload.path("icon").asText("🔌"), 10));
        p.setCoverUrl(cut(textOrNull(payload, "coverUrl"), 255));
        p.setAuthor(cut(textOrNull(payload, "author"), 50));
        p.setLicense(cut(payload.path("license").asText("GPL-3.0"), 30));
        p.setVerified(payload.path("verified").asBoolean(false));
        p.setLayers(payload.hasNonNull("layers") ? payload.get("layers").asInt() : null);
        p.setPcbSize(cut(textOrNull(payload, "pcbSize"), 30));
        p.setCost(payload.hasNonNull("cost") ? payload.get("cost").asDouble() : null);
        p.setTags(arrayText(payload, "tags"));
        p.setFeatures(arrayText(payload, "features"));
        p.setLearningGoals(arrayText(payload, "learningGoals"));
        p.setPrerequisites(arrayText(payload, "prerequisites"));
        p.setSkillRequirements(arrayText(payload, "skillRequirements"));
        p.setSyllabus(arrayText(payload, "syllabus"));
        p.setBom(arrayText(payload, "bom"));
        p.setResources(arrayText(payload, "resources"));
        p.setEquipmentNames(arrayText(payload, "equipmentNames"));
        p.setAssessments(arrayText(payload, "assessments"));
    }

    private JsonNode parse(String json) {
        try {
            JsonNode n = objectMapper.readTree(json == null || json.trim().isEmpty() ? "[]" : json);
            return n.isArray() ? n : objectMapper.createArrayNode();
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private ObjectNode readObject(String text) {
        try {
            return (ObjectNode) objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(500, "项目内容序列化失败");
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static String arrayText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && v.isArray() ? v.toString() : "[]";
    }

    private static String cut(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
