package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.UserRepository;
import com.example.ioedunew.store.HubClient;
import com.example.ioedunew.store.StoreService;
import com.example.ioedunew.store.StoreSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 项目商店(本地系统侧):
 * - /api/store/**:登录用户可浏览,安装与发布需教师或管理员;
 * - /api/admin/store-settings:管理员维护商店地址与 API Key;
 * - /api/public/store-assets/{sha}.{ext}:商店封面图代理(浏览器不直连商店,内容寻址不可枚举)。
 */
@RestController
public class StoreController {

    private final StoreService storeService;
    private final StoreSettingsService settingsService;
    private final HubClient hubClient;
    private final UserRepository userRepository;

    public StoreController(StoreService storeService, StoreSettingsService settingsService,
                           HubClient hubClient, UserRepository userRepository) {
        this.storeService = storeService;
        this.settingsService = settingsService;
        this.hubClient = hubClient;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/store/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(storeService.status());
    }

    @GetMapping("/api/store/items")
    public ApiResponse<JsonNode> items(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String category) {
        return ApiResponse.ok(storeService.items(q, category));
    }

    @GetMapping("/api/store/items/{id}")
    public ApiResponse<JsonNode> item(@PathVariable long id) {
        return ApiResponse.ok(storeService.item(id));
    }

    /** body: { status?: PUBLISHED|DRAFT, overwriteProjectId?: 已安装的本地项目 id(更新到新版本) } */
    @PostMapping("/api/store/items/{id}/install")
    public ApiResponse<Project> install(@PathVariable long id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        requireStaff(user);
        String status = body == null || body.get("status") == null ? "PUBLISHED" : String.valueOf(body.get("status"));
        Long overwrite = body == null || body.get("overwriteProjectId") == null
                ? null : Long.valueOf(String.valueOf(body.get("overwriteProjectId")));
        return ApiResponse.ok(storeService.install(id, status, overwrite, user, userName(user)));
    }

    /** body: { changelog? } */
    @PostMapping("/api/store/publish/{projectId}")
    public ApiResponse<JsonNode> publish(@PathVariable long projectId,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        requireStaff(user);
        String changelog = body == null || body.get("changelog") == null ? "" : String.valueOf(body.get("changelog"));
        return ApiResponse.ok(storeService.publish(projectId, changelog, user, userName(user)));
    }

    /** 当前用户可发布的本地项目及其在商店中的状态(管理员全部,教师仅自己指导的) */
    @GetMapping("/api/store/local-projects")
    public ApiResponse<List<Map<String, Object>>> localProjects(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        requireStaff(user);
        return ApiResponse.ok(storeService.localProjects(user));
    }

    @GetMapping("/api/store/mine")
    public ApiResponse<JsonNode> mine(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        requireStaff(user);
        return ApiResponse.ok(storeService.mine());
    }

    // ---------- 管理员:接入设置 ----------

    @GetMapping("/api/admin/store-settings")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(settingsService.view());
    }

    @PutMapping("/api/admin/store-settings")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(settingsService.update(body));
    }

    @PostMapping("/api/admin/store-settings/test")
    public ApiResponse<Map<String, Object>> testSettings() {
        return ApiResponse.ok(storeService.status());
    }

    // ---------- 封面图代理(公开) ----------

    @GetMapping("/api/public/store-assets/{sha}.{ext}")
    public ResponseEntity<byte[]> asset(@PathVariable String sha, @PathVariable String ext) {
        if (!sha.matches("^[a-f0-9]{64}$") || !ext.matches("^[a-z0-9]{1,10}$")) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = hubClient.downloadAsset("/hub-assets/" + sha + "." + ext);
        String contentType = URLConnection.guessContentTypeFromName("x." + ext);
        return ResponseEntity.ok()
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(bytes);
    }

    private void requireStaff(AuthUser user) {
        if (!user.isAdmin() && !user.isTeacher()) {
            throw new BusinessException(403, "仅教师或管理员可安装/发布项目");
        }
    }

    private String userName(AuthUser user) {
        User u = userRepository.findById(user.getId()).orElse(null);
        return u == null ? "用户" + user.getId() : u.getName();
    }
}
