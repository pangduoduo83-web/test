package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Discussion;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Favorite;
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
 * 报名副作用:enrolledCount 自增、经验值 +10、生成通知;
 * 进度到 100 时置为 COMPLETED 并加 50 经验,由本服务统一控制。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DiscussionRepository discussionRepository;

    public ProjectService(ProjectRepository projectRepository,
                          EnrollmentRepository enrollmentRepository,
                          FavoriteRepository favoriteRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          DiscussionRepository discussionRepository) {
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.discussionRepository = discussionRepository;
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
            case "rating":
                cmp = Comparator.comparing(Project::getRating).reversed();
                break;
            case "newest":
                cmp = Comparator.comparing(Project::getUpdatedAt).reversed();
                break;
            case "downloads":
                cmp = Comparator.comparing(Project::getDownloads).reversed();
                break;
            case "popular":
            default:
                cmp = Comparator.comparing(Project::getViews).reversed();
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
        Enrollment enrollment = enrollmentRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);
        result.put("enrolled", enrollment != null);
        result.put("enrollment", enrollment);
        result.put("favorited", favoriteRepository.findByUserIdAndProjectId(userId, projectId).isPresent());
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

        p.setEnrolledCount(p.getEnrolledCount() + 1);
        projectRepository.save(p);
        addExp(userId, 10);
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
        if (existing != null) {
            favoriteRepository.delete(existing);
            p.setFavoriteCount(Math.max(0, p.getFavoriteCount() - 1));
            projectRepository.save(p);
            return false;
        }
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setProjectId(projectId);
        favoriteRepository.save(f);
        p.setFavoriteCount(p.getFavoriteCount() + 1);
        projectRepository.save(p);
        return true;
    }

    @Transactional
    public Enrollment updateProgress(Long userId, Long projectId, int progress, String currentTask) {
        Enrollment e = enrollmentRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(() -> new BusinessException("尚未报名该项目"));
        boolean wasCompleted = "COMPLETED".equals(e.getStatus());
        e.setProgress(progress);
        if (currentTask != null && !currentTask.isEmpty()) {
            e.setCurrentTask(currentTask);
        }
        if (progress >= 100 && !wasCompleted) {
            e.setStatus("COMPLETED");
            addExp(userId, 50);
            notificationService.create(userId, "project", "项目完成",
                    "恭喜完成《" + e.getProjectTitle() + "》,经验值 +50!");
        }
        enrollmentRepository.save(e);
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setWeeklyHours(user.getWeeklyHours() + 2);
            userRepository.save(user);
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
    public Discussion postDiscussion(Long userId, Long projectId, String content, Long parentId) {
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
        Discussion d = new Discussion();
        d.setProjectId(projectId);
        d.setUserId(userId);
        d.setUserName(user.getName());
        d.setParentId(parentId);
        d.setContent(content.trim());
        return discussionRepository.save(d);
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
