package com.example.ioedunew.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 技能综合分更新算法的纯函数测试,不依赖 Spring 上下文与数据库 */
class SkillScoringTest {

    @Test
    void passingGradeMeansReachingProjectRequirement() {
        assertEquals(40, SkillService.demonstratedByRule(40, 60));
        assertEquals(53, SkillService.demonstratedByRule(40, 85));
        assertEquals(30, SkillService.demonstratedByRule(40, 40));
        assertEquals(100, SkillService.demonstratedByRule(95, 100));
        assertEquals(0, SkillService.demonstratedByRule(10, 0));
    }

    @Test
    void blendPrefersRuleWhenBothPresent() {
        assertEquals(53, SkillService.blend(53, null));
        assertEquals(70, SkillService.blend(null, 70));
        assertEquals(60, SkillService.blend(50, 75));
    }

    @Test
    void scoreRisesFastAndFallsSlowly() {
        assertEquals(44, SkillService.nextScore(30, 53, 1.0));
        assertEquals(72, SkillService.nextScore(80, 40, 1.0));
        assertEquals(50, SkillService.nextScore(50, 50, 1.0));
    }

    @Test
    void repeatedEvidenceConvergesToDemonstratedLevel() {
        int score = 30;
        for (int i = 0; i < 8; i++) {
            score = SkillService.nextScore(score, 53, 1.0);
        }
        assertTrue(Math.abs(score - 53) <= 1, "expected convergence near 53 but was " + score);
    }

    @Test
    void assessmentItemWeightScalesTheStep() {
        assertEquals(34, SkillService.nextScore(30, 53, 0.3));
        // 权重下限 0.25,避免小权重考核项完全不产生实证
        assertEquals(SkillService.nextScore(30, 53, 0.25), SkillService.nextScore(30, 53, 0.0));
        assertEquals(SkillService.nextScore(30, 53, 1.0), SkillService.nextScore(30, 53, 5.0));
    }

    @Test
    void resultsStayWithinBounds() {
        assertEquals(100, SkillService.nextScore(100, 100, 1.0));
        assertEquals(0, SkillService.nextScore(0, 0, 1.0));
        assertEquals(100, SkillService.clamp(150));
        assertEquals(0, SkillService.clamp(-5));
    }
}
