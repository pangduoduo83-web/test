package com.example.ioedunew.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 库内敏感配置(商店 API Key、大模型 API Key 等)的加解密:AES-256-GCM,
 * 密钥由环境变量 IOEDU_MASTER_KEY 经 SHA-256 派生。密文格式 enc:v1:base64(iv+ciphertext)。
 * 未配置主密钥时拒绝加密(提示配置),但仍能读取历史明文值,保证老部署平滑升级。
 */
@Component
public class SecretCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public SecretCrypto(@Value("${ioedu.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.trim().isEmpty()) {
            this.key = null;
        } else {
            try {
                this.key = MessageDigest.getInstance("SHA-256").digest(masterKey.trim().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        if (key == null) {
            throw new BusinessException(500, "未配置主密钥 IOEDU_MASTER_KEY,无法安全保存密钥类配置");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 非 enc:v1: 前缀的值视为历史明文原样返回 */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        if (key == null) {
            throw new BusinessException(500, "未配置主密钥 IOEDU_MASTER_KEY,无法读取已加密的配置");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(500, "解密失败:主密钥与加密时不一致");
        }
    }
}
