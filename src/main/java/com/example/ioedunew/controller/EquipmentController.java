package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.service.EquipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 设备图书馆接口:学生浏览、筛选与心愿单。
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    /** 我的心愿单设备 id 列表 */
    @GetMapping("/favorites")
    public ApiResponse<List<Long>> favorites(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(equipmentService.favoriteIds(user.getId()));
    }

    /** 心愿单开关 */
    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Boolean>> toggleFavorite(
            @PathVariable Long id,
            @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        boolean favorited = equipmentService.toggleFavorite(user.getId(), id);
        return ApiResponse.ok(Collections.singletonMap("favorited", favorited));
    }

    @GetMapping
    public ApiResponse<List<Equipment>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String location,
                                             @RequestParam(required = false) Double minRating) {
        return ApiResponse.ok(equipmentService.list(keyword, status, location, minRating));
    }

    @GetMapping("/locations")
    public ApiResponse<List<String>> locations() {
        return ApiResponse.ok(equipmentService.locations());
    }

    @GetMapping("/{id}")
    public ApiResponse<Equipment> get(@PathVariable Long id) {
        return ApiResponse.ok(equipmentService.get(id));
    }
}
