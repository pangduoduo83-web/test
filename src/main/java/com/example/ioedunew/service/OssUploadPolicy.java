package com.example.ioedunew.service;

import com.aliyun.oss.common.auth.*;
import com.example.ioedunew.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Single-object POST V4 grants: exact tenant key, exact size, private ACL and short expiration. */
@Component
public class OssUploadPolicy {
    private final StorageProperties properties;
    private final ObjectMapper json;
    public OssUploadPolicy(StorageProperties properties, ObjectMapper json) { this.properties = properties; this.json = json; }

    public String host() {
        StorageProperties.Oss p = properties.getOss();
        String endpoint = p.getUploadEndpoint().isEmpty() ? "https://oss-" + p.getRegion() + ".aliyuncs.com" : p.getUploadEndpoint();
        URI uri = URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != -1
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())) || uri.getHost().contains("-internal")
                || !uri.getHost().matches("oss-[a-z0-9-]+\\.aliyuncs\\.com"))
            throw new BusinessException(503, "OSS 直传地址须使用公网地域 Endpoint");
        return "https://" + p.getBucket() + "." + uri.getHost();
    }

    public Map<String, String> fields(String key, long size, String mime, String id, Instant now) {
        try {
            StorageProperties.Oss p = properties.getOss();
            Credentials c = !p.getAccessKeyId().isEmpty() && !p.getAccessKeySecret().isEmpty()
                    ? new DefaultCredentials(p.getAccessKeyId(), p.getAccessKeySecret(), p.getSecurityToken().isEmpty() ? null : p.getSecurityToken())
                    : CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider().getCredentials();
            String day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("key", key);
            form.put("x-oss-signature-version", "OSS4-HMAC-SHA256");
            form.put("x-oss-credential", c.getAccessKeyId() + "/" + day + "/" + p.getRegion() + "/oss/aliyun_v4_request");
            form.put("x-oss-date", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now));
            if (c.useSecurityToken()) form.put("x-oss-security-token", c.getSecurityToken());
            form.put("success_action_status", "200");
            form.put("x-oss-object-acl", "private");
            form.put("x-oss-forbid-overwrite", "true");
            form.put("x-oss-content-type", mime);
            form.put("x-oss-meta-upload-id", id);
            List<Object> conditions = new ArrayList<>();
            conditions.add(Collections.singletonMap("bucket", p.getBucket()));
            conditions.add(Arrays.asList("content-length-range", size, size));
            form.forEach((k, v) -> conditions.add(Collections.singletonMap(k, v)));
            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("expiration", now.plusSeconds(p.getUploadSeconds()).toString()); policy.put("conditions", conditions);
            String encoded = Base64.getEncoder().encodeToString(json.writeValueAsBytes(policy));
            byte[] signing = hmac(("aliyun_v4" + c.getSecretAccessKey()).getBytes(StandardCharsets.UTF_8), day);
            for (String part : Arrays.asList(p.getRegion(), "oss", "aliyun_v4_request")) signing = hmac(signing, part);
            form.put("policy", encoded); form.put("x-oss-signature", hex(hmac(signing, encoded)));
            return form;
        } catch (Exception e) { throw new BusinessException(503, "无法生成 OSS 直传凭证，请检查平台存储配置"); }
    }

    static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b & 255)); return s.toString(); }
}
