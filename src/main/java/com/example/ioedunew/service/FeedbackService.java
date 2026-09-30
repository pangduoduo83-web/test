package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Feedback;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.FeedbackRepository;
import com.example.ioedunew.repository.UserRepository;
import com.example.ioedunew.store.HubClient;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private static final List<String> CATEGORIES = Arrays.asList("PROCESS", "OPERATION", "SUGGESTION", "OTHER");
    private static final List<String> STATUSES = Arrays.asList("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final HubClient hubClient;
    private final ObjectMapper objectMapper;

    public FeedbackService(FeedbackRepository feedbackRepository, UserRepository userRepository,
                           HubClient hubClient, ObjectMapper objectMapper) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.hubClient = hubClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Feedback create(AuthUser auth, MiscDtos.FeedbackCreateRequest req) {
        User user = userRepository.findById(auth.getId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        String category = normalize(req.getCategory());
        if (!CATEGORIES.contains(category)) throw new BusinessException("反馈类型无效");
        Feedback item = new Feedback();
        item.setUserId(user.getId());
        item.setUserName(user.getName());
        item.setUserRole(user.getRole());
        item.setCategory(category);
        item.setTitle(req.getTitle().trim());
        item.setContent(req.getContent().trim());
        item.setPageUrl(trimToNull(req.getPageUrl()));
        item.setAttachments(trimToNull(req.getAttachments()));
        item.setStatus("OPEN");
        Feedback saved = feedbackRepository.save(item);
        // 本地记录用于用户在分站查看历史；平台中心保存一份跨租户汇总记录。
        try {
            ObjectNode payload = objectMapper.valueToTree(saved);
            payload.put("tenantCode", TenantContext.require());
            hubClient.submitFeedback(payload, user.getName());
        } catch (RuntimeException e) {
            // 中心短暂不可用时不丢失用户反馈，下一版可增加待同步队列。
            log.warn("反馈已写入本地，但同步平台中心失败: {}", e.getMessage());
        }
        return saved;
    }

    public List<Feedback> mine(Long userId) {
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Feedback> adminList(String status, String category, String keyword) {
        String normalizedStatus = status == null ? "" : normalize(status);
        String normalizedCategory = category == null ? "" : normalize(category);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(f -> normalizedStatus.isEmpty() || "ALL".equals(normalizedStatus) || normalizedStatus.equals(f.getStatus()))
                .filter(f -> normalizedCategory.isEmpty() || "ALL".equals(normalizedCategory) || normalizedCategory.equals(f.getCategory()))
                .filter(f -> query.isEmpty() || contains(f.getTitle(), query) || contains(f.getContent(), query)
                        || contains(f.getUserName(), query))
                .collect(Collectors.toList());
    }

    @Transactional
    public Feedback update(Long id, AuthUser admin, MiscDtos.FeedbackUpdateRequest req) {
        Feedback item = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "反馈不存在"));
        String status = normalize(req.getStatus());
        if (!STATUSES.contains(status)) throw new BusinessException("处理状态无效");
        item.setStatus(status);
        item.setAdminReply(trimToNull(req.getAdminReply()));
        item.setAdminId(admin.getId());
        User user = userRepository.findById(admin.getId()).orElse(null);
        item.setAdminName(user == null ? null : user.getName());
        item.setUpdatedAt(LocalDateTime.now());
        return feedbackRepository.save(item);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
