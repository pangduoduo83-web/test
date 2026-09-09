package com.example.ioedunew.service;

import com.example.ioedunew.ai.skill.AiUsageDaily;
import com.example.ioedunew.ai.skill.AiUsageDailyRepository;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.LearningActivityRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据大屏快照:一次把用户、项目进度分布、成果评审、设备、AI、学生榜、项目榜、提醒与 7 天趋势算好返回。
 * 全部走当前租户的仓库,天然按站点隔离;大屏每 30 秒拉一次,数据量在千级以内直接内存聚合即可。
 */
@Service
public class BigScreenService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final BorrowRequestRepository borrowRepository;
    private final EquipmentRepository equipmentRepository;
    private final LearningActivityRepository activityRepository;
    private final AiUsageDailyRepository aiUsageRepository;
    private final SiteConfigService siteConfigService;
    private final KicadAiClient kicadAiClient;
    private final com.example.ioedunew.repository.CourseClassRepository classRepository;
    private final com.example.ioedunew.repository.ClassMemberRepository memberRepository;
    private final com.example.ioedunew.repository.SkillScoreRepository skillScoreRepository;

    public BigScreenService(UserRepository userRepository, ProjectRepository projectRepository,
                            EnrollmentRepository enrollmentRepository, SubmissionRepository submissionRepository,
                            BorrowRequestRepository borrowRepository, EquipmentRepository equipmentRepository,
                            LearningActivityRepository activityRepository, AiUsageDailyRepository aiUsageRepository,
                            SiteConfigService siteConfigService, KicadAiClient kicadAiClient,
                            com.example.ioedunew.repository.CourseClassRepository classRepository,
                            com.example.ioedunew.repository.ClassMemberRepository memberRepository,
                            com.example.ioedunew.repository.SkillScoreRepository skillScoreRepository) {
        this.classRepository = classRepository;
        this.memberRepository = memberRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.kicadAiClient = kicadAiClient;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.borrowRepository = borrowRepository;
        this.equipmentRepository = equipmentRepository;
        this.activityRepository = activityRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.siteConfigService = siteConfigService;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> screenCfg = siteConfigService.screenConfig();
        Map<String, Object> out = Boolean.TRUE.equals(screenCfg.get("demo")) ? demoSnapshot() : realSnapshot();
        Map<String, Object> pub = siteConfigService.publicConfig();
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("title", pub.get("title"));
        site.put("logoUrl", pub.get("logoUrl"));
        site.put("screenTitle", screenCfg.get("title"));
        site.put("screenSubtitle", screenCfg.get("subtitle"));
        out.put("site", site);
        out.put("demo", Boolean.TRUE.equals(screenCfg.get("demo")));
        out.put("kpi", kpi(screenCfg, out));
        return out;
    }

    /** 学期 KPI:目标 vs 实际,目标为 0 表示未设置 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> kpi(Map<String, Object> cfg, Map<String, Object> snap) {
        Map<String, Object> users = (Map<String, Object>) snap.get("users");
        Map<String, Object> projects = (Map<String, Object>) snap.get("projects");
        Map<String, Object> equipment = (Map<String, Object>) snap.get("equipment");
        long students = ((Number) users.get("students")).longValue();
        long active7 = ((Number) users.get("active7d")).longValue();
        int activeRate = students == 0 ? 0 : (int) Math.round(active7 * 100.0 / students);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(kpiItem("参与学生", students, ((Number) cfg.get("targetStudents")).intValue(), "人"));
        list.add(kpiItem("完成项目", ((Number) projects.get("completed")).longValue(), ((Number) cfg.get("targetCompleted")).intValue(), "个"));
        list.add(kpiItem("设备利用率", ((Number) equipment.get("utilization")).longValue(), ((Number) cfg.get("targetUtilization")).intValue(), "%"));
        list.add(kpiItem("周活跃率", activeRate, ((Number) cfg.get("targetActiveRate")).intValue(), "%"));
        return list;
    }

    private static Map<String, Object> kpiItem(String name, long actual, int target, String unit) {
        int pct = target <= 0 ? 0 : (int) Math.min(100, Math.round(actual * 100.0 / target));
        return map("name", name, "actual", actual, "target", target, "unit", unit, "percent", pct);
    }

    private Map<String, Object> realSnapshot() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<User> users = userRepository.findAll();
        List<Project> projects = projectRepository.findAll();
        List<Enrollment> enrollments = enrollmentRepository.findAll();
        List<Submission> submissions = submissionRepository.findAllByOrderBySubmittedAtDesc();
        List<BorrowRequest> borrows = borrowRepository.findAllByOrderByAppliedAtDesc();
        List<Equipment> equipment = equipmentRepository.findAll();
        List<LearningActivity> recent14 = activityRepository.findByCreatedAtAfterOrderByCreatedAtDesc(now.minusDays(14));

        Map<Long, User> userById = users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, Project> projectById = projects.stream().collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("siteTitle", siteConfigService.publicConfig().get("title"));
        out.put("generatedAt", now);

        // ---------- 用户 ----------
        long students = users.stream().filter(u -> "STUDENT".equals(u.getRole())).count();
        long teachers = users.stream().filter(u -> "TEACHER".equals(u.getRole())).count();
        Set<Long> activeToday = new HashSet<>();
        Set<Long> active7 = new HashSet<>();
        for (LearningActivity a : recent14) {
            if (!a.getCreatedAt().isBefore(now.minusDays(7))) {
                active7.add(a.getUserId());
            }
            if (a.getCreatedAt().toLocalDate().equals(today)) {
                activeToday.add(a.getUserId());
            }
        }
        long newWeek = users.stream().filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().isBefore(now.minusDays(7))).count();
        out.put("users", map("total", users.size(), "students", students, "teachers", teachers,
                "activeToday", activeToday.size(), "active7d", active7.size(), "newThisWeek", newWeek));

        // ---------- 项目与进度分布 ----------
        List<Enrollment> ongoing = enrollments.stream().filter(e -> !"COMPLETED".equals(e.getStatus())).collect(Collectors.toList());
        long completed = enrollments.size() - ongoing.size();
        int[] buckets = new int[5]; // 未开始 / 0-25 / 25-50 / 50-75 / 75-100
        for (Enrollment e : ongoing) {
            int p = e.getProgress() == null ? 0 : e.getProgress();
            buckets[p <= 0 ? 0 : p < 25 ? 1 : p < 50 ? 2 : p < 75 ? 3 : 4]++;
        }
        long overdue = ongoing.stream().filter(e -> e.getDeadline() != null && e.getDeadline().isBefore(today)).count();
        long published = projects.stream().filter(p -> "PUBLISHED".equals(p.getStatus())).count();
        out.put("projects", map("total", projects.size(), "published", published, "enrollments", enrollments.size(),
                "ongoing", ongoing.size(), "completed", completed,
                "completionRate", enrollments.isEmpty() ? 0 : Math.round(completed * 100.0 / enrollments.size()),
                "overdue", overdue,
                "buckets", map("notStarted", buckets[0], "p0_25", buckets[1], "p25_50", buckets[2], "p50_75", buckets[3], "p75_100", buckets[4])));

        // ---------- 成果评审 ----------
        long pending = submissions.stream().filter(s -> "SUBMITTED".equals(s.getStatus())).count();
        long returned = submissions.stream().filter(s -> "RETURNED".equals(s.getStatus())).count();
        List<Submission> graded = submissions.stream().filter(s -> "GRADED".equals(s.getStatus()) && s.getScore() != null).collect(Collectors.toList());
        double avg = graded.isEmpty() ? 0 : graded.stream().mapToInt(Submission::getScore).average().orElse(0);
        long failing = graded.stream().filter(s -> s.getScore() < 60).count();
        long excellent = graded.stream().filter(s -> s.getScore() >= 85).count();
        out.put("grades", map("submitted", submissions.size(), "pending", pending, "returned", returned, "graded", graded.size(),
                "avgScore", Math.round(avg), "failing", failing, "excellent", excellent,
                "passRate", graded.isEmpty() ? 0 : Math.round((graded.size() - failing) * 100.0 / graded.size())));

        // ---------- 设备 ----------
        int totalUnits = equipment.stream().mapToInt(q -> q.getTotalCount() == null ? 0 : q.getTotalCount()).sum();
        int availableUnits = equipment.stream().mapToInt(q -> q.getAvailableCount() == null ? 0 : q.getAvailableCount()).sum();
        long borrowPending = borrows.stream().filter(b -> "PENDING".equals(b.getStatus())).count();
        long borrowing = borrows.stream().filter(b -> "APPROVED".equals(b.getStatus()) || "RETURN_REQUESTED".equals(b.getStatus())).count();
        long borrowOverdue = borrows.stream().filter(b -> ("APPROVED".equals(b.getStatus()) || "RETURN_REQUESTED".equals(b.getStatus()))
                && b.getStartDate() != null && b.getStartDate().plusDays(b.getDurationDays() == null ? 14 : b.getDurationDays()).isBefore(today)).count();
        long outOfStock = equipment.stream().filter(q -> q.getAvailableCount() != null && q.getAvailableCount() <= 0).count();
        out.put("equipment", map("kinds", equipment.size(), "totalUnits", totalUnits, "availableUnits", availableUnits,
                "utilization", totalUnits == 0 ? 0 : Math.round((totalUnits - availableUnits) * 100.0 / totalUnits),
                "borrowing", borrowing, "pending", borrowPending, "overdue", borrowOverdue, "outOfStock", outOfStock));

        // ---------- AI ----------
        long aiRunsToday = 0, aiRunsMonth = 0, aiTokensMonth = 0;
        for (AiUsageDaily d : aiUsageRepository.findByDayBetween(today.withDayOfMonth(1), today)) {
            aiRunsMonth += d.getRuns();
            aiTokensMonth += d.getPromptTokens() + d.getCompletionTokens();
            if (today.equals(d.getDay())) {
                aiRunsToday += d.getRuns();
            }
        }
        out.put("ai", map("runsToday", aiRunsToday, "runsMonth", aiRunsMonth, "tokensMonth", aiTokensMonth));
        // KiCad AI 设计助手(独立服务,按站点隔离);未部署或不可达为 null
        out.put("kicad", kicadAiClient.tenantStats());

        // ---------- 特别提醒 ----------
        List<Map<String, Object>> alerts = new ArrayList<>();
        long inactive14 = ongoing.stream().map(Enrollment::getUserId).distinct()
                .filter(uid -> recent14.stream().noneMatch(a -> a.getUserId().equals(uid))).count();
        alert(alerts, overdue, "danger", overdue + " 个报名已过截止仍未完成");
        alert(alerts, pending, "warning", pending + " 份成果等待评审");
        alert(alerts, returned, "info", returned + " 份成果被退回,等待学生修改");
        alert(alerts, borrowPending, "warning", borrowPending + " 条借阅申请待审批");
        alert(alerts, borrowOverdue, "danger", borrowOverdue + " 笔借用已逾期未归还");
        alert(alerts, outOfStock, "info", outOfStock + " 种设备已全部借出");
        alert(alerts, inactive14, "warning", inactive14 + " 名在学学生 14 天没有学习动作");
        out.put("alerts", alerts);

        // ---------- 学生榜 ----------
        Map<Long, List<Enrollment>> enrollByUser = enrollments.stream().collect(Collectors.groupingBy(Enrollment::getUserId));
        Map<Long, List<Submission>> subsByUser = submissions.stream().collect(Collectors.groupingBy(Submission::getUserId));
        Map<Long, Integer> actsThisWeek = new HashMap<>();
        Map<Long, Integer> actsLastWeek = new HashMap<>();
        Map<Long, Set<LocalDate>> activeDays = new HashMap<>();
        Map<Long, LocalDateTime> lastActive = new HashMap<>();
        for (LearningActivity a : recent14) {
            boolean thisWeek = !a.getCreatedAt().isBefore(now.minusDays(7));
            (thisWeek ? actsThisWeek : actsLastWeek).merge(a.getUserId(), 1, Integer::sum);
            if (thisWeek) {
                activeDays.computeIfAbsent(a.getUserId(), k -> new HashSet<>()).add(a.getCreatedAt().toLocalDate());
            }
            lastActive.merge(a.getUserId(), a.getCreatedAt(), (x, y) -> x.isAfter(y) ? x : y);
        }
        List<Map<String, Object>> studentCards = new ArrayList<>();
        for (User u : users) {
            if (!"STUDENT".equals(u.getRole())) {
                continue;
            }
            List<Enrollment> mine = enrollByUser.getOrDefault(u.getId(), new ArrayList<>());
            if (mine.isEmpty()) {
                continue; // 没报名的学生不进榜,避免一堆 0
            }
            int progressSum = 0;
            int done = 0;
            for (Enrollment e : mine) {
                if ("COMPLETED".equals(e.getStatus())) {
                    done++;
                    progressSum += 100;
                } else {
                    progressSum += e.getProgress() == null ? 0 : e.getProgress();
                }
            }
            List<Submission> mySubs = subsByUser.getOrDefault(u.getId(), new ArrayList<>()).stream()
                    .filter(s -> "GRADED".equals(s.getStatus()) && s.getScore() != null).collect(Collectors.toList());
            Integer score = mySubs.isEmpty() ? null : (int) Math.round(mySubs.stream().mapToInt(Submission::getScore).average().orElse(0));
            int tw = actsThisWeek.getOrDefault(u.getId(), 0);
            int lw = actsLastWeek.getOrDefault(u.getId(), 0);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("userId", u.getId());
            c.put("name", u.getName());
            c.put("studentNo", u.getStudentNo());
            c.put("major", u.getMajor());
            c.put("projects", mine.size());
            c.put("completed", done);
            c.put("progress", Math.round(progressSum * 1.0 / mine.size()));
            c.put("score", score);
            c.put("actions7d", tw);
            c.put("activeDays7d", activeDays.getOrDefault(u.getId(), new HashSet<>()).size());
            c.put("trend", tw > lw ? "up" : tw < lw ? "down" : "flat");
            c.put("lastActiveAt", lastActive.get(u.getId()));
            c.put("overdue", mine.stream().anyMatch(e -> !"COMPLETED".equals(e.getStatus()) && e.getDeadline() != null && e.getDeadline().isBefore(today)));
            studentCards.add(c);
        }
        studentCards.sort((a, b) -> {
            int c = Long.compare((long) b.get("progress"), (long) a.get("progress"));
            if (c != 0) {
                return c;
            }
            int sa = a.get("score") == null ? -1 : (int) a.get("score");
            int sb = b.get("score") == null ? -1 : (int) b.get("score");
            return Integer.compare(sb, sa);
        });
        int rank = 1;
        for (Map<String, Object> c : studentCards) {
            c.put("rank", rank++);
        }
        out.put("students", studentCards);

        // ---------- 项目榜 ----------
        Map<Long, List<Enrollment>> enrollByProject = enrollments.stream().collect(Collectors.groupingBy(Enrollment::getProjectId));
        List<Map<String, Object>> projectCards = new ArrayList<>();
        for (Project p : projects) {
            List<Enrollment> es = enrollByProject.getOrDefault(p.getId(), new ArrayList<>());
            if (es.isEmpty() && !"PUBLISHED".equals(p.getStatus())) {
                continue;
            }
            long done = es.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            int avgP = es.isEmpty() ? 0 : (int) Math.round(es.stream().mapToInt(e -> "COMPLETED".equals(e.getStatus()) ? 100 : (e.getProgress() == null ? 0 : e.getProgress())).average().orElse(0));
            long pend = submissions.stream().filter(s -> s.getProjectId().equals(p.getId()) && "SUBMITTED".equals(s.getStatus())).count();
            long over = es.stream().filter(e -> !"COMPLETED".equals(e.getStatus()) && e.getDeadline() != null && e.getDeadline().isBefore(today)).count();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("projectId", p.getId());
            c.put("title", p.getTitle());
            c.put("mentor", p.getMentor());
            c.put("difficulty", p.getDifficulty());
            c.put("enrolled", es.size());
            c.put("completed", done);
            c.put("avgProgress", avgP);
            c.put("completionRate", es.isEmpty() ? 0 : Math.round(done * 100.0 / es.size()));
            c.put("pendingSubmissions", pend);
            c.put("overdue", over);
            projectCards.add(c);
        }
        projectCards.sort((a, b) -> Integer.compare((int) b.get("enrolled"), (int) a.get("enrolled")));
        rank = 1;
        for (Map<String, Object> c : projectCards) {
            c.put("rank", rank++);
        }
        out.put("projectCards", projectCards);

        // ---------- 7 天趋势 ----------
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long all = recent14.stream().filter(a -> a.getCreatedAt().toLocalDate().equals(d)).count();
            long tasks = recent14.stream().filter(a -> a.getCreatedAt().toLocalDate().equals(d)
                    && (LearningActivity.PROGRESS.equals(a.getType()) || LearningActivity.SUBMIT.equals(a.getType()) || LearningActivity.GRADED.equals(a.getType()))).count();
            long activeUsers = recent14.stream().filter(a -> a.getCreatedAt().toLocalDate().equals(d)).map(LearningActivity::getUserId).distinct().count();
            trend.add(map("date", d.toString(), "label", d.getMonthValue() + "/" + d.getDayOfMonth(), "actions", all, "tasks", tasks, "activeUsers", activeUsers));
        }
        out.put("trend", trend);

        // ---------- 最近动态(跑马灯) ----------
        List<Map<String, Object>> feed = new ArrayList<>();
        for (LearningActivity a : activityRepository.findTop30ByOrderByCreatedAtDesc()) {
            User u = userById.get(a.getUserId());
            feed.add(map("time", a.getCreatedAt(), "user", u == null ? "已注销用户" : u.getName(), "type", a.getType(), "title", a.getTitle()));
        }
        out.put("feed", feed);

        // ---------- 累计与同比 ----------
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime twoWeeksAgo = now.minusDays(14);
        long actsThis = recent14.stream().filter(a -> !a.getCreatedAt().isBefore(weekAgo)).count();
        long actsLast = recent14.size() - actsThis;
        long activeLast = recent14.stream().filter(a -> a.getCreatedAt().isBefore(weekAgo)).map(LearningActivity::getUserId).distinct().count();
        long completedThis = recent14.stream().filter(a -> LearningActivity.GRADED.equals(a.getType()) && !a.getCreatedAt().isBefore(weekAgo)).count();
        long completedLast = recent14.stream().filter(a -> LearningActivity.GRADED.equals(a.getType()) && a.getCreatedAt().isBefore(weekAgo)).count();
        long newUsersLast = users.stream().filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isBefore(weekAgo) && !u.getCreatedAt().isBefore(twoWeeksAgo)).count();
        LocalDateTime since = users.stream().map(User::getCreatedAt).filter(d -> d != null).min(LocalDateTime::compareTo).orElse(now);
        out.put("cumulative", map(
                "students", students,
                "completed", completed,
                "actions", activityRepository.count(),
                "submissions", submissions.size(),
                "runningDays", Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(since.toLocalDate(), today) + 1),
                "aiRunsMonth", aiRunsMonth));
        out.put("wow", map(
                "actions", wow(actsThis, actsLast),
                "activeUsers", wow(active7.size(), activeLast),
                "completed", wow(completedThis, completedLast),
                "newUsers", wow(newWeek, newUsersLast)));

        // ---------- 成果墙:评分最高的成果 ----------
        List<Map<String, Object>> works = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        graded.stream().sorted((a, b) -> Integer.compare(b.getScore(), a.getScore())).forEach(s -> {
            String key = s.getUserId() + ":" + s.getProjectId();
            if (works.size() >= 18 || !seen.add(key)) {
                return;
            }
            Project p = projectById.get(s.getProjectId());
            User u = userById.get(s.getUserId());
            works.add(map("studentName", u == null ? s.getUserName() : u.getName(), "major", u == null ? null : u.getMajor(),
                    "projectTitle", p == null ? s.getProjectTitle() : p.getTitle(), "coverUrl", p == null ? null : p.getCoverUrl(),
                    "icon", p == null ? null : p.getIcon(), "score", s.getScore(), "feedback", s.getFeedback(),
                    "assessmentName", s.getAssessmentName(), "gradedAt", s.getGradedAt(), "mentor", p == null ? null : p.getMentor()));
        });
        out.put("works", works);

        // ---------- 班级榜 ----------
        List<Map<String, Object>> classRanks = new ArrayList<>();
        for (com.example.ioedunew.entity.CourseClass c : classRepository.findAll()) {
            List<Long> memberIds = memberRepository.findByClassIdOrderByJoinedAtAsc(c.getId()).stream()
                    .map(com.example.ioedunew.entity.ClassMember::getUserId).collect(Collectors.toList());
            if (memberIds.isEmpty()) {
                continue;
            }
            List<Enrollment> es = enrollments.stream().filter(e -> memberIds.contains(e.getUserId())).collect(Collectors.toList());
            long done = es.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            int avgP = es.isEmpty() ? 0 : (int) Math.round(es.stream().mapToInt(e -> "COMPLETED".equals(e.getStatus()) ? 100 : (e.getProgress() == null ? 0 : e.getProgress())).average().orElse(0));
            long activeMembers = memberIds.stream().filter(active7::contains).count();
            classRanks.add(map("name", c.getName(), "teacher", c.getTeacherName(), "members", memberIds.size(),
                    "avgProgress", avgP, "completionRate", es.isEmpty() ? 0 : Math.round(done * 100.0 / es.size()),
                    "activeRate", Math.round(activeMembers * 100.0 / memberIds.size())));
        }
        classRanks.sort((a, b) -> Long.compare(((Number) b.get("avgProgress")).longValue(), ((Number) a.get("avgProgress")).longValue()));
        out.put("classes", classRanks);

        // ---------- 专业分布 ----------
        Map<String, Long> majorCount = users.stream().filter(u -> "STUDENT".equals(u.getRole()))
                .collect(Collectors.groupingBy(u -> u.getMajor() == null || u.getMajor().trim().isEmpty() ? "未填写" : u.getMajor().trim(), Collectors.counting()));
        List<Map<String, Object>> majors = majorCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> map("name", e.getKey(), "value", e.getValue())).collect(Collectors.toList());
        out.put("majors", majors);

        // ---------- 全校技能雷达 ----------
        Map<String, List<Integer>> bySkill = new java.util.LinkedHashMap<>();
        for (com.example.ioedunew.entity.SkillScore s : skillScoreRepository.findAll()) {
            if (s.getScore() != null) {
                bySkill.computeIfAbsent(s.getSkillName(), k -> new ArrayList<>()).add(s.getScore());
            }
        }
        List<Map<String, Object>> skills = new ArrayList<>();
        bySkill.forEach((name, scores) -> skills.add(map("name", name,
                "avg", (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)),
                "max", scores.stream().mapToInt(Integer::intValue).max().orElse(0), "count", scores.size())));
        out.put("skills", skills);

        // ---------- 24 小时活跃分布(近 7 天) ----------
        int[] hours = new int[24];
        for (LearningActivity a : recent14) {
            if (!a.getCreatedAt().isBefore(weekAgo)) {
                hours[a.getCreatedAt().getHour()]++;
            }
        }
        List<Integer> hourList = new ArrayList<>();
        for (int h : hours) {
            hourList.add(h);
        }
        out.put("hours", hourList);

        // ---------- 设备排行与资产 ----------
        List<Map<String, Object>> equipmentTop = equipment.stream()
                .sorted((a, b) -> Integer.compare(b.getBorrowCount() == null ? 0 : b.getBorrowCount(), a.getBorrowCount() == null ? 0 : a.getBorrowCount()))
                .limit(10)
                .map(q -> map("name", q.getName(), "borrowCount", q.getBorrowCount() == null ? 0 : q.getBorrowCount(),
                        "total", q.getTotalCount() == null ? 0 : q.getTotalCount(), "available", q.getAvailableCount() == null ? 0 : q.getAvailableCount(),
                        "category", q.getCategory()))
                .collect(Collectors.toList());
        double assetValue = equipment.stream().mapToDouble(q -> (q.getPrice() == null ? 0 : q.getPrice()) * (q.getTotalCount() == null ? 0 : q.getTotalCount())).sum();
        out.put("equipmentTop", equipmentTop);
        out.put("equipmentAssets", map("value", Math.round(assetValue), "kinds", equipment.size(), "units", totalUnits,
                "borrowsTotal", borrows.size(), "borrowsMonth", borrows.stream().filter(b -> b.getAppliedAt() != null && !b.getAppliedAt().isBefore(now.minusDays(30))).count()));

        // ---------- 教师带教榜 ----------
        Map<Long, List<Project>> byMentor = projects.stream().filter(p -> p.getMentorId() != null).collect(Collectors.groupingBy(Project::getMentorId));
        List<Map<String, Object>> teachersRank = new ArrayList<>();
        byMentor.forEach((mentorId, ps) -> {
            User t = userById.get(mentorId);
            Set<Long> pids = ps.stream().map(Project::getId).collect(Collectors.toSet());
            List<Enrollment> es = enrollments.stream().filter(e -> pids.contains(e.getProjectId())).collect(Collectors.toList());
            List<Submission> gs = graded.stream().filter(s -> pids.contains(s.getProjectId())).collect(Collectors.toList());
            teachersRank.add(map("name", t == null ? ps.get(0).getMentor() : t.getName(), "projects", ps.size(),
                    "students", es.stream().map(Enrollment::getUserId).distinct().count(),
                    "completed", es.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count(),
                    "graded", gs.size(),
                    "avgScore", gs.isEmpty() ? null : (int) Math.round(gs.stream().mapToInt(Submission::getScore).average().orElse(0)),
                    "pending", submissions.stream().filter(s -> pids.contains(s.getProjectId()) && "SUBMITTED".equals(s.getStatus())).count()));
        });
        teachersRank.sort((a, b) -> Long.compare(((Number) b.get("students")).longValue(), ((Number) a.get("students")).longValue()));
        out.put("teachers", teachersRank);
        return out;
    }

    // ====================================================================
    // 演示数据:新站点还没有学生时给领导看的一整屏。固定随机种子,每次刷新数字稍有波动但整体稳定。
    // ====================================================================
    private static final String[] DEMO_SURNAMES = {"陈", "李", "王", "张", "刘", "杨", "黄", "周", "吴", "赵", "林", "郑", "何", "许", "孙", "罗", "高", "梁", "宋", "唐"};
    private static final String[] DEMO_GIVEN = {"梓涵", "子墨", "浩然", "欣怡", "宇轩", "诗涵", "俊杰", "雨桐", "博文", "可欣", "嘉豪", "思远", "语嫣", "皓轩", "若曦", "明哲", "佳琪", "泽宇", "婉婷", "逸飞", "安琪", "睦晨", "书瑶", "启航"};
    private static final String[] DEMO_MAJORS = {"电子信息工程", "自动化", "物联网工程", "通信工程", "计算机科学与技术", "机械电子工程"};
    private static final String[][] DEMO_PROJECTS = {
            {"基于 STM32 的智能温湿度监测节点", "🌡️"}, {"四足机器狗 LinkDog 二代", "🐕"}, {"桌面机械台灯(会听会看)", "💡"},
            {"FPGA 数字信号处理实验板", "📡"}, {"低功耗 LoRa 农业传感网", "🌾"}, {"智能小车循迹与避障", "🚗"},
            {"USB PD 快充电源模块", "🔋"}, {"语音识别智能音箱", "🔊"}, {"六轴姿态传感手环", "⌚"}, {"太阳能 MPPT 充电控制器", "☀️"}
    };
    private static final String[] DEMO_EQUIPMENT = {"数字示波器 DS1054Z", "恒温焊台 T12", "逻辑分析仪", "可编程直流电源", "STM32 开发板", "万用表 UT61E", "热风枪返修台", "频谱分析仪", "3D 打印机", "信号发生器"};
    private static final String[] DEMO_FEEDBACK = {"电路设计规范,焊接工艺优秀,文档完整", "功能全部实现,PCB 布局合理,建议优化电源滤波", "创新点突出,演示流畅,报告结构清晰", "调试记录详实,问题定位准确", "团队协作好,成果超出预期"};

    private Map<String, Object> demoSnapshot() {
        java.util.Random rnd = new java.util.Random(LocalDate.now().toEpochDay());
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("siteTitle", siteConfigService.publicConfig().get("title"));
        out.put("generatedAt", now);
        int students = 486 + rnd.nextInt(20);
        int teachers = 18;
        int activeToday = 120 + rnd.nextInt(40);
        int active7 = 310 + rnd.nextInt(40);
        out.put("users", map("total", students + teachers + 3, "students", students, "teachers", teachers, "activeToday", activeToday, "active7d", active7, "newThisWeek", 12 + rnd.nextInt(8)));
        int enrollments = 1260 + rnd.nextInt(60);
        int completed = 342 + rnd.nextInt(10);
        out.put("projects", map("total", 46, "published", 41, "enrollments", enrollments, "ongoing", enrollments - completed, "completed", completed,
                "completionRate", Math.round(completed * 100.0 / enrollments), "overdue", 23,
                "buckets", map("notStarted", 96, "p0_25", 214, "p25_50", 268, "p50_75", 201, "p75_100", 139)));
        out.put("grades", map("submitted", 398, "pending", 17, "returned", 6, "graded", 375, "avgScore", 82, "failing", 9, "excellent", 168, "passRate", 98));
        out.put("equipment", map("kinds", 36, "totalUnits", 412, "availableUnits", 245, "utilization", 41, "borrowing", 158, "pending", 7, "overdue", 3, "outOfStock", 2));
        out.put("ai", map("runsToday", 86 + rnd.nextInt(30), "runsMonth", 2140, "tokensMonth", 3_860_000L));
        out.put("kicad", map("users", 64, "activeUsers24h", 21, "projects", 133, "conversations", 412, "conversationsActive24h", 38, "messages", 3120, "toolCalls", 5680,
                "tokens", 41_000_000L, "online", 6, "activeRuns", 2, "agentReady", true, "toolCount", 130, "model", "custom:deepseek-chat"));
        List<Map<String, Object>> alerts = new ArrayList<>();
        alerts.add(map("level", "danger", "text", "23 个报名已过截止仍未完成", "count", 23));
        alerts.add(map("level", "warning", "text", "17 份成果等待评审", "count", 17));
        alerts.add(map("level", "warning", "text", "7 条借阅申请待审批", "count", 7));
        alerts.add(map("level", "danger", "text", "3 笔借用已逾期未归还", "count", 3));
        out.put("alerts", alerts);

        List<Map<String, Object>> studentCards = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int progress = Math.max(8, Math.min(100, 95 - i * 2 + rnd.nextInt(6)));
            int score = progress > 60 ? 70 + rnd.nextInt(28) : 55 + rnd.nextInt(30);
            String trend = rnd.nextInt(10) < 6 ? "up" : rnd.nextInt(2) == 0 ? "down" : "flat";
            studentCards.add(map("userId", 1000 + i, "rank", i + 1, "name", demoName(rnd, i), "studentNo", "2023" + (1000 + i),
                    "major", DEMO_MAJORS[i % DEMO_MAJORS.length], "projects", 1 + rnd.nextInt(3), "completed", progress >= 100 ? 1 : 0,
                    "progress", progress, "score", score, "actions7d", 4 + rnd.nextInt(30), "activeDays7d", 1 + rnd.nextInt(6), "trend", trend,
                    "lastActiveAt", now.minusHours(rnd.nextInt(72)), "overdue", i % 11 == 7));
        }
        out.put("students", studentCards);
        List<Map<String, Object>> projectCards = new ArrayList<>();
        for (int i = 0; i < DEMO_PROJECTS.length; i++) {
            int enrolled = 180 - i * 14 + rnd.nextInt(10);
            int done = (int) (enrolled * (0.18 + rnd.nextDouble() * 0.3));
            projectCards.add(map("projectId", 100 + i, "rank", i + 1, "title", DEMO_PROJECTS[i][0], "mentor", demoName(rnd, 200 + i) + "老师",
                    "difficulty", i % 3 == 0 ? "挑战" : i % 3 == 1 ? "进阶" : "入门", "enrolled", enrolled, "completed", done,
                    "avgProgress", 35 + rnd.nextInt(45), "completionRate", Math.round(done * 100.0 / enrolled), "pendingSubmissions", rnd.nextInt(5), "overdue", rnd.nextInt(4)));
        }
        out.put("projectCards", projectCards);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            int acts = weekend ? 60 + rnd.nextInt(60) : 260 + rnd.nextInt(160);
            trend.add(map("date", d.toString(), "label", d.getMonthValue() + "/" + d.getDayOfMonth(), "actions", acts, "tasks", acts / 3 + rnd.nextInt(20), "activeUsers", weekend ? 30 + rnd.nextInt(30) : 110 + rnd.nextInt(50)));
        }
        out.put("trend", trend);
        String[] feedTitles = {"《%s》进度 25%% → 50%%", "提交《%s》成果", "与 AI「项目导师」对话", "借用 %s", "《%s》第 3 阶段完成", "《%s》成果评分 88 分"};
        List<Map<String, Object>> feed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String tpl = feedTitles[rnd.nextInt(feedTitles.length)];
            String title = tpl.contains("借用") ? String.format(tpl, DEMO_EQUIPMENT[rnd.nextInt(DEMO_EQUIPMENT.length)]) : String.format(tpl, DEMO_PROJECTS[rnd.nextInt(DEMO_PROJECTS.length)][0]);
            feed.add(map("time", now.minusMinutes(i * 7 + rnd.nextInt(6)), "user", demoName(rnd, 300 + i), "type", "PROGRESS", "title", title));
        }
        out.put("feed", feed);

        out.put("cumulative", map("students", 1286, "completed", completed, "actions", 583_000 + rnd.nextInt(1000), "submissions", 398, "runningDays", 216, "aiRunsMonth", 2140));
        out.put("wow", map("actions", wow(1860, 1655), "activeUsers", wow(active7, 288), "completed", wow(21, 16), "newUsers", wow(14, 19)));

        List<Map<String, Object>> works = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String[] p = DEMO_PROJECTS[i % DEMO_PROJECTS.length];
            works.add(map("studentName", demoName(rnd, 400 + i), "major", DEMO_MAJORS[i % DEMO_MAJORS.length], "projectTitle", p[0], "coverUrl", null,
                    "icon", p[1], "score", 98 - i * 2, "feedback", DEMO_FEEDBACK[i % DEMO_FEEDBACK.length], "assessmentName", i % 2 == 0 ? "项目答辩" : "功能实现",
                    "gradedAt", now.minusDays(i * 2), "mentor", demoName(rnd, 500 + i) + "老师"));
        }
        out.put("works", works);

        List<Map<String, Object>> classes = new ArrayList<>();
        String[] classNames = {"电信 2301", "自动化 2302", "物联网 2301", "通信 2303", "计科 2304", "机电 2301", "电信 2302", "物联网 2302"};
        for (int i = 0; i < classNames.length; i++) {
            classes.add(map("name", classNames[i], "teacher", demoName(rnd, 600 + i) + "老师", "members", 38 + rnd.nextInt(10),
                    "avgProgress", 82 - i * 5 + rnd.nextInt(4), "completionRate", 46 - i * 4 + rnd.nextInt(5), "activeRate", 88 - i * 6 + rnd.nextInt(6)));
        }
        out.put("classes", classes);
        List<Map<String, Object>> majors = new ArrayList<>();
        int[] majorCounts = {142, 96, 88, 71, 55, 34};
        for (int i = 0; i < DEMO_MAJORS.length; i++) {
            majors.add(map("name", DEMO_MAJORS[i], "value", majorCounts[i]));
        }
        out.put("majors", majors);
        String[] skillNames = {"嵌入式开发", "编程能力", "通信技术", "PCB设计", "信号处理", "硬件调试"};
        int[] skillAvg = {68, 74, 57, 63, 52, 66};
        List<Map<String, Object>> skills = new ArrayList<>();
        for (int i = 0; i < skillNames.length; i++) {
            skills.add(map("name", skillNames[i], "avg", skillAvg[i] + rnd.nextInt(3), "max", 92 + rnd.nextInt(6), "count", students));
        }
        out.put("skills", skills);
        List<Integer> hours = new ArrayList<>();
        int[] shape = {2, 1, 0, 0, 0, 1, 4, 18, 62, 110, 128, 96, 40, 72, 118, 134, 121, 88, 74, 102, 118, 86, 41, 12};
        for (int h : shape) {
            hours.add(h + rnd.nextInt(8));
        }
        out.put("hours", hours);
        List<Map<String, Object>> equipmentTop = new ArrayList<>();
        for (int i = 0; i < DEMO_EQUIPMENT.length; i++) {
            int total = 6 + rnd.nextInt(20);
            equipmentTop.add(map("name", DEMO_EQUIPMENT[i], "borrowCount", 260 - i * 22 + rnd.nextInt(10), "total", total, "available", rnd.nextInt(total + 1), "category", i < 4 ? "测试仪表" : "工具"));
        }
        out.put("equipmentTop", equipmentTop);
        out.put("equipmentAssets", map("value", 1_268_000L, "kinds", 36, "units", 412, "borrowsTotal", 3480, "borrowsMonth", 286));
        List<Map<String, Object>> teachersRank = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            teachersRank.add(map("name", demoName(rnd, 700 + i) + "老师", "projects", 6 - i / 2, "students", 96 - i * 9 + rnd.nextInt(5), "completed", 31 - i * 3,
                    "graded", 58 - i * 5, "avgScore", 80 + rnd.nextInt(8), "pending", rnd.nextInt(4)));
        }
        out.put("teachers", teachersRank);
        return out;
    }

    private static String demoName(java.util.Random rnd, int seed) {
        java.util.Random r = new java.util.Random(seed * 31L + 7);
        return DEMO_SURNAMES[r.nextInt(DEMO_SURNAMES.length)] + DEMO_GIVEN[r.nextInt(DEMO_GIVEN.length)];
    }

    /** 同比:本周 vs 上周,返回 {now, prev, pct};上周为 0 时 pct 为 null */
    private static Map<String, Object> wow(long cur, long prev) {
        return map("now", cur, "prev", prev, "pct", prev == 0 ? null : Math.round((cur - prev) * 100.0 / prev));
    }

    private static void alert(List<Map<String, Object>> alerts, long count, String level, String text) {
        if (count > 0) {
            alerts.add(map("level", level, "text", text, "count", count));
        }
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
