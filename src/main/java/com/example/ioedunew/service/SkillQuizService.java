package com.example.ioedunew.service;

import com.example.ioedunew.ai.skill.SkillRunner;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.SkillDimension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 能力测评:用隐藏 SKILL「skill-quiz」出题,答案只留在服务端(30 分钟),
 * 学生提交答案后在这里判分,并作为一次 QUIZ 实证并入技能综合分。
 */
@Service
public class SkillQuizService {

    private static final int QUESTION_COUNT = 6;
    private static final long TTL_MS = 30 * 60_000L;

    private final SkillRunner runner;
    private final SkillService skillService;
    private final SkillDimensionService dimensionService;
    private final ObjectMapper om;
    private final Map<String, Quiz> quizzes = new ConcurrentHashMap<>();

    public SkillQuizService(SkillRunner runner, SkillService skillService, SkillDimensionService dimensionService, ObjectMapper om) {
        this.runner = runner;
        this.skillService = skillService;
        this.dimensionService = dimensionService;
        this.om = om;
    }

    private static class Quiz {
        String id;
        Long userId;
        String skillName;
        List<Integer> answers = new ArrayList<>();
        List<String> explains = new ArrayList<>();
        long createdAt = System.currentTimeMillis();
        boolean submitted;
    }

    /** 出题:返回 quizId 与不含答案的题目 */
    public Map<String, Object> start(AuthUser user, String skillName) {
        SkillDimension dim = dimensionService.listEnabled().stream().filter(d -> d.getName().equals(skillName)).findFirst()
                .orElseThrow(() -> new BusinessException("未知的技能维度:" + skillName));
        Object skills = skillService.summary(user.getId()).get("skills");
        int current = skills instanceof List ? currentScore((List<?>) skills, skillName) : 30;
        ObjectNode input = om.createObjectNode();
        input.put("skill", dim.getName());
        input.put("description", dim.getDescription() == null ? "" : dim.getDescription());
        input.put("score", current);
        input.put("count", QUESTION_COUNT);
        SkillRunner.Request req = new SkillRunner.Request();
        req.skillKey = "skill-quiz";
        req.input = input;
        req.title = "AI 测评 · " + dim.getName();

        final String[] content = new String[1];
        final String[] error = new String[1];
        runner.run(user, req, new SkillRunner.Events() {
            public void delta(String text) {
            }

            public void toolCall(String name, JsonNode args) {
            }

            public void toolResult(String name, boolean ok, String summary) {
            }

            public void confirmRequired(String toolName, JsonNode args, String description) {
            }

            public void done(Map<String, Object> result) {
                content[0] = String.valueOf(result.get("content"));
            }

            public void error(String message) {
                error[0] = message;
            }
        }, false);
        if (error[0] != null) {
            throw new BusinessException(error[0]);
        }
        JsonNode questions = parseQuestions(content[0]);

        Quiz quiz = new Quiz();
        quiz.id = UUID.randomUUID().toString().replace("-", "");
        quiz.userId = user.getId();
        quiz.skillName = dim.getName();
        ArrayNode publicQuestions = om.createArrayNode();
        for (JsonNode q : questions) {
            int answer = q.path("answer").asInt(-1);
            JsonNode options = q.path("options");
            if (!options.isArray() || options.size() < 2 || answer < 0 || answer >= options.size()) {
                continue;
            }
            quiz.answers.add(answer);
            quiz.explains.add(q.path("explain").asText(""));
            ObjectNode pq = publicQuestions.addObject();
            pq.put("q", q.path("q").asText(""));
            pq.set("options", options);
            pq.put("difficulty", q.path("difficulty").asText(""));
        }
        if (publicQuestions.size() < 3) {
            throw new BusinessException("AI 出题不完整,请重试");
        }
        sweep();
        quizzes.put(quiz.id, quiz);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quizId", quiz.id);
        m.put("skillName", quiz.skillName);
        m.put("questions", publicQuestions);
        m.put("currentScore", current);
        return m;
    }

    /** 判分并计入画像;返回每题对错、解析与分数变化 */
    public Map<String, Object> submit(AuthUser user, String quizId, List<Integer> answers) {
        Quiz quiz = quizzes.get(quizId);
        if (quiz == null || !quiz.userId.equals(user.getId())) {
            throw new BusinessException("测评已过期,请重新开始");
        }
        if (quiz.submitted) {
            throw new BusinessException("这份测评已经提交过了");
        }
        quiz.submitted = true;
        int total = quiz.answers.size();
        int correct = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            Integer given = answers != null && i < answers.size() ? answers.get(i) : null;
            boolean ok = given != null && given.equals(quiz.answers.get(i));
            if (ok) {
                correct++;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("index", i);
            d.put("correct", ok);
            d.put("answer", quiz.answers.get(i));
            d.put("given", given);
            d.put("explain", quiz.explains.get(i));
            detail.add(d);
        }
        int score = (int) Math.round(correct * 100.0 / total);
        Map<String, Object> applied = skillService.applyQuizEvidence(user.getId(), quiz.skillName, score, correct, total);
        quizzes.remove(quizId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skillName", quiz.skillName);
        m.put("correct", correct);
        m.put("total", total);
        m.put("score", score);
        m.put("before", applied.get("before"));
        m.put("after", applied.get("after"));
        m.put("detail", detail);
        m.put("summary", skillService.summary(user.getId()));
        return m;
    }

    private JsonNode parseQuestions(String content) {
        if (content == null) {
            throw new BusinessException("AI 没有返回题目");
        }
        String s = content.trim();
        int fence = s.indexOf("```");
        if (fence >= 0) {
            int start = s.indexOf('\n', fence);
            int end = s.lastIndexOf("```");
            if (start > 0 && end > start) {
                s = s.substring(start + 1, end);
            }
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) {
            throw new BusinessException("AI 返回的题目格式不正确,请重试");
        }
        try {
            JsonNode root = om.readTree(s.substring(a, b + 1));
            JsonNode qs = root.path("questions");
            if (!qs.isArray()) {
                throw new BusinessException("AI 返回的题目格式不正确,请重试");
            }
            return qs;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 返回的题目格式不正确,请重试");
        }
    }

    private static int currentScore(List<?> skills, String name) {
        for (Object o : skills) {
            if (o instanceof Map && name.equals(((Map<?, ?>) o).get("skillName"))) {
                Object v = ((Map<?, ?>) o).get("score");
                return v instanceof Number ? ((Number) v).intValue() : 30;
            }
        }
        return 30;
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        quizzes.entrySet().removeIf(e -> now - e.getValue().createdAt > TTL_MS);
    }
}
