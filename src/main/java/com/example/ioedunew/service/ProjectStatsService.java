package com.example.ioedunew.service;

import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.FavoriteRepository;
import com.example.ioedunew.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目统计字段(报名数 / 收藏数 / 完成率)统一由真实记录重算,不再靠各处 +1/-1 维护,
 * 避免种子数据或并发导致的数字漂移。
 */
@Service
public class ProjectStatsService {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FavoriteRepository favoriteRepository;

    public ProjectStatsService(ProjectRepository projectRepository, EnrollmentRepository enrollmentRepository,
                               FavoriteRepository favoriteRepository) {
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional
    public void refresh(Long projectId) {
        Project p = projectRepository.findById(projectId).orElse(null);
        if (p == null) {
            return;
        }
        List<Enrollment> enrollments = enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(projectId);
        long completed = enrollments.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
        p.setEnrolledCount(enrollments.size());
        p.setCompletionRate(enrollments.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / enrollments.size()));
        p.setFavoriteCount((int) favoriteRepository.countByProjectId(projectId));
        projectRepository.save(p);
    }
}
