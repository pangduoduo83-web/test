package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import com.example.ioedunew.service.SiteConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 公开接口:落地页统计数字与站点配置,无需登录。
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SiteConfigService siteConfigService;

    public PublicController(EquipmentRepository equipmentRepository,
                            UserRepository userRepository,
                            ProjectRepository projectRepository,
                            SiteConfigService siteConfigService) {
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.siteConfigService = siteConfigService;
    }

    /** 站点配置:标题/LOGO/底部信息/注册开关/每页数量/分类 */
    @GetMapping("/site-config")
    public ApiResponse<Map<String, Object>> siteConfig() {
        return ApiResponse.ok(siteConfigService.publicConfig());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("equipmentCount", equipmentRepository.count());
        m.put("studentCount", userRepository.countByRole("STUDENT"));
        m.put("projectCount", projectRepository.count());
        return ApiResponse.ok(m);
    }
}
