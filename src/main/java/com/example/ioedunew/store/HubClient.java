package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 调用项目商店(hub)的客户端:只在服务端发起,API Key 不出后端,浏览器永远不直连商店。
 */
@Component
public class HubClient {

    private final StoreSettingsService settings;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RestTemplate uploadTemplate;

    public HubClient(StoreSettingsService settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(f);
        SimpleClientHttpRequestFactory uf = new SimpleClientHttpRequestFactory();
        uf.setConnectTimeout(3000);
        uf.setReadTimeout(180000);
        this.uploadTemplate = new RestTemplate(uf);
    }

    public JsonNode me() {
        return get("/api/store/me", null);
    }

    public JsonNode items(String q, String category) {
        StringBuilder path = new StringBuilder("/api/store/items?");
        if (q != null && !q.trim().isEmpty()) {
            path.append("q=").append(encode(q.trim())).append('&');
        }
        if (category != null && !category.trim().isEmpty()) {
            path.append("category=").append(encode(category.trim()));
        }
        return get(path.toString(), null);
    }

    public JsonNode item(long id) {
        return get("/api/store/items/" + id, null);
    }

    public JsonNode install(long id, String actingUser) {
        return exchange(HttpMethod.POST, "/api/store/items/" + id + "/install", null, actingUser);
    }

    public JsonNode publish(JsonNode body, String actingUser) {
        return exchange(HttpMethod.POST, "/api/store/publish", body, actingUser);
    }

    public JsonNode mine() {
        return get("/api/store/mine", null);
    }

    /** 上传附件到商店,返回 /hub-assets/{sha}.{ext} */
    public String uploadAsset(Path file) {
        HttpHeaders headers = authHeaders(null);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(file));
        try {
            ResponseEntity<String> resp = uploadTemplate.exchange(settings.baseUrl() + "/api/store/assets",
                    HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
            return unwrap(resp.getBody()).path("url").asText();
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(502, "无法连接项目商店: " + e.getMessage());
        }
    }

    /** 下载商店附件(公开地址,不需要 API Key) */
    public byte[] downloadAsset(String assetPath) {
        try {
            ResponseEntity<byte[]> resp = uploadTemplate.getForEntity(settings.baseUrl() + assetPath, byte[].class);
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(502, "下载商店附件失败: HTTP " + e.getRawStatusCode());
        } catch (ResourceAccessException e) {
            throw new BusinessException(502, "无法连接项目商店: " + e.getMessage());
        }
    }

    private JsonNode get(String path, String actingUser) {
        return exchange(HttpMethod.GET, path, null, actingUser);
    }

    private JsonNode exchange(HttpMethod method, String path, JsonNode body, String actingUser) {
        if (!settings.isConfigured()) {
            throw new BusinessException(412, "尚未接入项目商店,请管理员在「项目商店 → 接入设置」填写商店地址与 API Key");
        }
        HttpHeaders headers = authHeaders(actingUser);
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        try {
            String json = body == null ? null : objectMapper.writeValueAsString(body);
            ResponseEntity<String> resp = restTemplate.exchange(settings.baseUrl() + path, method,
                    new HttpEntity<>(json, headers), String.class);
            return unwrap(resp.getBody());
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(502, "无法连接项目商店: " + e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "项目商店响应异常: " + e.getMessage());
        }
    }

    private HttpHeaders authHeaders(String actingUser) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + settings.apiKey());
        if (actingUser != null && !actingUser.isEmpty()) {
            headers.set("X-Acting-User", encode(actingUser));
        }
        return headers;
    }

    /** 商店的统一响应 {code,message,data}:code≠0 转成业务异常,否则返回 data */
    private JsonNode unwrap(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new BusinessException(code == -1 ? 502 : code, "项目商店:" + root.path("message").asText("未知错误"));
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "项目商店响应格式异常");
        }
    }

    private BusinessException translate(HttpStatusCodeException e) {
        try {
            JsonNode root = objectMapper.readTree(e.getResponseBodyAsString());
            return new BusinessException(e.getRawStatusCode(), "项目商店:" + root.path("message").asText(e.getStatusText()));
        } catch (Exception ignored) {
            return new BusinessException(e.getRawStatusCode(), "项目商店:HTTP " + e.getRawStatusCode());
        }
    }

    private static String encode(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }
}
