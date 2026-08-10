package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.repository.EquipmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设备服务:设备图书馆的查询与筛选。
 * 筛选语义:status=BORROWED_OUT 表示可借数量为 0 的设备(前端显示"已借完")。
 */
@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(EquipmentRepository equipmentRepository) {
        this.equipmentRepository = equipmentRepository;
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

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }
}
