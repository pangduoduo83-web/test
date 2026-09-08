package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.SkillDimension;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.SkillScoreEvent;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.repository.SkillScoreEventRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能画像服务。
 * <p>
 * 分数模型:
 * <ul>
 *   <li>selfScore:学生自评,只是冷启动基线与"自我认知"对照线;</li>
 *   <li>score:综合分。没有实证时等于自评(或基线 30);有实证后按评级方式增量更新——
 *       实证水平高于当前分时快速上调(×0.6),低于时缓慢下调(×0.2),多次实证后收敛到真实水平;</li>
 *   <li>单次实证水平:项目技能要求 required + (评分 − 60) / 2(恰好及格即视为达到项目要求),
 *       教师确认的 AI 证据 level 以 0.4 权重与之混合。</li>
 * </ul>
 * 每次综合分改写都会写入 skill_score_events,成长曲线与变动记录由流水驱动。
 */
@Service
public class SkillService {

    static final int BASELINE = 30;
    static final double UP_FACTOR = 0.6;
    static final double DOWN_FACTOR = 0.2;
    static final double RULE_WEIGHT = 0.6;
    static final double AI_WEIGHT = 0.4;
    private static final int HISTORY_POINTS = 12;
    private static final int RECENT_EVENTS = 10;

    private final SkillScoreRepository skillScoreRepository;
    private final SkillScoreEventRepository eventRepository;
    private final SkillDimensionService dimensionService;
    private final AiPlanService aiPlanService;
    private final ObjectMapper objectMapper;

    public SkillService(SkillScoreRepository skillScoreRepository,
                        SkillScoreEventRepository eventRepository,
                        SkillDimensionService dimensionService,
                        AiPlanService aiPlanService,
                        ObjectMapper objectMapper) {
        this.skillScoreRepository = skillScoreRepository;
        this.eventRepository = eventRepository;
        this.dimensionService = dimensionService;
        this.aiPlanService = aiPlanService;
        this.objectMapper = objectMapper;
    }

    // ---------- 查询 ----------

    public Map<String, Object> summary(Long userId) {
        List<SkillDimension> dims = dimensionService.listEnabled();
        List<Map<String, Object>> skills = mergedSkills(userId, dims);

        int overall = overallOf(skills);
        int selfCount = 0;
        int selfSum = 0;
        int evidenceTotal = 0;
        for (Map<String, Object> s : skills) {
            Integer self = (Integer) s.get("selfScore");
            if (self != null) {
                selfCount++;
                selfSum += self;
            }
            evidenceTotal += (Integer) s.get("evidenceCount");
        }
        boolean selfComplete = !skills.isEmpty() && selfCount == skills.size();

        List<SkillScoreEvent> events = eventRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", skills);
        result.put("overall", overall);
        result.put("selfOverall", selfCount == 0 ? null : (int) Math.round((double) selfSum / selfCount));
        result.put("selfComplete", selfComplete);
        result.put("evidenceTotal", evidenceTotal);
        result.put("suggestions", buildSuggestions(skills, evidenceTotal, selfComplete));
        result.put("history", history(events));
        result.put("events", recentEvents(events));
        return result;
    }

    /** 综合评分(启用维度的综合分均值),看板等处复用 */
    public int overall(Long userId) {
        return overallOf(mergedSkills(userId, dimensionService.listEnabled()));
    }

    // ---------- 写入 ----------

    /** 新用户初始化:每个启用维度一条基线分 */
    @Transactional
    public void initUser(Long userId) {
        List<SkillDimension> dims = dimensionService.listEnabled();
        List<SkillScoreEvent> events = new ArrayList<>();
        for (SkillDimension d : dims) {
            if (skillScoreRepository.findByUserIdAndSkillName(userId, d.getName()).isPresent()) {
                continue;
            }
            SkillScore s = new SkillScore();
            s.setUserId(userId);
            s.setSkillName(d.getName());
            s.setScore(BASELINE);
            skillScoreRepository.save(s);
            events.add(event(userId, d.getName(), SkillScoreEvent.SOURCE_INIT, BASELINE, BASELINE, null, "注册基线"));
        }
        saveEvents(userId, dims, events);
    }

    /** 学生自评:只改 selfScore;尚无实证的维度综合分跟随自评 */
    @Transactional
    public Map<String, Object> assess(Long userId, Map<String, Integer> scores) {
        List<SkillDimension> dims = dimensionService.listEnabled();
        Set<String> enabled = new LinkedHashSet<>();
        for (SkillDimension d : dims) {
            enabled.add(d.getName());
        }
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (!enabled.contains(entry.getKey())) {
                throw new BusinessException("未知的技能维度:" + entry.getKey());
            }
            Integer v = entry.getValue();
            if (v == null || v < 0 || v > 100) {
                throw new BusinessException("技能分数必须在 0-100 之间:" + entry.getKey());
            }
        }

        List<SkillScoreEvent> events = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String name = entry.getKey();
            int v = entry.getValue();
            SkillScore s = findOrCreate(userId, name);
            boolean selfChanged = s.getSelfScore() == null || s.getSelfScore() != v;
            int before = s.getScore();
            s.setSelfScore(v);
            if (s.getEvidenceCount() == 0) {
                s.setScore(v);
            }
            s.setUpdatedAt(LocalDateTime.now());
            skillScoreRepository.save(s);
            if (selfChanged) {
                String note = s.getEvidenceCount() == 0
                        ? "自评 " + v
                        : "自评 " + v + "(已有 " + s.getEvidenceCount() + " 次实证,综合分不随自评变化)";
                events.add(event(userId, name, SkillScoreEvent.SOURCE_SELF, before, s.getScore(), null, note));
            }
        }
        saveEvents(userId, dims, events);
        aiPlanService.evict(userId);
        return summary(userId);
    }

    /**
     * 项目评分实证:成果被教师评分后,按项目技能要求(与教师确认的 AI 证据)更新对应维度的综合分。
     *
     * @param weight 本次评分在项目中的权重,整体成果为 1,分阶段考核项为 weight/100
     */
    @Transactional
    public void applyProjectEvidence(Submission submission, Project project,
                                     List<MiscDtos.SkillEvidenceItem> confirmed, double weight) {
        if (submission.getScore() == null) {
            return;
        }
        List<SkillDimension> dims = dimensionService.listEnabled();
        Set<String> enabled = new LinkedHashSet<>();
        for (SkillDimension d : dims) {
            enabled.add(d.getName());
        }

        Map<String, Integer> required = new LinkedHashMap<>();
        if (project != null) {
            for (Map.Entry<String, Integer> e : parseRequirements(project.getSkillRequirements()).entrySet()) {
                if (enabled.contains(e.getKey())) {
                    required.put(e.getKey(), e.getValue());
                }
            }
        }
        Map<String, Integer> aiLevels = new LinkedHashMap<>();
        if (confirmed != null) {
            for (MiscDtos.SkillEvidenceItem item : confirmed) {
                if (item == null || item.getName() == null || item.getLevel() == null) {
                    continue;
                }
                String name = item.getName().trim();
                if (enabled.contains(name)) {
                    aiLevels.put(name, clamp(item.getLevel()));
                }
            }
        }
        Set<String> names = new LinkedHashSet<>(required.keySet());
        names.addAll(aiLevels.keySet());
        if (names.isEmpty()) {
            return;
        }

        Long userId = submission.getUserId();
        int submissionScore = submission.getScore();
        String title = submission.getProjectTitle() == null ? "项目" : submission.getProjectTitle();
        List<SkillScoreEvent> events = new ArrayList<>();
        for (String name : names) {
            Integer ruleDemo = required.containsKey(name)
                    ? demonstratedByRule(required.get(name), submissionScore) : null;
            Integer aiDemo = aiLevels.get(name);
            int demonstrated = blend(ruleDemo, aiDemo);

            SkillScore s = findOrCreate(userId, name);
            int before = s.getScore();
            int after = nextScore(before, demonstrated, weight);
            s.setScore(after);
            s.setEvidenceCount(s.getEvidenceCount() + 1);
            s.setUpdatedAt(LocalDateTime.now());
            skillScoreRepository.save(s);

            StringBuilder note = new StringBuilder("《").append(title).append("》评分 ").append(submissionScore);
            if (submission.getAssessmentName() != null && !submission.getAssessmentName().isEmpty()) {
                note.append(",考核项「").append(submission.getAssessmentName()).append("」");
            }
            if (ruleDemo != null) {
                note.append(",项目要求 ").append(required.get(name));
            }
            if (aiDemo != null) {
                note.append(",AI 证据水平 ").append(aiDemo);
            }
            events.add(event(userId, name, SkillScoreEvent.SOURCE_PROJECT, before, after,
                    submission.getId(), cut(note.toString(), 200)));
        }
        saveEvents(userId, dims, events);
        aiPlanService.evict(userId);
    }

    // ---------- 评分算法(纯函数,便于单测) ----------

    /** 项目要求 required 的成果拿到 submissionScore 分,体现出的维度水平:恰好及格即视为达到项目要求 */
    static int demonstratedByRule(int required, int submissionScore) {
        return clamp((int) Math.round(required + (submissionScore - 60) / 2.0));
    }

    /** 规则实证与 AI 证据同时存在时按 0.6 / 0.4 混合,只有一个时直接采用 */
    static int blend(Integer ruleDemo, Integer aiDemo) {
        if (ruleDemo == null) {
            return aiDemo;
        }
        if (aiDemo == null) {
            return ruleDemo;
        }
        return clamp((int) Math.round(ruleDemo * RULE_WEIGHT + aiDemo * AI_WEIGHT));
    }

    /** 评级式增量更新:高于当前分快速上调,低于当前分缓慢下调;weight 为本次实证的权重(0.25-1) */
    static int nextScore(int current, int demonstrated, double weight) {
        double w = Math.max(0.25, Math.min(1.0, weight));
        double factor = demonstrated >= current ? UP_FACTOR : DOWN_FACTOR;
        double delta = (demonstrated - current) * factor * w;
        return clamp((int) Math.round(current + delta));
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    // ---------- 内部 ----------

    /** 启用维度 × 用户分数合并:缺失的维度按基线 30 虚拟补齐,不落库 */
    private List<Map<String, Object>> mergedSkills(Long userId, List<SkillDimension> dims) {
        Map<String, SkillScore> byName = new HashMap<>();
        for (SkillScore s : skillScoreRepository.findByUserId(userId)) {
            byName.put(s.getSkillName(), s);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (SkillDimension d : dims) {
            SkillScore s = byName.get(d.getName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s == null ? null : s.getId());
            m.put("skillName", d.getName());
            m.put("description", d.getDescription());
            m.put("score", s == null ? BASELINE : s.getScore());
            m.put("selfScore", s == null ? null : s.getSelfScore());
            m.put("evidenceCount", s == null ? 0 : s.getEvidenceCount());
            m.put("updatedAt", s == null ? null : s.getUpdatedAt());
            list.add(m);
        }
        return list;
    }

    private int overallOf(List<Map<String, Object>> skills) {
        if (skills.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (Map<String, Object> s : skills) {
            sum += (Integer) s.get("score");
        }
        return (int) Math.round((double) sum / skills.size());
    }

    private SkillScore findOrCreate(Long userId, String name) {
        return skillScoreRepository.findByUserIdAndSkillName(userId, name).orElseGet(() -> {
            SkillScore s = new SkillScore();
            s.setUserId(userId);
            s.setSkillName(name);
            s.setScore(BASELINE);
            return s;
        });
    }

    private SkillScoreEvent event(Long userId, String skillName, String source,
                                  int before, int after, Long refId, String note) {
        SkillScoreEvent e = new SkillScoreEvent();
        e.setUserId(userId);
        e.setSkillName(skillName);
        e.setSource(source);
        e.setBeforeScore(before);
        e.setAfterScore(after);
        e.setRefId(refId);
        e.setNote(note);
        return e;
    }

    /** 所有分数落库后统一补上综合分快照再保存流水 */
    private void saveEvents(Long userId, List<SkillDimension> dims, List<SkillScoreEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        int overall = overallOf(mergedSkills(userId, dims));
        for (SkillScoreEvent e : events) {
            e.setOverallAfter(overall);
        }
        eventRepository.saveAll(events);
    }

    /** 成长曲线:同一批次(同来源、同关联对象、同一分钟)的流水折叠为一个点,最多保留 12 个点 */
    private List<Map<String, Object>> history(List<SkillScoreEvent> events) {
        List<Map<String, Object>> points = new ArrayList<>();
        String lastKey = null;
        for (SkillScoreEvent e : events) {
            String key = e.getSource() + ":" + e.getRefId() + ":" + e.getCreatedAt().withSecond(0).withNano(0);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", e.getCreatedAt());
            p.put("overall", e.getOverallAfter());
            p.put("source", e.getSource());
            if (key.equals(lastKey)) {
                points.set(points.size() - 1, p);
            } else {
                points.add(p);
                lastKey = key;
            }
        }
        if (points.size() <= HISTORY_POINTS) {
            return points;
        }
        List<Map<String, Object>> thinned = new ArrayList<>();
        for (int i = 0; i < HISTORY_POINTS; i++) {
            int idx = (int) Math.round((double) i * (points.size() - 1) / (HISTORY_POINTS - 1));
            thinned.add(points.get(idx));
        }
        return thinned;
    }

    /** 最近的变动记录(不含注册基线),倒序 */
    private List<Map<String, Object>> recentEvents(List<SkillScoreEvent> events) {
        List<SkillScoreEvent> sorted = new ArrayList<>(events);
        Collections.reverse(sorted);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SkillScoreEvent e : sorted) {
            if (SkillScoreEvent.SOURCE_INIT.equals(e.getSource())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skillName", e.getSkillName());
            m.put("source", e.getSource());
            m.put("beforeScore", e.getBeforeScore());
            m.put("afterScore", e.getAfterScore());
            m.put("note", e.getNote());
            m.put("createdAt", e.getCreatedAt());
            list.add(m);
            if (list.size() >= RECENT_EVENTS) {
                break;
            }
        }
        return list;
    }

    /** 规则化建议:短板补强、强项挑战,并提示自评与实证的差距 */
    private List<String> buildSuggestions(List<Map<String, Object>> skills, int evidenceTotal, boolean selfComplete) {
        List<String> suggestions = new ArrayList<>();
        if (skills.isEmpty()) {
            suggestions.add("管理员尚未配置技能维度");
            return suggestions;
        }
        if (evidenceTotal == 0) {
            suggestions.add(selfComplete
                    ? "当前画像仅基于自评,完成项目并通过教师评审后会自动校准为实证分"
                    : "先完成一次能力自评建立基线,再通过项目实践让画像变得可信");
        }
        List<Map<String, Object>> sorted = new ArrayList<>(skills);
        sorted.sort(Comparator.comparingInt(s -> (Integer) s.get("score")));
        Map<String, Object> weakest = sorted.get(0);
        Map<String, Object> strongest = sorted.get(sorted.size() - 1);
        suggestions.add("重点提升「" + weakest.get("skillName") + "」能力,建议完成2个相关入门项目");
        if (sorted.size() > 1) {
            Map<String, Object> second = sorted.get(1);
            if ((Integer) second.get("score") < 60) {
                suggestions.add("「" + second.get("skillName") + "」基础薄弱,推荐先修相关基础课程");
            }
        }
        if ((Integer) strongest.get("score") >= 70) {
            suggestions.add("「" + strongest.get("skillName") + "」能力良好,可挑战高难度项目");
        }
        for (Map<String, Object> s : skills) {
            Integer self = (Integer) s.get("selfScore");
            int score = (Integer) s.get("score");
            if (self != null && (Integer) s.get("evidenceCount") > 0 && self - score >= 15) {
                suggestions.add("「" + s.get("skillName") + "」自评 " + self + " 高于实证 " + score
                        + ",建议通过更高要求的项目验证自己的判断");
                break;
            }
        }
        return suggestions;
    }

    private Map<String, Integer> parseRequirements(String json) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    String name = n.path("name").asText("").trim();
                    if (!name.isEmpty()) {
                        map.put(name, clamp(n.path("required").asInt(50)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }
}
