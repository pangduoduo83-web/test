package com.example.ioedunew.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** Aliyun CDN Type A authentication. The CDN removes auth_key before caching and origin fetches. */
public final class CdnUrls {
    private CdnUrls() { }

    public static void validate(StorageProperties.Cdn config) {
        if (config.getDomain().isEmpty()) return;
        URI uri = URI.create(config.getDomain());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
            throw new IllegalArgumentException("CDN 地址须为 HTTPS 域名，不含路径或查询参数");
        if (!config.getAuthKey().matches("[A-Za-z0-9]{16,128}")
                || config.getAuthSeconds() < 300 || config.getAuthSeconds() > 86400)
            throw new IllegalArgumentException("请配置 CDN A类鉴权密钥及300至86400秒的有效期");
    }

    public static String sign(StorageProperties.Cdn config, String key) {
        return sign(config, key, Instant.now().getEpochSecond());
    }

    static String sign(StorageProperties.Cdn config, String key, long timestamp) {
        validate(config);
        if (config.getDomain().isEmpty()) return null;
        // Object names in this application are generated ASCII paths, never raw user filenames.
        if (!key.matches("[A-Za-z0-9_./-]+") || key.contains("..") || key.startsWith("/"))
            throw new IllegalArgumentException("CDN 文件路径无效");
        String path = "/" + key;
        String fields = timestamp + "-0-0";
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest((path + "-" + fields + "-" + config.getAuthKey()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b & 255));
            return config.getDomain().replaceAll("/+$", "") + path + "?auth_key=" + fields + "-" + hex;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
