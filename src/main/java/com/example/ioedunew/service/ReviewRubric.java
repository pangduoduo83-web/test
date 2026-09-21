package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Project;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Rubric weights describe this submission, never the stage's share of the course grade. */
public final class ReviewRubric {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ReviewRubric() {}
    public static ArrayNode validate(String raw) {
        try {
            JsonNode node = JSON.readTree(raw == null || raw.trim().isEmpty() ? "[]" : raw);
            if (!node.isArray() || node.size() > 12) throw new BusinessException("评分细则应为数组，最多12项");
            int total = 0; Set<String> names = new HashSet<>();
            for (JsonNode item : node) {
                String name = item.path("name").asText("").trim();
                if (name.isEmpty() || name.length() > 80 || !names.add(name)) throw new BusinessException("评分项名称不能为空或重复，最多80字");
                JsonNode points = item.path("points");
                if (!points.isIntegralNumber() || points.asInt() < 1 || points.asInt() > 100) throw new BusinessException("每个评分项分值须为1至100的整数");
                if (item.path("description").asText("").length() > 1000) throw new BusinessException("评分项说明最多1000字");
                total += points.asInt();
            }
            if (node.size() > 0 && total != 100) throw new BusinessException("评分细则分值合计必须为100分");
            return (ArrayNode) node;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("评分细则格式错误"); }
    }
    public static ArrayNode forSubmission(Project project, String assessment) {
        try {
            if (project != null && assessment != null) {
                for (JsonNode a : JSON.readTree(project.getAssessments() == null ? "[]" : project.getAssessments())) {
                    if (assessment.equals(a.path("name").asText()) && a.path("rubric").size() > 0) return validate(a.path("rubric").toString());
                }
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("考核项格式错误"); }
        ArrayNode rubric = validate(project == null ? null : project.getReviewRubric());
        if (rubric.size() == 0) {
            String[] names = {"任务完成度", "技术实现与问题解决", "成果表达", "反思与总结"};
            int[] points = {40,30,15,15};
            for (int i=0;i<4;i++) rubric.addObject().put("name", names[i]).put("points", points[i]);
        }
        return rubric;
    }
}
