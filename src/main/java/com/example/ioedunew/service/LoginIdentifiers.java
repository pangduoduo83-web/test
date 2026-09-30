package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.repository.UserRepository;

/** 邮箱、手机号、学号、工号共用登录命名空间,校验仅查询当前租户。 */
final class LoginIdentifiers {
    private LoginIdentifiers() {}

    static String number(String value, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > 30 || normalized.matches(".*[\\s@].*")) {
            throw new BusinessException(label + "最多 30 位,不能包含空白或 @");
        }
        return normalized;
    }

    static void available(UserRepository users, String value, Long selfId, String label) {
        if (value != null && users.findByLoginAccount(value).stream()
                .anyMatch(u -> selfId == null || !selfId.equals(u.getId()))) {
            throw new BusinessException("该" + label + "与已有账号的登录标识重复");
        }
    }

    static void password(String value) {
        if (value == null || value.trim().isEmpty() || value.length() < 6
                || value.length() > 72 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException("密码至少 6 位,最多 72 个 UTF-8 字节");
        }
    }
}
