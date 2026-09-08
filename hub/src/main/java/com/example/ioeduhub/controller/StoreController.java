package com.example.ioeduhub.controller;

import com.example.ioeduhub.common.ApiResponse;
import com.example.ioeduhub.config.HubAuthFilter;
import com.example.ioeduhub.entity.HubAsset;
import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.service.AssetService;
import com.example.ioeduhub.service.StoreService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户实例调用的商店接口(租户 API Key 鉴权) */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;
    private final AssetService assetService;

    public StoreController(StoreService storeService, AssetService assetService) {
        this.storeService = storeService;
        this.assetService = assetService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", tenant.getCode());
        m.put("name", tenant.getName());
        return ApiResponse.ok(m);
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String category) {
        return ApiResponse.ok(storeService.visibleItems(tenant, q, category));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<Map<String, Object>> item(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant,
                                                 @PathVariable Long id) {
        return ApiResponse.ok(storeService.visibleItem(tenant, id));
    }

    @PostMapping("/items/{id}/install")
    public ApiResponse<Map<String, Object>> install(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant,
                                                    @PathVariable Long id,
                                                    @RequestHeader(value = HubAuthFilter.HEADER_ACTING_USER, required = false) String actingUser) {
        return ApiResponse.ok(storeService.install(tenant, id, decode(actingUser)));
    }

    /** body: { itemId?, title, summary, category, tags[], coverUrl, changelog, payload{} } */
    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant,
                                                    @RequestHeader(value = HubAuthFilter.HEADER_ACTING_USER, required = false) String actingUser,
                                                    @RequestBody JsonNode body) {
        return ApiResponse.ok(storeService.publish(tenant, decode(actingUser), body));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant) {
        return ApiResponse.ok(storeService.mine(tenant));
    }

    /** 上传条目附件,返回 {url:"/hub-assets/{sha}.{ext}"} */
    @PostMapping("/assets")
    public ApiResponse<Map<String, Object>> uploadAsset(@RequestAttribute(HubAuthFilter.ATTR_TENANT) HubTenant tenant,
                                                        @RequestParam("file") MultipartFile file) throws IOException {
        HubAsset asset = assetService.store(file, tenant.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", asset.url());
        m.put("sha256", asset.getSha256());
        m.put("size", asset.getSize());
        return ApiResponse.ok(m);
    }

    /** 操作人姓名经 URL 编码传输(HTTP 头不能直接放中文) */
    private String decode(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(header, "UTF-8");
        } catch (Exception e) {
            return header;
        }
    }
}
