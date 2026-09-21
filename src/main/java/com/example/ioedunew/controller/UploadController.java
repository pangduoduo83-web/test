package com.example.ioedunew.controller;

import com.example.ioedunew.service.UploadStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.service.OssFileResponse;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 按租户提供上传文件:/uploads/{yyyyMM}/{uuid.ext} → {upload-dir}/{租户}/{yyyyMM}/{uuid.ext}。
 * 无需登录(图片会被 <img> 直接引用,带不了令牌),文件名为随机 UUID 本身不可枚举。
 */
@RestController
public class UploadController {

    private final UploadStorage storage;
    private final OssFileResponse cloud;

    public UploadController(UploadStorage storage, OssFileResponse cloud) {
        this.storage = storage;
        this.cloud = cloud;
    }

    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        String uri = request.getRequestURI();
        String relative = uri.length() > "/uploads/".length() ? uri.substring("/uploads/".length()) : "";
        if (!relative.matches("\\d{6}/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,10}")) return ResponseEntity.notFound().build();
        StoredFile remote = storage.remote(relative);
        if (remote != null) { cloud.serve(remote.getObjectKey(), relative, request, response); return null; }
        Path file = storage.resolveForRead(relative);
        if (file == null || !file.toFile().isFile()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(file));
    }
}
