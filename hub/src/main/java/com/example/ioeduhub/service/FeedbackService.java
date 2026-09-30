package com.example.ioeduhub.service;

import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.entity.HubFeedback;
import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.repository.FeedbackRepo;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class FeedbackService {
    private static final List<String> CATEGORIES = Arrays.asList("PROCESS", "OPERATION", "SUGGESTION", "OTHER");
    private static final List<String> STATUSES = Arrays.asList("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private final FeedbackRepo repo;

    public FeedbackService(FeedbackRepo repo) { this.repo = repo; }

    @Transactional
    public HubFeedback create(HubTenant tenant, JsonNode body) {
        String category = upper(body.path("category").asText());
        String title = body.path("title").asText("").trim();
        String content = body.path("content").asText("").trim();
        if (!CATEGORIES.contains(category) || title.length() < 2 || title.length() > 120 || content.length() < 5 || content.length() > 5000) {
            throw new BusinessException("反馈内容不完整或格式无效");
        }
        HubFeedback f = new HubFeedback();
        f.setTenantCode(tenant.getCode()); f.setTenantName(tenant.getName());
        Long userId = body.hasNonNull("userId") ? Long.valueOf(body.get("userId").asLong()) : null;
        f.setUserId(userId);
        f.setUserName(body.path("userName").asText("未知用户")); f.setUserRole(body.path("userRole").asText("UNKNOWN"));
        f.setCategory(category); f.setTitle(title); f.setContent(content);
        f.setAttachments(nullIfBlank(body.path("attachments").asText(null)));
        f.setPageUrl(nullIfBlank(body.path("pageUrl").asText(null))); f.setStatus("OPEN");
        return repo.save(f);
    }

    public List<HubFeedback> mine(HubTenant tenant, Long userId) {
        return repo.findByTenantCodeOrderByCreatedAtDesc(tenant.getCode()).stream()
                .filter(f -> userId == null || userId.equals(f.getUserId())).collect(Collectors.toList());
    }

    public List<HubFeedback> list(String tenantCode, String status, String category, String keyword) {
        String tenant = tenantCode == null ? "" : tenantCode.trim().toLowerCase(Locale.ROOT);
        String state = upper(status); String type = upper(category); String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return repo.findAllByOrderByCreatedAtDesc().stream()
                .filter(f -> tenant.isEmpty() || "ALL".equals(tenant) || tenant.equalsIgnoreCase(f.getTenantCode()))
                .filter(f -> state.isEmpty() || "ALL".equals(state) || state.equals(f.getStatus()))
                .filter(f -> type.isEmpty() || "ALL".equals(type) || type.equals(f.getCategory()))
                .filter(f -> query.isEmpty() || contains(f.getTitle(), query) || contains(f.getContent(), query) || contains(f.getUserName(), query) || contains(f.getTenantName(), query))
                .collect(Collectors.toList());
    }

    @Transactional
    public HubFeedback update(Long id, String admin, JsonNode body) {
        HubFeedback f = repo.findById(id).orElseThrow(() -> new BusinessException(404, "反馈不存在"));
        String status = upper(body.path("status").asText());
        if (!STATUSES.contains(status)) throw new BusinessException("处理状态无效");
        f.setStatus(status); f.setAdminReply(nullIfBlank(body.path("adminReply").asText(null))); f.setAdminName(admin); f.setUpdatedAt(LocalDateTime.now());
        return repo.save(f);
    }

    private static boolean contains(String value, String q) { return value != null && value.toLowerCase(Locale.ROOT).contains(q); }
    private static String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static String nullIfBlank(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
