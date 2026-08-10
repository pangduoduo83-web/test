package com.example.ioedunew.dto;

import com.example.ioedunew.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 认证相关请求/响应模型。
 */
public class AuthDtos {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;

        @NotBlank(message = "学号不能为空")
        private String studentNo;

        @NotBlank(message = "专业不能为空")
        private String major;

        @NotBlank(message = "年级不能为空")
        private String grade;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "邮箱不能为空")
        private String email;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private User user;
    }

    @Data
    public static class ProfileUpdateRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;

        private String major;
        private String grade;
        private String avatarUrl;
    }

    @Data
    public static class PasswordChangeRequest {
        @NotBlank(message = "请输入原密码")
        private String oldPassword;

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
        private String newPassword;
    }
}
