package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个人中心服务:汇总统计卡、进行中项目、成就徽章与学习活跃度趋势。
 * 趋势与"本周学习"全部来自 learning_activities 的真实记录;成就为规则计算而非落库。
 */
@Service
public class DashboardService {

    /** 视为"推进类"动作:进度更新、提交成果、获得评分 */
    private static final Set<String> TASK_TYPES = new HashSet<>(Arrays.asList(
            LearningActivity.PROGRESS, LearningActivity.SUBMIT, LearningActivity.GRADED));

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BorrowRequestRepository borrowRepository;
    private final SkillService skillService;
    private final LearningActivityService activityService;

    public DashboardService(UserRepository userRepository,
                            EnrollmentRepository enrollmentRepository,
                            BorrowRequestRepository borrowRepository,
                            SkillService skillService,
                            LearningActivityService activityService) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.borrowRepository = borrowRepository;
        this.skillService = skillService;
        this.activityService = activityService;
    }

    public Map<String, Object> overview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        long enrollCount = enrollmentRepository.countByUserId(userId);
        long completedCount = enrollmentRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long borrowTotal = borrowRepository.countByUserId(userId);

        int skillAvg = skillService.overall(userId);

        List<Map<String, Object>> achievements = buildAchievements(enrollCount, borrowTotal, completedCount);
        long unlocked = achievements.stream().filter(a -> Boolean.TRUE.equals(a.get("unlocked"))).count();

        List<Enrollment> ongoing = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByUserIdOrderByEnrolledAtDesc(userId)) {
            if ("IN_PROGRESS".equals(e.getStatus())) {
                ongoing.add(e);
            }
        }

        List<LearningActivity> week = activityService.recent(userId, 7);

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("level", user.getExp() / 100 + 1);
        result.put("levelProgress", user.getExp() % 100);
        result.put("weeklyActivities", week.size());
        result.put("weeklyActiveDays", activityService.activeDays(userId, 7));
        result.put("achievementCount", unlocked);
        result.put("completedProjects", completedCount);
        result.put("skillAvg", skillAvg);
        result.put("ongoingProjects", ongoing);
        result.put("achievements", achievements);
        result.put("weekTrend", activityService.dailyCounts(userId, 7, null));
        result.put("monthTrend", activityService.dailyCounts(userId, 30, null));
        result.put("weekTaskTrend", activityService.dailyCounts(userId, 7, TASK_TYPES));
        result.put("monthTaskTrend", activityService.dailyCounts(userId, 30, TASK_TYPES));
        result.put("recentActivities", recent(userId));
        return result;
    }

    private List<Map<String, Object>> recent(Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LearningActivity a : activityService.latest(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", a.getType());
            m.put("refId", a.getRefId());
            m.put("title", a.getTitle());
            m.put("createdAt", a.getCreatedAt());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildAchievements(long enrollCount, long borrowTotal, long completedCount) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(achievement("初出茅庐", "完成第一个项目报名", "🌱", enrollCount >= 1));
        list.add(achievement("借阅达人", "累计借阅10次设备", "📦", borrowTotal >= 10));
        list.add(achievement("项目先锋", "同时推进3个项目", "🚀", enrollCount >= 3));
        list.add(achievement("技术大牛", "5个项目通过评审完成", "🏆", completedCount >= 5));
        return list;
    }

    private Map<String, Object> achievement(String name, String desc, String icon, boolean unlocked) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("desc", desc);
        m.put("icon", icon);
        m.put("unlocked", unlocked);
        return m;
    }
}
