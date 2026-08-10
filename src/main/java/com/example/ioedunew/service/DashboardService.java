package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人中心服务:汇总统计卡、进行中项目、成就徽章与学习趋势。
 * 成就为规则计算而非落库:根据报名数/借阅数/完成数实时判定。
 */
@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BorrowRequestRepository borrowRepository;
    private final SkillScoreRepository skillScoreRepository;

    public DashboardService(UserRepository userRepository,
                            EnrollmentRepository enrollmentRepository,
                            BorrowRequestRepository borrowRepository,
                            SkillScoreRepository skillScoreRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.borrowRepository = borrowRepository;
        this.skillScoreRepository = skillScoreRepository;
    }

    public Map<String, Object> overview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        long enrollCount = enrollmentRepository.countByUserId(userId);
        long completedCount = enrollmentRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long borrowTotal = borrowRepository.countByUserId(userId);

        List<SkillScore> skills = skillScoreRepository.findByUserId(userId);
        int skillAvg = (int) Math.round(skills.stream().mapToInt(SkillScore::getScore).average().orElse(0));

        List<Map<String, Object>> achievements = buildAchievements(enrollCount, borrowTotal, completedCount);
        long unlocked = achievements.stream().filter(a -> Boolean.TRUE.equals(a.get("unlocked"))).count();

        List<Enrollment> ongoing = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByUserIdOrderByEnrolledAtDesc(userId)) {
            if ("IN_PROGRESS".equals(e.getStatus())) {
                ongoing.add(e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("level", user.getExp() / 100 + 1);
        result.put("levelProgress", user.getExp() % 100);
        result.put("weeklyHours", user.getWeeklyHours());
        result.put("achievementCount", unlocked);
        result.put("completedProjects", completedCount);
        result.put("skillAvg", skillAvg);
        result.put("ongoingProjects", ongoing);
        result.put("achievements", achievements);
        result.put("weekTrend", buildTrend(user, 7));
        result.put("monthTrend", buildTrend(user, 30));
        result.put("weekTaskTrend", buildTaskTrend(user, 7));
        result.put("monthTaskTrend", buildTaskTrend(user, 30));
        return result;
    }

    private List<Map<String, Object>> buildAchievements(long enrollCount, long borrowTotal, long completedCount) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(achievement("初出茅庐", "完成第一个项目报名", "🌱", enrollCount >= 1));
        list.add(achievement("借阅达人", "累计借阅10次设备", "📦", borrowTotal >= 10));
        list.add(achievement("项目先锋", "同时推进3个项目", "🚀", enrollCount >= 3));
        list.add(achievement("技术大牛", "完成5个进阶项目", "🏆", completedCount >= 5));
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

    /**
     * 学习趋势曲线:以用户经验值为基线生成平滑序列,仅用于图表展示。
     */
    private List<Integer> buildTrend(User user, int points) {
        List<Integer> trend = new ArrayList<>();
        int base = Math.max(1, user.getWeeklyHours());
        long seed = user.getId() * 31 + user.getExp();
        for (int i = 0; i < points; i++) {
            seed = (seed * 1103515245L + 12345) & 0x7fffffff;
            trend.add((int) (seed % (base + 3)) + (i % 3));
        }
        return trend;
    }

    /**
     * 完成任务数曲线:与学习时长曲线同源但换种子,作为趋势图第二条序列。
     */
    private List<Integer> buildTaskTrend(User user, int points) {
        List<Integer> trend = new ArrayList<>();
        long seed = user.getId() * 17 + user.getExp() + 7;
        for (int i = 0; i < points; i++) {
            seed = (seed * 1103515245L + 12345) & 0x7fffffff;
            trend.add((int) (seed % 5) + (i % 2) + 1);
        }
        return trend;
    }
}
