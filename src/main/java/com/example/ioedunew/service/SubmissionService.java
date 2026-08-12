package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目成果提交与评分服务。
 * 评分副作用:>=60 分自动把对应报名进度置为 100(完成),经验值按 score/10 发放,并通知学生;
 * 这些副作用只允许经由 grade() 发生。
 */
@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SubmissionService(SubmissionRepository submissionRepository,
                             EnrollmentRepository enrollmentRepository,
                             ProjectRepository projectRepository,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.submissionRepository = submissionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Submission submit(Long userId, Long projectId, MiscDtos.SubmissionRequest req) {
        if (req.getContent() == null || req.getContent().trim().isEmpty()) {
            throw new BusinessException("成果说明不能为空");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        enrollmentRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseThrow(() -> new BusinessException("请先报名该项目再提交成果"));

        // 考核项校验:项目定义了考核项则必须选择其一;未定义则按整体成果提交
        List<AssessmentItem> assessments = parseAssessments(project.getAssessments());
        String assessmentName = req.getAssessmentName() == null ? "" : req.getAssessmentName().trim();
        if (!assessments.isEmpty()) {
            String finalName = assessmentName;
            boolean valid = assessments.stream().anyMatch(a -> a.name.equals(finalName));
            if (!valid) {
                throw new BusinessException("请选择要提交的考核项");
            }
        } else {
            assessmentName = "";
        }

        // 同一维度(考核项/整体)只允许一份在评审中
        Submission last = latestOf(userId, projectId, assessmentName);
        if (last != null && "SUBMITTED".equals(last.getStatus())) {
            throw new BusinessException((assessmentName.isEmpty() ? "上一份成果" : "「" + assessmentName + "」的上一份提交")
                    + "还在评审中,请耐心等待");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        Submission s = new Submission();
        s.setUserId(userId);
        s.setProjectId(projectId);
        s.setUserName(user.getName());
        s.setProjectTitle(project.getTitle());
        s.setContent(req.getContent().trim());
        s.setAttachmentUrl(req.getAttachmentUrl());
        s.setAssessmentName(assessmentName.isEmpty() ? null : assessmentName);
        return submissionRepository.save(s);
    }

    public Submission mySubmission(Long userId, Long projectId) {
        return submissionRepository
                .findTopByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId).orElse(null);
    }

    /** 我在该项目的全部提交(含各考核项),按时间倒序 */
    public List<Submission> mySubmissions(Long userId, Long projectId) {
        return submissionRepository.findByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId);
    }

    public List<Submission> listAll(String status) {
        return listAll(status, null, null);
    }

    public List<Submission> listAll(String status, Long userId, Long projectId) {
        List<Submission> items;
        if (status == null || status.isEmpty() || "ALL".equals(status)) {
            items = submissionRepository.findAllByOrderBySubmittedAtDesc();
        } else {
            items = submissionRepository.findByStatusOrderBySubmittedAtDesc(status);
        }
        return items.stream()
                .filter(s -> userId == null || userId.equals(s.getUserId()))
                .filter(s -> projectId == null || projectId.equals(s.getProjectId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Submission grade(Long submissionId, MiscDtos.GradeRequest req, String graderName) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交记录不存在"));
        if ("GRADED".equals(s.getStatus())) {
            throw new BusinessException("该成果已评过分");
        }
        s.setStatus("GRADED");
        s.setScore(req.getScore());
        s.setFeedback(req.getFeedback());
        s.setGraderName(graderName);
        s.setGradedAt(LocalDateTime.now());
        submissionRepository.save(s);

        if (s.getAssessmentName() == null || s.getAssessmentName().isEmpty()) {
            gradeWhole(s, req);
        } else {
            gradeAssessment(s, req);
        }
        return s;
    }

    /** 整体单一成果:旧行为不变 */
    private void gradeWhole(Submission s, MiscDtos.GradeRequest req) {
        userRepository.findById(s.getUserId()).ifPresent(u -> {
            u.setExp(u.getExp() + req.getScore() / 10);
            userRepository.save(u);
        });
        if (req.getScore() >= 60) {
            completeEnrollment(s.getUserId(), s.getProjectId());
        }
        notificationService.create(s.getUserId(), "project", "成果评分完成",
                "《" + s.getProjectTitle() + "》评分 " + req.getScore() + " 分"
                        + (req.getScore() >= 60 ? ",项目已判定完成!" : ",继续加油,可修改后再次提交。")
                        + (req.getFeedback() == null || req.getFeedback().isEmpty() ? "" : " 评语:" + req.getFeedback()));
    }

    /**
     * 分阶段考核项评分:经验按权重折算;
     * 全部考核项均已评分时计算加权综合分,综合 >=60 判定项目完成。
     */
    private void gradeAssessment(Submission s, MiscDtos.GradeRequest req) {
        Project project = projectRepository.findById(s.getProjectId()).orElse(null);
        List<AssessmentItem> assessments = project == null
                ? java.util.Collections.emptyList() : parseAssessments(project.getAssessments());
        int weight = assessments.stream()
                .filter(a -> a.name.equals(s.getAssessmentName()))
                .map(a -> a.weight).findFirst().orElse(0);

        userRepository.findById(s.getUserId()).ifPresent(u -> {
            u.setExp(u.getExp() + Math.max(1, req.getScore() * weight / 1000));
            userRepository.save(u);
        });
        notificationService.create(s.getUserId(), "project", "考核项评分完成",
                "《" + s.getProjectTitle() + "》考核项「" + s.getAssessmentName() + "」(权重 " + weight + "%)评分 "
                        + req.getScore() + " 分。"
                        + (req.getFeedback() == null || req.getFeedback().isEmpty() ? "" : " 评语:" + req.getFeedback()));

        // 综合判定:每个考核项取最新一次已评分的提交
        if (assessments.isEmpty()) {
            return;
        }
        List<Submission> mine = submissionRepository
                .findByUserIdAndProjectIdOrderBySubmittedAtDesc(s.getUserId(), s.getProjectId());
        double total = 0;
        for (AssessmentItem item : assessments) {
            Submission latestGraded = mine.stream()
                    .filter(x -> item.name.equals(x.getAssessmentName()) && "GRADED".equals(x.getStatus()))
                    .findFirst().orElse(null);
            if (latestGraded == null) {
                return; // 还有考核项未评分,暂不综合
            }
            total += latestGraded.getScore() * item.weight / 100.0;
        }
        int overall = (int) Math.round(total);
        boolean pass = overall >= 60;
        if (pass) {
            completeEnrollment(s.getUserId(), s.getProjectId());
        }
        notificationService.create(s.getUserId(), "project", "综合评分出炉",
                "《" + s.getProjectTitle() + "》全部考核项已评完,加权综合 " + overall + " 分"
                        + (pass ? ",项目已判定完成!" : ",未达 60 分,可完善后重新提交薄弱项。"));
    }

    private void completeEnrollment(Long userId, Long projectId) {
        Enrollment e = enrollmentRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);
        if (e != null && !"COMPLETED".equals(e.getStatus())) {
            e.setProgress(100);
            e.setStatus("COMPLETED");
            e.setCurrentTask("已通过成果评审");
            enrollmentRepository.save(e);
        }
    }

    /** 解析项目考核项 JSON:[{name,weight,desc}] */
    List<AssessmentItem> parseAssessments(String json) {
        List<AssessmentItem> list = new java.util.ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : node) {
                    String name = n.path("name").asText("").trim();
                    if (!name.isEmpty()) {
                        list.add(new AssessmentItem(name, n.path("weight").asInt(0)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private Submission latestOf(Long userId, Long projectId, String assessmentName) {
        for (Submission s : submissionRepository.findByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId)) {
            String name = s.getAssessmentName() == null ? "" : s.getAssessmentName();
            if (name.equals(assessmentName)) {
                return s;
            }
        }
        return null;
    }

    /** 考核项定义 */
    static class AssessmentItem {
        final String name;
        final int weight;

        AssessmentItem(String name, int weight) {
            this.name = name;
            this.weight = weight;
        }
    }
}
