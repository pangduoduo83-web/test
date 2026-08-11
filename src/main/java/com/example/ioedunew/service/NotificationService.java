package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.AdminDtos;
import com.example.ioedunew.entity.Notification;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.NotificationRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知服务:创建与查询站内通知,供借阅/项目流程调用。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public void create(Long userId, String type, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        notificationRepository.save(n);
    }

    public Map<String, Object> list(Long userId) {
        List<Notification> items = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("unread", notificationRepository.countByUserIdAndIsReadFalse(userId));
        return result;
    }

    public void markRead(Long userId, Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllRead(Long userId) {
        List<Notification> items = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        for (Notification n : items) {
            if (!Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
            }
        }
        notificationRepository.saveAll(items);
    }

    public List<Notification> adminList(Long userId, String type, Boolean read) {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(n -> userId == null || userId.equals(n.getUserId()))
                .filter(n -> type == null || type.trim().isEmpty() || "ALL".equalsIgnoreCase(type)
                        || type.equalsIgnoreCase(n.getType()))
                .filter(n -> read == null || read.equals(n.getIsRead()))
                .collect(Collectors.toList());
    }

    @Transactional
    public int adminSend(AdminDtos.NotificationCreateRequest req) {
        List<User> targets;
        if (req.getUserId() != null) {
            User user = userRepository.findById(req.getUserId())
                    .orElseThrow(() -> new BusinessException(404, "接收用户不存在"));
            targets = new ArrayList<>();
            targets.add(user);
        } else if (req.getRole() != null && !req.getRole().trim().isEmpty()) {
            String role = req.getRole().trim().toUpperCase();
            if (!"STUDENT".equals(role) && !"TEACHER".equals(role) && !"ADMIN".equals(role)) {
                throw new BusinessException("非法角色:" + req.getRole());
            }
            targets = userRepository.findByRole(role);
        } else {
            targets = userRepository.findAll();
        }

        List<Notification> notifications = new ArrayList<>();
        for (User target : targets) {
            Notification notification = new Notification();
            notification.setUserId(target.getId());
            notification.setTitle(req.getTitle().trim());
            notification.setContent(req.getContent() == null ? null : req.getContent().trim());
            notification.setType(req.getType().trim());
            notifications.add(notification);
        }
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    public void adminDelete(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "通知不存在"));
        notificationRepository.delete(notification);
    }
}
