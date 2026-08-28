package com.example.ioedunew.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 管理端用户、报名与通知请求模型。
 */
public class AdminDtos {

    @Data
    public static class UserCreateRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度须为 6-72 位")
        private String password;

        private String phone;
        private String studentNo;
        private String major;
        private String grade;
        private String avatarUrl;
        private String role;
        private Boolean enabled;
    }

    @Data
    public static class UserUpdateRequest {
        private String name;

        @Email(message = "邮箱格式不正确")
        private String email;

        private String phone;
        private String studentNo;
        private String major;
        private String grade;
        private String avatarUrl;
        private String role;
        private Boolean enabled;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度须为 6-72 位")
        private String password;
    }

    @Data
    public static class EnrollmentCreateRequest {
        @NotNull(message = "用户不能为空")
        private Long userId;

        @NotNull(message = "项目不能为空")
        private Long projectId;
    }

    @Data
    public static class EnrollmentUpdateRequest {
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        private Integer progress;

        private String currentTask;
        private LocalDate deadline;
    }

    @Data
    public static class NotificationCreateRequest {
        private Long userId;
        private String role;

        @NotBlank(message = "通知标题不能为空")
        @Size(max = 100, message = "通知标题不能超过 100 字")
        private String title;

        @Size(max = 300, message = "通知内容不能超过 300 字")
        private String content;

        @NotBlank(message = "通知类型不能为空")
        @Size(max = 20, message = "通知类型不能超过 20 字")
        private String type;
    }
}
