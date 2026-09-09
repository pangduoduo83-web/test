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

    public BigScreenService(UserRepository userRepository, ProjectRepository projectRepository,
                            EnrollmentRepository enrollmentRepository, SubmissionRepository submissionRepository,
                            BorrowRequestRepository borrowRepository, EquipmentRepository equipmentRepository,
                            LearningActivityRepository activityRepository, AiUsageDailyRepository aiUsageRepository,
                            SiteConfigService siteConfigService) {
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
        return out;
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
