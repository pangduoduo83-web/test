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

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/hub-assets/{sha}.{ext}")
    public ResponseEntity<Resource> serve(@PathVariable String sha, @PathVariable String ext) {
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
}
