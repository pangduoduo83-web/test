package com.example.ioedunew.service;

import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.repository.LearningActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 学习活动记录与按日聚合。记录失败只打日志,绝不影响业务主流程。
 */
@Service
public class LearningActivityService {

    private static final Logger log = LoggerFactory.getLogger(LearningActivityService.class);

    private final LearningActivityRepository repository;

    public LearningActivityService(LearningActivityRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, String type, Long refId, String title) {
        if (userId == null) {
            return;
        }
        try {
            LearningActivity a = new LearningActivity();
            a.setUserId(userId);
            a.setType(type);
            a.setRefId(refId);
            a.setTitle(title == null ? null : (title.length() > 200 ? title.substring(0, 200) : title));
            repository.save(a);
        } catch (Exception e) {
            log.warn("记录学习活动失败 user={} type={}: {}", userId, type, e.getMessage());
        }
    }

    /** 最近 N 天(含今天)每天的活动次数;types 为空表示全部类型 */
    public List<Integer> dailyCounts(Long userId, int days, Set<String> types) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1);
        Map<LocalDate, Integer> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            byDay.put(d, 0);
        }
        for (LearningActivity a : recent(userId, days)) {
            if (types != null && !types.isEmpty() && !types.contains(a.getType())) {
                continue;
            }
            LocalDate d = a.getCreatedAt().toLocalDate();
            if (byDay.containsKey(d)) {
                byDay.put(d, byDay.get(d) + 1);
            }
        }
        return new ArrayList<>(byDay.values());
    }

    /** 最近 N 天有活动的天数 */
    public int activeDays(Long userId, int days) {
        Set<LocalDate> set = new TreeSet<>();
        for (LearningActivity a : recent(userId, days)) {
            set.add(a.getCreatedAt().toLocalDate());
        }
        return set.size();
    }

    public List<LearningActivity> recent(Long userId, int days) {
        LocalDateTime after = LocalDate.now().minusDays(days - 1).atStartOfDay();
        return repository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, after);
    }

    public List<LearningActivity> latest(Long userId) {
        return repository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }
}
