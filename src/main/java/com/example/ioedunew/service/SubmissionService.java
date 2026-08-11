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
        Submission last = submissionRepository
                .findTopByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId).orElse(null);
        if (last != null && "SUBMITTED".equals(last.getStatus())) {
            throw new BusinessException("上一份成果还在评审中,请耐心等待");
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
        return submissionRepository.save(s);
    }

    public Submission mySubmission(Long userId, Long projectId) {
        return submissionRepository
                .findTopByUserIdAndProjectIdOrderBySubmittedAtDesc(userId, projectId).orElse(null);
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

        userRepository.findById(s.getUserId()).ifPresent(u -> {
            u.setExp(u.getExp() + req.getScore() / 10);
            userRepository.save(u);
        });

        if (req.getScore() >= 60) {
            Enrollment e = enrollmentRepository
                    .findByUserIdAndProjectId(s.getUserId(), s.getProjectId()).orElse(null);
            if (e != null && !"COMPLETED".equals(e.getStatus())) {
                e.setProgress(100);
                e.setStatus("COMPLETED");
                e.setCurrentTask("已通过成果评审");
                enrollmentRepository.save(e);
            }
        }
        notificationService.create(s.getUserId(), "project", "成果评分完成",
                "《" + s.getProjectTitle() + "》评分 " + req.getScore() + " 分"
                        + (req.getScore() >= 60 ? ",项目已判定完成!" : ",继续加油,可修改后再次提交。")
                        + (req.getFeedback() == null || req.getFeedback().isEmpty() ? "" : " 评语:" + req.getFeedback()));
        return s;
    }
}
