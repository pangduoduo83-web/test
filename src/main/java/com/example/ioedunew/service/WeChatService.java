package com.example.ioedunew.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 微信小程序服务端能力:code 换 openid、UGC 内容安全检测(msgSecCheck v2)。
 * 未配置 appid/secret 或微信接口不可用时自动降级放行,不影响业务可用性;
 * 仅当微信明确判定 risky/review 时拦截,满足小程序 UGC 审核要求。
 */
@Component
public class WeChatService {

    private static final Logger log = LoggerFactory.getLogger(WeChatService.class);
    private static final String API_BASE = "https://api.weixin.qq.com";

    private final String appid;
    private final String secret;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /** access_token 缓存(微信侧有效期约 2 小时,提前 5 分钟刷新) */
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public WeChatService(@Value("${ioedu.wechat.appid:}") String appid,
                         @Value("${ioedu.wechat.secret:}") String secret,
                         ObjectMapper objectMapper) {
        this.appid = appid == null ? "" : appid.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return !appid.isEmpty() && !secret.isEmpty();
    }

    /** 小程序 wx.login 的 code 换 openid;失败返回 null(调用方降级) */
    public String codeToOpenid(String code) {
        if (!isConfigured() || code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            String url = API_BASE + "/sns/jscode2session?appid=" + appid + "&secret=" + secret
                    + "&js_code=" + code.trim() + "&grant_type=authorization_code";
            JsonNode node = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            String openid = node.path("openid").asText(null);
            if (openid == null) {
                log.warn("jscode2session 失败 errcode={} errmsg={}",
                        node.path("errcode").asInt(), node.path("errmsg").asText());
            }
            return openid;
        } catch (Exception e) {
            log.warn("jscode2session 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * UGC 内容安全检测。scene: 1资料 2评论 3论坛 4社交日志。
     * 返回 false 仅当微信明确判定不安全;检测不可用(未配置/openid 为空/接口异常)时放行。
     */
    public boolean contentSafe(String openid, String content, int scene) {
        if (!isConfigured() || openid == null || content == null || content.trim().isEmpty()) {
            return true;
        }
        try {
            String token = accessToken(false);
            if (token == null) {
                return true;
            }
            JsonNode resp = doSecCheck(token, openid, content, scene);
            // access_token 过期(40001/42001)时强制刷新重试一次
            int errcode = resp.path("errcode").asInt();
            if (errcode == 40001 || errcode == 42001) {
                token = accessToken(true);
                if (token == null) {
                    return true;
                }
                resp = doSecCheck(token, openid, content, scene);
                errcode = resp.path("errcode").asInt();
            }
            if (errcode != 0) {
                log.warn("msgSecCheck 不可用 errcode={} errmsg={},降级放行",
                        errcode, resp.path("errmsg").asText());
                return true;
            }
            String suggest = resp.path("result").path("suggest").asText("pass");
            if (!"pass".equals(suggest)) {
                log.info("msgSecCheck 拦截 suggest={} label={}",
                        suggest, resp.path("result").path("label").asInt());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("msgSecCheck 异常: {},降级放行", e.getMessage());
            return true;
        }
    }

    /**
     * 手机号快速验证组件:用 getPhoneNumber 回调的动态令牌换取用户手机号(纯号码,不含区号)。
     * 仅企业主体小程序可用;失败返回 null,由调用方给出提示。
     */
    public String phoneNumberByCode(String code) {
        if (!isConfigured() || code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            String token = accessToken(false);
            if (token == null) {
                return null;
            }
            JsonNode resp = doGetPhoneNumber(token, code.trim());
            int errcode = resp.path("errcode").asInt();
            if (errcode == 40001 || errcode == 42001) {
                token = accessToken(true);
                if (token == null) {
                    return null;
                }
                resp = doGetPhoneNumber(token, code.trim());
                errcode = resp.path("errcode").asInt();
            }
            if (errcode != 0) {
                log.warn("getuserphonenumber 失败 errcode={} errmsg={}",
                        errcode, resp.path("errmsg").asText());
                return null;
            }
            return resp.path("phone_info").path("purePhoneNumber").asText(null);
        } catch (Exception e) {
            log.warn("getuserphonenumber 异常: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode doGetPhoneNumber(String token, String code) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", code);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String resp = restTemplate.postForObject(
                API_BASE + "/wxa/business/getuserphonenumber?access_token=" + token,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(resp);
    }

    private JsonNode doSecCheck(String token, String openid, String content, int scene) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("version", 2);
        body.put("openid", openid);
        body.put("scene", scene);
        // 微信限制单次 2500 字,超长内容截断检测
        String text = content.length() > 2400 ? content.substring(0, 2400) : content;
        body.put("content", text);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String resp = restTemplate.postForObject(API_BASE + "/wxa/msg_sec_check?access_token=" + token,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(resp);
    }

    private synchronized String accessToken(boolean forceRefresh) {
        if (!forceRefresh && cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedToken;
        }
        try {
            String url = API_BASE + "/cgi-bin/token?grant_type=client_credential&appid=" + appid
                    + "&secret=" + secret;
            JsonNode node = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            String token = node.path("access_token").asText(null);
            if (token == null) {
                log.warn("获取 access_token 失败 errcode={} errmsg={}",
                        node.path("errcode").asInt(), node.path("errmsg").asText());
                return null;
            }
            cachedToken = token;
            tokenExpireAt = System.currentTimeMillis() + (node.path("expires_in").asLong(7200) - 300) * 1000;
            return token;
        } catch (Exception e) {
            log.warn("获取 access_token 异常: {}", e.getMessage());
            return null;
        }
    }
}
