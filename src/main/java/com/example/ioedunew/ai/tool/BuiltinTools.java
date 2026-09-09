package com.example.ioedunew.ai.tool;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.BorrowDtos;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.entity.Discussion;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.example.ioedunew.service.BorrowService;
import com.example.ioedunew.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 内置工具:包装现有业务服务,全部以调用者身份执行。
 * 只读工具(查设备/项目/我的借阅/我的技能)所有登录用户可用;borrow.apply 为写操作,需用户确认后才执行。
 */
public final class BuiltinTools {

    private BuiltinTools() {
    }

    static ObjectNode schema(ObjectMapper om, String... propertyDefs) {
        ObjectNode schema = om.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        // propertyDefs 三元组:名称, 类型, 描述;名称以 * 开头表示必填
        for (int i = 0; i + 2 < propertyDefs.length; i += 3) {
            String name = propertyDefs[i];
            boolean req = name.startsWith("*");
            if (req) {
                name = name.substring(1);
                required.add(name);
            }
            ObjectNode p = props.putObject(name);
            p.put("type", propertyDefs[i + 1]);
            p.put("description", propertyDefs[i + 2]);
        }
        if (required.size() == 0) {
            schema.remove("required");
        }
        return schema;
    }

    static String text(JsonNode args, String field) {
        JsonNode n = args == null ? null : args.get(field);
        return n == null || n.isNull() ? "" : n.asText("").trim();
    }

    static String cut(String v, int max) {
        if (v == null) {
            return "";
        }
        String plain = v.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() <= max ? plain : plain.substring(0, max) + "…";
    }

    @Component
    public static class EquipmentSearch implements AiTool {
        private final EquipmentRepository repo;
        private final ObjectMapper om;

        public EquipmentSearch(EquipmentRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "equipment.search";
        }

        public String description() {
            return "搜索实验室设备图书馆:按关键字(名称/型号/描述/标签)和分类查找设备,返回库存与位置。";
        }

        public JsonNode inputSchema() {
            return schema(om, "keyword", "string", "关键字,可为空", "category", "string", "设备分类,可为空",
                    "onlyAvailable", "boolean", "是否只返回当前可借的设备");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            String kw = text(args, "keyword").toLowerCase(Locale.ROOT);
            String cat = text(args, "category");
            boolean onlyAvailable = args != null && args.path("onlyAvailable").asBoolean(false);
            ArrayNode out = om.createArrayNode();
            for (Equipment e : repo.findAll()) {
                if (!cat.isEmpty() && !cat.equals(e.getCategory())) {
                    continue;
                }
                if (onlyAvailable && (!"AVAILABLE".equals(e.getStatus()) || e.getAvailableCount() <= 0)) {
                    continue;
                }
                String hay = (e.getName() + " " + e.getModel() + " " + e.getDescription() + " " + e.getTags()).toLowerCase(Locale.ROOT);
                if (!kw.isEmpty() && !hay.contains(kw)) {
                    continue;
                }
                ObjectNode n = out.addObject();
                n.put("id", e.getId());
                n.put("name", e.getName());
                n.put("model", e.getModel());
                n.put("category", e.getCategory());
                n.put("location", e.getLocation());
                n.put("status", e.getStatus());
                n.put("availableCount", e.getAvailableCount());
                n.put("totalCount", e.getTotalCount());
                if (out.size() >= 20) {
                    break;
                }
            }
            return out;
        }
    }

    @Component
    public static class EquipmentGet implements AiTool {
        private final EquipmentRepository repo;
        private final ObjectMapper om;

        public EquipmentGet(EquipmentRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "equipment.get";
        }

        public String description() {
            return "按设备 id 查看设备详情(描述、规格、参考文档、适用项目)。";
        }

        public JsonNode inputSchema() {
            return schema(om, "*id", "integer", "设备 id");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            Equipment e = repo.findById(args.path("id").asLong()).orElseThrow(() -> new BusinessException(404, "设备不存在"));
            ObjectNode n = om.createObjectNode();
            n.put("id", e.getId());
            n.put("name", e.getName());
            n.put("model", e.getModel());
            n.put("category", e.getCategory());
            n.put("location", e.getLocation());
            n.put("status", e.getStatus());
            n.put("availableCount", e.getAvailableCount());
            n.put("totalCount", e.getTotalCount());
            n.put("manufacturer", e.getManufacturer());
            n.put("description", cut(e.getDescription(), 600));
            n.put("specs", cut(e.getSpecs(), 400));
            n.put("docs", cut(e.getDocs(), 300));
            n.put("suitableProjects", cut(e.getSuitableProjects(), 200));
            return n;
        }
    }

    @Component
    public static class ProjectSearch implements AiTool {
        private final ProjectRepository repo;
        private final ObjectMapper om;

        public ProjectSearch(ProjectRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "project.search";
        }

        public String description() {
            return "搜索已上架的实践项目:按关键字、难度(入门/进阶/挑战)、分类筛选,返回摘要与技能要求。";
        }

        public JsonNode inputSchema() {
            return schema(om, "keyword", "string", "关键字,可为空", "difficulty", "string", "难度:入门/进阶/挑战,可为空",
                    "category", "string", "项目分类,可为空");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            String kw = text(args, "keyword").toLowerCase(Locale.ROOT);
            String diff = text(args, "difficulty");
            String cat = text(args, "category");
            ArrayNode out = om.createArrayNode();
            for (Project p : repo.findByStatus("PUBLISHED")) {
                if (!diff.isEmpty() && !diff.equals(p.getDifficulty())) {
                    continue;
                }
                if (!cat.isEmpty() && !cat.equals(p.getCategory())) {
                    continue;
                }
                String hay = (p.getTitle() + " " + p.getSummary() + " " + p.getTags() + " " + p.getLearningGoals()).toLowerCase(Locale.ROOT);
                if (!kw.isEmpty() && !hay.contains(kw)) {
                    continue;
                }
                ObjectNode n = out.addObject();
                n.put("id", p.getId());
                n.put("title", p.getTitle());
                n.put("summary", cut(p.getSummary(), 120));
                n.put("difficulty", p.getDifficulty());
                n.put("duration", p.getDuration());
                n.put("category", p.getCategory());
                n.put("skillRequirements", cut(p.getSkillRequirements(), 200));
                if (out.size() >= 15) {
                    break;
                }
            }
            return out;
        }
    }

    @Component
    public static class ProjectGet implements AiTool {
        private final ProjectRepository repo;
        private final ObjectMapper om;

        public ProjectGet(ProjectRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "project.get";
        }

        public String description() {
            return "按项目 id 查看项目详情:学习目标、前置要求、教学大纲(分阶段)、成果考核项、技能要求、BOM、所需设备与教学资料。";
        }

        public JsonNode inputSchema() {
            return schema(om, "*id", "integer", "项目 id");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            Project p = repo.findById(args.path("id").asLong()).orElseThrow(() -> new BusinessException(404, "项目不存在"));
            ObjectNode n = om.createObjectNode();
            n.put("id", p.getId());
            n.put("title", p.getTitle());
            n.put("summary", p.getSummary());
            n.put("difficulty", p.getDifficulty());
            n.put("duration", p.getDuration());
            n.put("teamSize", p.getTeamSize());
            n.put("mentor", p.getMentor());
            n.put("learningGoals", cut(p.getLearningGoals(), 400));
            n.put("prerequisites", cut(p.getPrerequisites(), 300));
            n.set("syllabus", parseJson(om, p.getSyllabus()));
            n.set("assessments", parseJson(om, p.getAssessments()));
            n.set("skillRequirements", parseJson(om, p.getSkillRequirements()));
            n.put("bom", cut(p.getBom(), 800));
            n.put("equipmentNames", cut(p.getEquipmentNames(), 200));
            n.set("resources", parseJson(om, p.getResources()));
            n.put("description", cut(p.getDescription(), 1200));
            return n;
        }
    }

    static JsonNode parseJson(ObjectMapper om, String json) {
        try {
            JsonNode n = om.readTree(json == null || json.trim().isEmpty() ? "[]" : json);
            return n.isArray() ? n : om.createArrayNode();
        } catch (Exception e) {
            return om.createArrayNode();
        }
    }

    @Component
    public static class EnrollmentMyList implements AiTool {
        private final EnrollmentRepository repo;
        private final ObjectMapper om;

        public EnrollmentMyList(EnrollmentRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "enrollment.my_list";
        }

        public String description() {
            return "查看当前用户已报名的项目及学习状态:进度百分比、当前任务、截止日期、是否已通过评审完成。";
        }

        public JsonNode inputSchema() {
            return schema(om, "projectId", "integer", "只看某个项目,可为空");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            long only = args == null ? 0 : args.path("projectId").asLong(0);
            ArrayNode out = om.createArrayNode();
            for (Enrollment e : repo.findByUserIdOrderByEnrolledAtDesc(ctx.getUserId())) {
                if (only > 0 && !e.getProjectId().equals(only)) {
                    continue;
                }
                ObjectNode n = out.addObject();
                n.put("projectId", e.getProjectId());
                n.put("projectTitle", e.getProjectTitle());
                n.put("status", e.getStatus());
                n.put("progress", e.getProgress() == null ? 0 : e.getProgress());
                n.put("currentTask", e.getCurrentTask());
                n.put("completedPhases", e.getCompletedPhases() == null ? "[]" : e.getCompletedPhases());
                n.put("deadline", e.getDeadline() == null ? null : e.getDeadline().toString());
                n.put("enrolledAt", e.getEnrolledAt() == null ? null : e.getEnrolledAt().toString());
            }
            return out;
        }
    }

    @Component
    public static class EnrollmentUpdateProgress implements AiTool {
        private final ProjectService projectService;
        private final ObjectMapper om;

        public EnrollmentUpdateProgress(ProjectService projectService, ObjectMapper om) {
            this.projectService = projectService;
            this.om = om;
        }

        public String name() {
            return "enrollment.update_progress";
        }

        public String description() {
            return "更新当前用户在某个已报名项目上的学习进度(需要用户确认)。项目有教学大纲时优先传 completedPhases(已完成阶段序号,从 1 开始),"
                    + "进度会按阶段数自动折算;没有大纲时传 progress 百分比。进度到 100 不等于完成,完成需提交成果并通过评审。";
        }

        public JsonNode inputSchema() {
            ObjectNode s = schema(om, "*projectId", "integer", "项目 id", "progress", "integer", "新的进度百分比 0~100(无大纲时使用)",
                    "currentTask", "string", "接下来要做的任务,一句话,可省略由系统按大纲填");
            ObjectNode phases = ((ObjectNode) s.get("properties")).putObject("completedPhases");
            phases.put("type", "array");
            phases.put("description", "已完成的大纲阶段序号列表,从 1 开始,例如 [1,2] 表示前两个阶段已完成");
            phases.putObject("items").put("type", "integer");
            return s;
        }

        public boolean readOnly() {
            return false;
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            java.util.List<Integer> phases = null;
            if (args.has("completedPhases") && args.get("completedPhases").isArray()) {
                phases = new java.util.ArrayList<>();
                for (JsonNode p : args.get("completedPhases")) {
                    phases.add(p.asInt());
                }
            }
            Enrollment e = projectService.updateProgress(ctx.getUserId(), args.path("projectId").asLong(),
                    args.path("progress").asInt(0), text(args, "currentTask"), phases);
            ObjectNode n = om.createObjectNode();
            n.put("projectTitle", e.getProjectTitle());
            n.put("progress", e.getProgress());
            n.put("currentTask", e.getCurrentTask());
            n.put("status", e.getStatus());
            n.put("message", e.getProgress() >= 100 ? "进度已到 100%,请提醒学生到项目页提交成果等待评审" : "进度已更新");
            return n;
        }
    }

    @Component
    public static class SubmissionMyList implements AiTool {
        private final SubmissionRepository repo;
        private final ObjectMapper om;

        public SubmissionMyList(SubmissionRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "submission.my_list";
        }

        public String description() {
            return "查看当前用户在某个项目提交过的成果及评审结果(考核项、状态、分数、教师评语)。";
        }

        public JsonNode inputSchema() {
            return schema(om, "*projectId", "integer", "项目 id");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            ArrayNode out = om.createArrayNode();
            for (Submission s : repo.findByUserIdAndProjectIdOrderBySubmittedAtDesc(ctx.getUserId(), args.path("projectId").asLong())) {
                ObjectNode n = out.addObject();
                n.put("id", s.getId());
                n.put("assessmentName", s.getAssessmentName() == null ? "整体成果" : s.getAssessmentName());
                n.put("status", s.getStatus());
                n.put("score", s.getScore());
                n.put("feedback", s.getFeedback());
                n.put("content", cut(s.getContent(), 200));
                n.put("submittedAt", s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString());
                if (out.size() >= 10) {
                    break;
                }
            }
            return out;
        }
    }

    @Component
    public static class DiscussionPost implements AiTool {
        private final ProjectService projectService;
        private final ObjectMapper om;

        public DiscussionPost(ProjectService projectService, ObjectMapper om) {
            this.projectService = projectService;
            this.om = om;
        }

        public String name() {
            return "discussion.post";
        }

        public String description() {
            return "以当前用户身份在项目讨论区发一条求助或问题(需要用户确认),指导教师和同学可以看到并回复。";
        }

        public JsonNode inputSchema() {
            return schema(om, "*projectId", "integer", "项目 id", "*content", "string", "要发布的内容,100 字以内为宜");
        }

        public boolean readOnly() {
            return false;
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            Discussion d = projectService.postDiscussion(ctx.getUserId(), args.path("projectId").asLong(),
                    text(args, "content"), null, null);
            ObjectNode n = om.createObjectNode();
            n.put("id", d.getId());
            n.put("message", "已发布到项目讨论区");
            return n;
        }
    }

    @Component
    public static class BorrowMyList implements AiTool {
        private final BorrowRequestRepository repo;
        private final ObjectMapper om;

        public BorrowMyList(BorrowRequestRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "borrow.my_list";
        }

        public String description() {
            return "查看当前用户自己的设备借阅记录与状态(审批中/借用中/已归还等)及到期日。";
        }

        public JsonNode inputSchema() {
            return schema(om, "status", "string", "按状态过滤:PENDING/APPROVED/RETURN_REQUESTED/RETURNED/REJECTED/CANCELLED,可为空");
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            String status = text(args, "status");
            ArrayNode out = om.createArrayNode();
            List<BorrowRequest> list = repo.findByUserIdOrderByAppliedAtDesc(ctx.getUserId());
            for (BorrowRequest b : list) {
                if (!status.isEmpty() && !status.equalsIgnoreCase(b.getStatus())) {
                    continue;
                }
                ObjectNode n = out.addObject();
                n.put("id", b.getId());
                n.put("requestNo", b.getRequestNo());
                n.put("equipmentName", b.getEquipmentName());
                n.put("quantity", b.getQuantity());
                n.put("status", b.getStatus());
                n.put("startDate", b.getStartDate() == null ? null : b.getStartDate().toString());
                n.put("dueDate", b.getStartDate() == null ? null : b.getStartDate().plusDays(b.getDurationDays()).toString());
                if (out.size() >= 20) {
                    break;
                }
            }
            return out;
        }
    }

    @Component
    public static class SkillMyScores implements AiTool {
        private final SkillScoreRepository repo;
        private final ObjectMapper om;

        public SkillMyScores(SkillScoreRepository repo, ObjectMapper om) {
            this.repo = repo;
            this.om = om;
        }

        public String name() {
            return "skill.my_scores";
        }

        public String description() {
            return "查看当前用户的技能画像(各维度分数),用于给出个性化学习建议。";
        }

        public JsonNode inputSchema() {
            return schema(om);
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            ArrayNode out = om.createArrayNode();
            for (SkillScore s : repo.findByUserId(ctx.getUserId())) {
                out.addObject().put("skill", s.getSkillName()).put("score", s.getScore());
            }
            return out;
        }
    }

    @Component
    public static class BorrowApply implements AiTool {
        private final BorrowService borrowService;
        private final ObjectMapper om;

        public BorrowApply(BorrowService borrowService, ObjectMapper om) {
            this.borrowService = borrowService;
            this.om = om;
        }

        public String name() {
            return "borrow.apply";
        }

        public String description() {
            return "以当前用户身份提交一条设备借阅申请(需要用户确认)。提交前请先用 equipment.search 确认设备 id 与库存。";
        }

        public JsonNode inputSchema() {
            return schema(om, "*equipmentId", "integer", "设备 id", "quantity", "integer", "数量,默认 1",
                    "*purpose", "string", "使用目的,如 课程实验/项目开发/竞赛", "projectName", "string", "关联项目名称,可为空",
                    "startDate", "string", "开始日期 yyyy-MM-dd,默认今天", "durationDays", "integer", "借用天数,默认 14");
        }

        public boolean readOnly() {
            return false;
        }

        public JsonNode execute(ToolContext ctx, JsonNode args) {
            BorrowDtos.ApplyRequest req = new BorrowDtos.ApplyRequest();
            req.setEquipmentId(args.path("equipmentId").asLong());
            req.setQuantity(Math.max(1, args.path("quantity").asInt(1)));
            req.setPurpose(text(args, "purpose").isEmpty() ? "课程实验" : text(args, "purpose"));
            req.setProjectName(text(args, "projectName"));
            String start = text(args, "startDate");
            req.setStartDate(start.isEmpty() ? LocalDate.now() : LocalDate.parse(start));
            req.setDurationDays(Math.max(1, Math.min(60, args.path("durationDays").asInt(14))));
            BorrowRequest br = borrowService.apply(ctx.getUserId(), req);
            ObjectNode n = om.createObjectNode();
            n.put("requestNo", br.getRequestNo());
            n.put("status", br.getStatus());
            n.put("equipmentName", br.getEquipmentName());
            n.put("message", "借阅申请已提交,等待管理员审批");
            return n;
        }
    }
}
