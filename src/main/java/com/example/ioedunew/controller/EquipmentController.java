package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.service.EquipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备图书馆接口:学生浏览与筛选设备。
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
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
