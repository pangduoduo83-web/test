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
        @Size(max = 50, message = "字段长度超过上限 50")
        private String name;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "字段长度超过上限 100")
        private String email;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度须为 6-72 位")
        private String password;

        @Size(max = 20, message = "字段长度超过上限 20")
        private String phone;
        @Size(max = 30, message = "字段长度超过上限 30")
        private String studentNo;
        @Size(max = 30, message = "字段长度超过上限 30")
        private String teacherNo;
        @Size(max = 50, message = "字段长度超过上限 50")
        private String major;
        @Size(max = 20, message = "字段长度超过上限 20")
        private String grade;
        @Size(max = 255, message = "字段长度超过上限 255")
        private String avatarUrl;
        @Size(max = 20, message = "字段长度超过上限 20")
        private String role;
        private Boolean enabled;
    }

    @Data
    public static class UserUpdateRequest {
        @Size(max = 50, message = "字段长度超过上限 50")
        private String name;

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "字段长度超过上限 100")
        private String email;

        @Size(max = 20, message = "字段长度超过上限 20")
        private String phone;
        @Size(max = 30, message = "字段长度超过上限 30")
        private String studentNo;
        @Size(max = 30, message = "字段长度超过上限 30")
        private String teacherNo;
        @Size(max = 50, message = "字段长度超过上限 50")
        private String major;
        @Size(max = 20, message = "字段长度超过上限 20")
        private String grade;
        @Size(max = 255, message = "字段长度超过上限 255")
        private String avatarUrl;
        @Size(max = 20, message = "字段长度超过上限 20")
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
        @Size(max = 20, message = "字段长度超过上限 20")
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
