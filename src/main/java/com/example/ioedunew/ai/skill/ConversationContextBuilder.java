package com.example.ioedunew.ai.skill;

import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话上下文:把"学生正在做哪个项目、做到哪一步、交了什么"渲染成一段事实文本注入系统提示,
 * 让项目导师类 SKILL 不必先猜再查。目前支持 projectId 一种上下文。
 */
@Component
public class ConversationContextBuilder {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final com.example.ioedunew.service.LearningActivityService activityService;
    private final com.example.ioedunew.service.SkillService skillService;
    private final com.example.ioedunew.service.TeacherService teacherService;
    private final com.example.ioedunew.repository.UserRepository userRepository;
    private final ObjectMapper om;

    public ConversationContextBuilder(ProjectRepository projectRepository, EnrollmentRepository enrollmentRepository,
                                      SubmissionRepository submissionRepository,
                                      com.example.ioedunew.service.LearningActivityService activityService,
                                      com.example.ioedunew.service.SkillService skillService,
                                      com.example.ioedunew.service.TeacherService teacherService,
                                      com.example.ioedunew.repository.UserRepository userRepository, ObjectMapper om) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.activityService = activityService;
        this.skillService = skillService;
        this.teacherService = teacherService;
        this.om = om;
    }

    /** 只保留认识的字段,返回可入库的 JSON;无有效内容返回 null */
    public String normalize(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return null;
        }
        ObjectNode n = om.createObjectNode();
        long projectId = raw.path("projectId").asLong(0);
        if (projectId > 0 && projectRepository.existsById(projectId)) {
            n.put("projectId", projectId);
        }
        String scope = raw.path("scope").asText("");
        if ("student".equals(scope) || "teacher".equals(scope)) {
            n.put("scope", scope);
        }
        return n.size() == 0 ? null : n.toString();
    }

    public String render(String contextJson, Long userId) {
        return render(contextJson, userId, true);
    }

    /** toolsAvailable=false 时不写"用 xx 工具查询"之类的提示,避免无工具的 SKILL 把工具名当成让学生做的事 */
    public String render(String contextJson, Long userId, boolean toolsAvailable) {
        if (contextJson == null || contextJson.trim().isEmpty()) {
            return "";
        }
        JsonNode ctx;
        try {
            ctx = om.readTree(contextJson);
        } catch (Exception e) {
            return "";
        }
        StringBuilder all = new StringBuilder();
        String scope = ctx.path("scope").asText("");
        if ("student".equals(scope)) {
            all.append(renderStudentScope(userId));
        } else if ("teacher".equals(scope)) {
            all.append(renderTeacherScope(userId));
        }
        long projectId = ctx.path("projectId").asLong(0);
        if (projectId > 0) {
            String p = renderProject(projectId, userId, toolsAvailable);
            if (!p.isEmpty()) {
                all.append(all.length() > 0 ? "\n\n" : "").append(p);
            }
        }
        return all.toString();
    }

    /** 学生全局画像:在学项目、截止、最近活跃、技能短板——给"今日建议"类 SKILL 用 */
    private String renderStudentScope(Long userId) {
        StringBuilder sb = new StringBuilder("[学生学习状态]");
        List<Enrollment> list = enrollmentRepository.findByUserIdOrderByEnrolledAtDesc(userId);
        int ongoing = 0;
        for (Enrollment e : list) {
            if ("COMPLETED".equals(e.getStatus())) {
                continue;
            }
            ongoing++;
            sb.append("\n- 项目 id ").append(e.getProjectId()).append("《").append(e.getProjectTitle()).append("》进度 ")
                    .append(e.getProgress() == null ? 0 : e.getProgress()).append("%,当前任务:").append(nz(e.getCurrentTask(), "未设置"))
                    .append(",截止 ").append(e.getDeadline() == null ? "未设置" : e.getDeadline());
            if (e.getDeadline() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), e.getDeadline());
                sb.append(days < 0 ? "(已过期 " + (-days) + " 天)" : "(还剩 " + days + " 天)");
            }
            List<Submission> subs = submissionRepository.findByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, e.getProjectId());
            if (!subs.isEmpty()) {
                Submission s = subs.get(0);
                sb.append(",最近成果:").append("GRADED".equals(s.getStatus()) ? "已评 " + s.getScore() + " 分"
                        : "RETURNED".equals(s.getStatus()) ? "被退回修改(" + cut(nz(s.getFeedback(), ""), 60) + ")" : "评审中");
            }
        }
        if (ongoing == 0) {
            sb.append("\n- 当前没有进行中的项目").append(list.isEmpty() ? ",还没有报名过任何项目" : "");
        }
        List<LearningActivity> recent = activityService.recent(userId, 7);
        sb.append("\n最近 7 天学习动作 ").append(recent.size()).append(" 次,活跃 ").append(activityService.activeDays(userId, 7)).append(" 天");
        if (!recent.isEmpty()) {
            sb.append(",最近一次:").append(recent.get(0).getTitle()).append("(").append(recent.get(0).getCreatedAt().toLocalDate()).append(")");
        }
        java.util.Map<String, Object> skills = skillService.summary(userId);
        Object arr = skills.get("skills");
        if (arr instanceof List) {
            sb.append("\n技能画像:");
            int i = 0;
            for (Object o : (List<?>) arr) {
                if (o instanceof java.util.Map) {
                    java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                    sb.append(i++ > 0 ? ";" : "").append(m.get("skillName")).append(" ").append(m.get("score"));
                }
            }
        }
        return sb.toString();
    }

    /** 教师全局画像:名下项目、报名/完成、待评成果、掉队学生——给"AI 周报"类 SKILL 用 */
    private String renderTeacherScope(Long userId) {
        StringBuilder sb = new StringBuilder("[教师教学状态,截至 ").append(java.time.LocalDate.now()).append("]");
        // 管理员看全站项目;教师只看自己指导的
        boolean admin = userRepository.findById(userId).map(u -> "ADMIN".equals(u.getRole())).orElse(false);
        List<Project> mine = new java.util.ArrayList<>();
        for (Project p : projectRepository.findAll()) {
            if (admin || userId.equals(p.getMentorId())) {
                mine.add(p);
            }
        }
        if (mine.isEmpty()) {
            sb.append("\n名下还没有指导的项目。");
            return sb.toString();
        }
        if (admin) {
            sb.append("\n(管理员视角:以下为全站项目)");
        }
        for (Project p : mine) {
            List<Enrollment> es = enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(p.getId());
            long done = es.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            int avg = es.isEmpty() ? 0 : (int) Math.round(es.stream().mapToInt(e -> e.getProgress() == null ? 0 : e.getProgress()).average().orElse(0));
            long pending = submissionRepository.findByStatusOrderBySubmittedAtDesc("SUBMITTED").stream()
                    .filter(s -> s.getProjectId().equals(p.getId())).count();
            sb.append("\n- 《").append(p.getTitle()).append("》(id ").append(p.getId()).append("):报名 ").append(es.size())
                    .append(" 人,完成 ").append(done).append(" 人,平均进度 ").append(avg).append("%,待评成果 ").append(pending).append(" 份");
        }
        List<java.util.Map<String, Object>> risk = teacherService.atRisk(userId, admin);
        if (risk.isEmpty()) {
            sb.append("\n掉队学生:无");
        } else {
            sb.append("\n掉队学生(").append(risk.size()).append(" 人):");
            int i = 0;
            for (java.util.Map<String, Object> r : risk) {
                if (i++ >= 12) {
                    sb.append(" …");
                    break;
                }
                sb.append("\n  · ").append(r.get("studentName")).append(" @《").append(r.get("projectTitle")).append("》进度 ")
                        .append(r.get("progress")).append("%:").append(String.join("、", ((List<?>) r.get("reasons")).stream().map(String::valueOf).toArray(String[]::new)));
            }
        }
        return sb.toString();
    }

    private String renderProject(long projectId, Long userId, boolean toolsHint) {
        Project p = projectRepository.findById(projectId).orElse(null);
        if (p == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[会话上下文] 学生当前正在学习的项目(id ").append(p.getId()).append("):《")
                .append(p.getTitle()).append("》,难度 ").append(nz(p.getDifficulty())).append(",周期 ").append(nz(p.getDuration()))
                .append(",指导教师 ").append(nz(p.getMentor(), "未指派")).append("。");
        if (p.getSummary() != null && !p.getSummary().isEmpty()) {
            sb.append("\n简介:").append(cut(p.getSummary(), 200));
        }
        appendArray(sb, "\n教学大纲:", p.getSyllabus(), 8, n -> n.path("phase").asText("") + " " + n.path("title").asText("")
                + (n.path("hours").asInt(0) > 0 ? "(" + n.path("hours").asInt() + "学时)" : ""));
        appendArray(sb, "\n成果考核项:", p.getAssessments(), 8, n -> n.path("name").asText("") + " 权重" + n.path("weight").asInt(0) + "%");
        appendArray(sb, "\n技能要求:", p.getSkillRequirements(), 8, n -> n.path("name").asText("") + "≥" + n.path("required").asInt(0));
        appendArray(sb, "\n所需设备:", p.getEquipmentNames(), 10, n -> n.asText(""));

        Enrollment e = enrollmentRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);
        if (e == null) {
            sb.append("\n学生尚未报名该项目;如学生想开始,提醒其在项目页点击「立即报名」。");
        } else {
            sb.append("\n学生报名状态:").append("COMPLETED".equals(e.getStatus()) ? "已通过评审完成" : "进行中")
                    .append(",进度 ").append(e.getProgress() == null ? 0 : e.getProgress()).append("%,已完成大纲阶段:")
                    .append(e.getCompletedPhases() == null || e.getCompletedPhases().isEmpty() ? "无" : e.getCompletedPhases())
                    .append(",当前任务:").append(nz(e.getCurrentTask(), "未设置"))
                    .append(",截止 ").append(e.getDeadline() == null ? "未设置" : e.getDeadline()).append("。");
            if (toolsHint) {
                sb.append("更新进度请用 enrollment.update_progress 并传 completedPhases(已完成阶段序号列表)。");
            }
            List<Submission> subs = submissionRepository.findByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId);
            if (subs.isEmpty()) {
                sb.append("\n成果提交:尚未提交任何成果。");
            } else {
                sb.append("\n成果提交(最近 5 条):");
                int i = 0;
                for (Submission s : subs) {
                    if (i++ >= 5) {
                        break;
                    }
                    sb.append("\n- ").append(s.getAssessmentName() == null ? "整体成果" : s.getAssessmentName())
                            .append(" · ").append("GRADED".equals(s.getStatus()) ? "已评 " + s.getScore() + " 分" : "评审中")
                            .append(s.getFeedback() == null || s.getFeedback().isEmpty() ? "" : " · 评语:" + cut(s.getFeedback(), 80));
                }
            }
        }
        if (toolsHint) {
            sb.append("\n如需项目描述、BOM 等更多细节,用 project.get 查询 id ").append(p.getId()).append("。");
        } else {
            appendArray(sb, "\nBOM 主要元件:", p.getBom(), 12, n -> n.path("name").asText("") + (n.path("qty").asInt(0) > 0 ? "×" + n.path("qty").asInt() : ""));
            appendArray(sb, "\n教学资料:", p.getResources(), 8, n -> n.path("type").asText("") + " " + n.path("name").asText(""));
        }
        return sb.toString();
    }

    private interface Fmt {
        String apply(JsonNode n);
    }

    private void appendArray(StringBuilder sb, String label, String json, int limit, Fmt fmt) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode arr = om.readTree(json);
            if (!arr.isArray() || arr.size() == 0) {
                return;
            }
            sb.append(label);
            int i = 0;
            for (JsonNode n : arr) {
                if (i++ >= limit) {
                    sb.append(" …");
                    break;
                }
                sb.append(i > 1 ? ";" : "").append(fmt.apply(n).trim());
            }
        } catch (Exception ignored) {
        }
    }

    private static String nz(String v) {
        return nz(v, "-");
    }

    private static String nz(String v, String def) {
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    private static String cut(String v, int max) {
        String plain = v.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= max ? plain : plain.substring(0, max) + "…";
    }
}
