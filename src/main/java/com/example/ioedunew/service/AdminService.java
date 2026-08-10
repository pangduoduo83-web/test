package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端服务:设备/项目 CRUD、用户管理与数据看板统计。
 * 设备删除边界:存在未归还借阅时禁止删除,避免库存对不上。
 */
@Service
public class AdminService {

    private final EquipmentRepository equipmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final BorrowRequestRepository borrowRepository;

    public AdminService(EquipmentRepository equipmentRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        BorrowRequestRepository borrowRepository) {
        this.equipmentRepository = equipmentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.borrowRepository = borrowRepository;
    }

    // ---------- 设备 ----------

    @Transactional
    public Equipment saveEquipment(Equipment input) {
        normalizeEquipmentJson(input);
        if (input.getId() != null) {
            Equipment existing = equipmentRepository.findById(input.getId())
                    .orElseThrow(() -> new BusinessException(404, "设备不存在"));
            input.setCreatedAt(existing.getCreatedAt());
            input.setBorrowCount(existing.getBorrowCount());
        } else {
            input.setBorrowCount(0);
        }
        if (input.getAvailableCount() == null || input.getAvailableCount() > input.getTotalCount()) {
            input.setAvailableCount(input.getTotalCount());
        }
        return equipmentRepository.save(input);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        boolean hasActive = borrowRepository.findAllByOrderByAppliedAtDesc().stream()
                .anyMatch(b -> b.getEquipmentId().equals(id)
                        && ("APPROVED".equals(b.getStatus()) || "RETURN_REQUESTED".equals(b.getStatus())
                        || "PENDING".equals(b.getStatus())));
        if (hasActive) {
            throw new BusinessException("该设备存在进行中的借阅,不能删除");
        }
        equipmentRepository.deleteById(id);
    }

    // ---------- 项目 ----------

    @Transactional
    public Project saveProject(Project input) {
        normalizeProjectJson(input);
        if (input.getId() != null) {
            Project existing = projectRepository.findById(input.getId())
                    .orElseThrow(() -> new BusinessException(404, "项目不存在"));
            input.setCreatedAt(existing.getCreatedAt());
            input.setEnrolledCount(existing.getEnrolledCount());
            input.setViews(existing.getViews());
            input.setFavoriteCount(existing.getFavoriteCount());
            input.setDownloads(existing.getDownloads());
            if (input.getForks() == null) {
                input.setForks(existing.getForks());
            }
        }
        input.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(input);
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    // ---------- 用户 ----------

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUser(Long id, MiscDtos.UserAdminUpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        if (req.getRole() != null) {
            if (!"STUDENT".equals(req.getRole()) && !"TEACHER".equals(req.getRole())
                    && !"ADMIN".equals(req.getRole())) {
                throw new BusinessException("非法角色:" + req.getRole());
            }
            user.setRole(req.getRole());
        }
        return userRepository.save(user);
    }

    // ---------- 数据看板 ----------

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("studentCount", userRepository.countByRole("STUDENT"));
        m.put("equipmentCount", equipmentRepository.count());
        m.put("projectCount", projectRepository.count());
        m.put("pendingBorrows", borrowRepository.countByStatus("PENDING"));
        m.put("activeBorrows", borrowRepository.countByStatus("APPROVED")
                + borrowRepository.countByStatus("RETURN_REQUESTED"));
        m.put("returnRequests", borrowRepository.countByStatus("RETURN_REQUESTED"));
        m.put("recentPending", borrowRepository.findByStatusOrderByAppliedAtDesc("PENDING").stream()
                .limit(5).toArray());
        return m;
    }

    /**
     * 报表数据:近 30 天申请/归还趋势 + 设备利用率排行(按累计借出次数取前 8)。
     */
    public Map<String, Object> trends() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<String> days = new java.util.ArrayList<>();
        java.util.List<Long> applied = new java.util.ArrayList<>();
        java.util.List<Long> returned = new java.util.ArrayList<>();
        java.util.List<com.example.ioedunew.entity.BorrowRequest> all = borrowRepository.findAll();
        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            days.add(day.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
            applied.add(all.stream()
                    .filter(b -> b.getAppliedAt() != null && b.getAppliedAt().toLocalDate().equals(day))
                    .count());
            returned.add(all.stream()
                    .filter(b -> b.getReturnedAt() != null && b.getReturnedAt().toLocalDate().equals(day))
                    .count());
        }

        java.util.List<Map<String, Object>> utilization = equipmentRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Equipment::getBorrowCount).reversed())
                .limit(8)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getName());
                    m.put("borrowCount", e.getBorrowCount());
                    m.put("inUse", e.getTotalCount() - e.getAvailableCount());
                    m.put("total", e.getTotalCount());
                    m.put("inUseRate", e.getTotalCount() == 0 ? 0
                            : Math.round((e.getTotalCount() - e.getAvailableCount()) * 100.0 / e.getTotalCount()));
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("applied", applied);
        result.put("returned", returned);
        result.put("utilization", utilization);
        return result;
    }

    // ---------- JSON 字段兜底 ----------

    private void normalizeEquipmentJson(Equipment e) {
        e.setSpecs(orEmptyArray(e.getSpecs()));
        e.setTags(orEmptyArray(e.getTags()));
        e.setDocs(orEmptyArray(e.getDocs()));
        e.setSuitableProjects(orEmptyArray(e.getSuitableProjects()));
    }

    private void normalizeProjectJson(Project p) {
        p.setTags(orEmptyArray(p.getTags()));
        p.setFeatures(orEmptyArray(p.getFeatures()));
        p.setLearningGoals(orEmptyArray(p.getLearningGoals()));
        p.setPrerequisites(orEmptyArray(p.getPrerequisites()));
        p.setSkillRequirements(orEmptyArray(p.getSkillRequirements()));
        p.setSyllabus(orEmptyArray(p.getSyllabus()));
        p.setBom(orEmptyArray(p.getBom()));
        p.setResources(orEmptyArray(p.getResources()));
        p.setEquipmentNames(orEmptyArray(p.getEquipmentNames()));
    }

    private String orEmptyArray(String json) {
        return (json == null || json.trim().isEmpty()) ? "[]" : json;
    }
}
