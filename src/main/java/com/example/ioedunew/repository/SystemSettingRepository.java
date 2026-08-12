package com.example.ioedunew.repository;

import com.example.ioedunew.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** 系统设置仓库 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
