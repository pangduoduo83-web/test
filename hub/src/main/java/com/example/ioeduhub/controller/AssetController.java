package com.example.ioeduhub.controller;

import com.example.ioeduhub.service.AssetService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 附件公开读取:内容寻址的文件名本身不可枚举,且内容不可变,可长期缓存 */
@RestController
public class AssetController {

    private final AssetService assetService;
    private final com.example.ioeduhub.service.OssFileResponse cloud;

    public AssetController(AssetService assetService, com.example.ioeduhub.service.OssFileResponse cloud) {
        this.assetService = assetService;
        this.cloud = cloud;
    }

    @GetMapping("/hub-assets/{sha}.{ext}")
    public ResponseEntity<Resource> serve(@PathVariable String sha, @PathVariable String ext,
            javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        com.example.ioeduhub.entity.HubAsset remote = assetService.remote(sha, ext);
        if (remote != null) { cloud.serve(remote.getObjectKey(), sha + "." + ext, request, response); return null; }
        Path file = assetService.resolve(sha, ext);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(file));
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/hub-admin/assets/migrate")
    public com.example.ioeduhub.common.ApiResponse<java.util.Map<String, Object>> migrate(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "true") boolean dryRun,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        return com.example.ioeduhub.common.ApiResponse.ok(assetService.migrate(dryRun, limit));
    }
}
