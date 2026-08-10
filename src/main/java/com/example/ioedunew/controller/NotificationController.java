package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 通知接口:列表、已读标记。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request) {
        return ApiResponse.ok(notificationService.list(auth(request).getId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        notificationService.markRead(auth(request).getId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(HttpServletRequest request) {
        notificationService.markAllRead(auth(request).getId());
        return ApiResponse.ok();
    }

    private AuthUser auth(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
    }
}
