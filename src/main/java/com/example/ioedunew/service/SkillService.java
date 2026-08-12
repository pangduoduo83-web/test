package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.repository.SkillScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能评估服务:技能维度查询与测评结果写入,并给出规则化学习建议。
 */
@Service
public class SkillService {

    private final SkillScoreRepository skillScoreRepository;
    private final AiPlanService aiPlanService;

    public SkillService(SkillScoreRepository skillScoreRepository, AiPlanService aiPlanService) {
        this.skillScoreRepository = skillScoreRepository;
        this.aiPlanService = aiPlanService;
    }

    public Map<String, Object> summary(Long userId) {
        List<SkillScore> skills = skillScoreRepository.findByUserId(userId);
        int overall = (int) Math.round(skills.stream().mapToInt(SkillScore::getScore).average().orElse(0));

        Map<String, Object> result = new HashMap<>();
        result.put("skills", skills);
        result.put("overall", overall);
        result.put("suggestions", buildSuggestions(skills));
        return result;
    }

    @Transactional
    public Map<String, Object> assess(Long userId, Map<String, Integer> scores) {
        List<SkillScore> existing = skillScoreRepository.findByUserId(userId);
        Map<String, SkillScore> byName = new HashMap<>();
        for (SkillScore s : existing) {
            byName.put(s.getSkillName(), s);
        }
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            Integer v = entry.getValue();
            if (v == null || v < 0 || v > 100) {
                throw new BusinessException("技能分数必须在 0-100 之间:" + entry.getKey());
            }
            SkillScore s = byName.get(entry.getKey());
            if (s == null) {
                s = new SkillScore();
                s.setUserId(userId);
                s.setSkillName(entry.getKey());
            }
            s.setScore(v);
            s.setUpdatedAt(LocalDateTime.now());
            skillScoreRepository.save(s);
        }
        // 技能画像变化后,AI 学习计划缓存立即失效
        aiPlanService.evict(userId);
        return summary(userId);
    }

    /**
     * 规则化建议:短板维度给补强建议,强项维度鼓励挑战高难项目。
     */
    private List<String> buildSuggestions(List<SkillScore> skills) {
        List<String> suggestions = new ArrayList<>();
        if (skills.isEmpty()) {
            suggestions.add("先完成一次能力测评,生成你的专属学习计划");
            return suggestions;
        }
        List<SkillScore> sorted = new ArrayList<>(skills);
        sorted.sort(Comparator.comparingInt(SkillScore::getScore));
        SkillScore weakest = sorted.get(0);
        SkillScore strongest = sorted.get(sorted.size() - 1);
        suggestions.add("重点提升「" + weakest.getSkillName() + "」能力,建议完成2个相关入门项目");
        if (sorted.size() > 1) {
            SkillScore second = sorted.get(1);
            if (second.getScore() < 60) {
                suggestions.add("「" + second.getSkillName() + "」基础薄弱,推荐先修相关基础课程");
            }
        }
        if (strongest.getScore() >= 70) {
            suggestions.add("「" + strongest.getSkillName() + "」能力良好,可挑战高难度项目");
        }
        return suggestions;
    }
}
