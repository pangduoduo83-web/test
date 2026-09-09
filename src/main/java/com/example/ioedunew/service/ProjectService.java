package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Discussion;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Favorite;
import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.DiscussionRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.FavoriteRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目服务:项目中心的浏览、报名、收藏、进度更新。
 * 报名副作用:经验值 +10、生成通知、刷新项目统计;
 * 进度是学生自报的学习位置,到 100 只提醒提交成果,项目"完成"由成果评审(SubmissionService)判定。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DiscussionRepository discussionRepository;
    private final WeChatService weChatService;
    private final ProjectStatsService statsService;
    private final LearningActivityService activityService;

    public ProjectService(ProjectRepository projectRepository,
                          EnrollmentRepository enrollmentRepository,
                          FavoriteRepository favoriteRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          DiscussionRepository discussionRepository,
                          WeChatService weChatService,
                          ProjectStatsService statsService,
                          LearningActivityService activityService) {
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.discussionRepository = discussionRepository;
        this.weChatService = weChatService;
        this.statsService = statsService;
        this.activityService = activityService;
    }

    public List<Project> list(String keyword, String difficulty, String sort) {
        List<Project> items = projectRepository.findByStatus("PUBLISHED").stream()
                .filter(p -> {
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return true;
                    }
                    String kw = keyword.trim().toLowerCase();
                    return contains(p.getTitle(), kw) || contains(p.getSummary(), kw)
                            || contains(p.getTags(), kw) || contains(p.getAuthor(), kw);
                })
                .filter(p -> difficulty == null || difficulty.isEmpty() || "全部".equals(difficulty)
                        || difficulty.equals(p.getDifficulty()))
                .collect(Collectors.toList());

        Comparator<Project> cmp;
        String s = sort == null ? "popular" : sort;
        switch (s) {
            case "newest":
                cmp = Comparator.comparing(Project::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case "favorites":
                cmp = Comparator.comparing(Project::getFavoriteCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Project::getViews, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case "popular":
            default:
                // 热门 = 参与人数优先,其次浏览量;这两个都是真实累计值
                cmp = Comparator.comparing(Project::getEnrolledCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Project::getViews, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        items.sort(cmp);
        return items;
    }

    /**
     * 项目详情:附带当前用户的报名/收藏状态,并累加浏览量。
     */
    @Transactional
    public Map<String, Object> detail(Long projectId, Long userId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        p.setViews(p.getViews() + 1);
        projectRepository.save(p);

        Map<String, Object> result = new HashMap<>();
        result.put("project", p);
        // userId 为空表示游客浏览,个人状态一律按未报名/未收藏返回
        Enrollment enrollment = userId == null ? null
                : enrollmentRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);
        result.put("enrolled", enrollment != null);
        result.put("enrollment", enrollment);
        result.put("favorited", userId != null
                && favoriteRepository.findByUserIdAndProjectId(userId, projectId).isPresent());
        return result;
    }

    @Transactional
    public Enrollment enroll(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        if (enrollmentRepository.findByUserIdAndProjectId(userId, projectId).isPresent()) {
            throw new BusinessException("你已报名该项目");
        }
        Enrollment e = new Enrollment();
        e.setUserId(userId);
        e.setProjectId(projectId);
        e.setProjectTitle(p.getTitle());
        e.setCurrentTask("阅读项目简介与前置知识");
        e.setDeadline(LocalDate.now().plusWeeks(parseWeeks(p.getDuration())));
        enrollmentRepository.save(e);

        statsService.refresh(projectId);
        addExp(userId, 10);
        activityService.record(userId, LearningActivity.ENROLL, projectId, "报名《" + p.getTitle() + "》");
        notificationService.create(userId, "project", "报名成功",
                "你已报名《" + p.getTitle() + "》,预计周期 " + p.getDuration() + ",加油!");
        return e;
    }

    /**
     * 收藏/取消收藏切换,返回最新收藏状态。
     */
    @Transactional
    public boolean toggleFavorite(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        Favorite existing = favoriteRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);
        boolean favorited;
        if (existing != null) {
            favoriteRepository.delete(existing);
            favorited = false;
        } else {
            Favorite f = new Favorite();
            f.setUserId(userId);
            f.setProjectId(projectId);
            favoriteRepository.save(f);
            favorited = true;
        }
        favoriteRepository.flush();
        statsService.refresh(p.getId());
        return favorited;
    }

    /**
     * 学生自报学习进度(0~100)。进度只是学习位置,不会把项目判定为完成:
     * 到 100 时提醒提交成果,评审通过(SubmissionService)才算完成并发放经验。
     */
    @Transactional
    public Enrollment updateProgress(Long userId, Long projectId, int progress, String currentTask) {
        return updateProgress(userId, projectId, progress, currentTask, null);
    }

    /**
     * 带大纲阶段的进度更新:completedPhases 为已完成阶段序号(从 1 开始)。
     * 项目有教学大纲且传了阶段时,进度按"完成阶段数 / 总阶段数"计算,当前任务默认为下一个未完成阶段的标题。
     */
    @Transactional
    public Enrollment updateProgress(Long userId, Long projectId, int progress, String currentTask, List<Integer> completedPhases) {
        Enrollment e = enrollmentRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(() -> new BusinessException("尚未报名该项目"));
        if ("COMPLETED".equals(e.getStatus())) {
            throw new BusinessException("该项目已通过评审完成,进度不再变动");
        }
        int before = e.getProgress() == null ? 0 : e.getProgress();
        int clamped = Math.max(0, Math.min(100, progress));
        String nextTask = currentTask == null ? "" : currentTask.trim();
        if (completedPhases != null) {
            List<String> titles = syllabusTitles(projectId);
            if (titles.isEmpty()) {
                throw new BusinessException("该项目没有教学大纲,请直接填写进度百分比");
            }
            java.util.TreeSet<Integer> done = new java.util.TreeSet<>();
            for (Integer n : completedPhases) {
                if (n != null && n >= 1 && n <= titles.size()) {
                    done.add(n);
                }
            }
            e.setCompletedPhases(done.toString());
            clamped = (int) Math.round(done.size() * 100.0 / titles.size());
            if (nextTask.isEmpty()) {
                for (int i = 1; i <= titles.size(); i++) {
                    if (!done.contains(i)) {
                        nextTask = "第 " + i + " 阶段:" + titles.get(i - 1);
                        break;
                    }
                }
            }
        }
        e.setProgress(clamped);
        if (!nextTask.isEmpty()) {
            e.setCurrentTask(cut(nextTask, 100));
        } else if (clamped >= 100) {
            e.setCurrentTask("提交项目成果,等待评审");
        }
        enrollmentRepository.save(e);
        activityService.record(userId, LearningActivity.PROGRESS, projectId,
                "《" + e.getProjectTitle() + "》进度 " + before + "% → " + clamped + "%");
        if (clamped >= 100 && before < 100) {
            notificationService.create(userId, "project", "进度已到 100%",
                    "《" + e.getProjectTitle() + "》的学习进度已推进到 100%,请到项目页「项目成果」提交成果,评审通过后项目才算完成并获得经验值。");
        }
        return e;
    }

    /**
     * 项目讨论列表:主题帖按时间倒序,回复挂在 replies 下按时间正序。
     */
    public List<Map<String, Object>> discussions(Long projectId) {
        List<Discussion> all = discussionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<Map<String, Object>> topics = new java.util.ArrayList<>();
        for (Discussion d : all) {
            if (d.getParentId() != null) {
                continue;
            }
            Map<String, Object> topic = new HashMap<>();
            topic.put("item", d);
            List<Discussion> replies = all.stream()
                    .filter(r -> d.getId().equals(r.getParentId()))
                    .sorted(Comparator.comparing(Discussion::getCreatedAt))
                    .collect(Collectors.toList());
            topic.put("replies", replies);
            topics.add(topic);
        }
        return topics;
    }

    @Transactional
    public Discussion postDiscussion(Long userId, Long projectId, String content, Long parentId, String wxCode) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("讨论内容不能为空");
        }
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        if (parentId != null && !discussionRepository.existsById(parentId)) {
            throw new BusinessException(404, "回复的主题不存在");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        // 小程序端带 wx.login code:走微信 UGC 内容安全检测(scene=2 评论)
        if (wxCode != null && !wxCode.trim().isEmpty()) {
            String openid = weChatService.codeToOpenid(wxCode);
            if (!weChatService.contentSafe(openid, content.trim(), 2)) {
                throw new BusinessException("内容含违规信息,请修改后重新发送");
            }
        }
        Discussion d = new Discussion();
        d.setProjectId(projectId);
        d.setUserId(userId);
        d.setUserName(user.getName());
        d.setParentId(parentId);
        d.setContent(content.trim());
        Discussion saved = discussionRepository.save(d);
        activityService.record(userId, LearningActivity.DISCUSS, projectId, parentId == null ? "发起项目讨论" : "回复项目讨论");
        return saved;
    }

    /** 举报讨论:通知全体管理员处理(小程序 UGC 审核要求提供举报途径) */
    public void reportDiscussion(Long userId, Long projectId, Long discussionId, String reason) {
        Discussion d = discussionRepository.findById(discussionId)
                .filter(x -> projectId.equals(x.getProjectId()))
                .orElseThrow(() -> new BusinessException(404, "讨论不存在"));
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        String excerpt = d.getContent().length() > 40 ? d.getContent().substring(0, 40) + "…" : d.getContent();
        String detail = reporter.getName() + " 举报了「" + d.getUserName() + "」的讨论(#" + d.getId() + "):" + excerpt
                + (reason == null || reason.trim().isEmpty() ? "" : ";理由:" + reason.trim())
                + "。请前往讨论管理处理。";
        for (User admin : userRepository.findByRole("ADMIN")) {
            notificationService.create(admin.getId(), "system", "收到讨论内容举报", detail);
        }
    }

    /** 项目教学大纲各阶段标题(phase + title),没有大纲返回空列表 */
    public List<String> syllabusTitles(Long projectId) {
        Project p = projectRepository.findById(projectId).orElse(null);
        List<String> titles = new java.util.ArrayList<>();
        if (p == null || p.getSyllabus() == null || p.getSyllabus().trim().isEmpty()) {
            return titles;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(p.getSyllabus());
            if (arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                    String phase = n.path("phase").asText("").trim();
                    String title = n.path("title").asText("").trim();
                    titles.add((phase.isEmpty() ? "" : phase + " ") + (title.isEmpty() ? "未命名阶段" : title));
                }
            }
        } catch (Exception ignored) {
        }
        return titles;
    }

    private static String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }

    private void addExp(Long userId, int delta) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setExp(u.getExp() + delta);
            userRepository.save(u);
        });
    }

    private long parseWeeks(String duration) {
        if (duration == null) {
            return 2;
        }
        String digits = duration.replaceAll("\\D", "");
        return digits.isEmpty() ? 2 : Long.parseLong(digits);
    }

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }
}
