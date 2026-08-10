package com.example.ioedunew.service;

import com.example.ioedunew.entity.Notification;
import com.example.ioedunew.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务:创建与查询站内通知,供借阅/项目流程调用。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
}
