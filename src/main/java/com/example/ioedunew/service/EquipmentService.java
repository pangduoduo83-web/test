package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.EquipmentFavorite;
import com.example.ioedunew.repository.EquipmentFavoriteRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设备服务:设备图书馆的查询、筛选与心愿单。
 * 筛选语义:status=BORROWED_OUT 表示可借数量为 0 的设备(前端显示"已借完")。
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentFavoriteRepository favoriteRepository;

    public EquipmentService(EquipmentRepository equipmentRepository,
                            EquipmentFavoriteRepository favoriteRepository) {
        this.equipmentRepository = equipmentRepository;
        this.favoriteRepository = favoriteRepository;
    }

    public List<Equipment> list(String keyword, String status, String location, Double minRating) {
        return equipmentRepository.findAll().stream()
                .filter(e -> {
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return true;
                    }
                    String kw = keyword.trim().toLowerCase();
                    return contains(e.getName(), kw) || contains(e.getModel(), kw)
                            || contains(e.getDescription(), kw) || contains(e.getTags(), kw);
                })
                .filter(e -> {
                    if (status == null || status.isEmpty() || "ALL".equals(status)) {
                        return true;
                    }
                    if ("BORROWED_OUT".equals(status)) {
                        return "AVAILABLE".equals(e.getStatus()) && e.getAvailableCount() <= 0;
                    }
                    if ("AVAILABLE".equals(status)) {
                        return "AVAILABLE".equals(e.getStatus()) && e.getAvailableCount() > 0;
                    }
                    return status.equals(e.getStatus());
                })
                .filter(e -> location == null || location.isEmpty() || "ALL".equals(location)
                        || location.equals(e.getLocation()))
                .filter(e -> minRating == null || e.getRating() >= minRating)
                .collect(Collectors.toList());
    }

    public Equipment get(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "设备不存在"));
    }

    public List<String> locations() {
        return equipmentRepository.findAll().stream()
                .map(Equipment::getLocation)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** 心愿单开关,返回切换后的状态 */
    @Transactional
    public boolean toggleFavorite(Long userId, Long equipmentId) {
        get(equipmentId);
        EquipmentFavorite existing = favoriteRepository
                .findByUserIdAndEquipmentId(userId, equipmentId).orElse(null);
        if (existing != null) {
            favoriteRepository.delete(existing);
            return false;
        }
        EquipmentFavorite favorite = new EquipmentFavorite();
        favorite.setUserId(userId);
        favorite.setEquipmentId(equipmentId);
        favoriteRepository.save(favorite);
        return true;
    }

    /** 我的心愿单设备 id 列表 */
    public List<Long> favoriteIds(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(EquipmentFavorite::getEquipmentId)
                .collect(Collectors.toList());
    }

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }
}
