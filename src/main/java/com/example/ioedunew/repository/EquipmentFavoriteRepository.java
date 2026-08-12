package com.example.ioedunew.repository;

import com.example.ioedunew.entity.EquipmentFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 设备心愿单仓库 */
public interface EquipmentFavoriteRepository extends JpaRepository<EquipmentFavorite, Long> {

    List<EquipmentFavorite> findByUserId(Long userId);

    Optional<EquipmentFavorite> findByUserIdAndEquipmentId(Long userId, Long equipmentId);

    void deleteByUserId(Long userId);

    void deleteByEquipmentId(Long equipmentId);
}
