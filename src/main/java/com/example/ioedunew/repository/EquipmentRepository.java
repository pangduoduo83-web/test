package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 设备仓库 */
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByName(String name);
}
