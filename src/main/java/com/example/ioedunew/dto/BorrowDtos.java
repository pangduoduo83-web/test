package com.example.ioedunew.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 借阅相关请求模型。
 */
public class BorrowDtos {

    @Data
    public static class ApplyRequest {
        @NotNull(message = "设备不能为空")
        private Long equipmentId;

        @Min(value = 1, message = "数量至少为 1")
        private int quantity = 1;

        @NotBlank(message = "请选择使用目的")
        private String purpose;

        private String projectName;

        @NotNull(message = "请选择开始日期")
        private LocalDate startDate;

        @Min(value = 1, message = "借用时长不合法")
        private int durationDays = 14;

        private String remark;
    }

    @Data
    public static class DecisionRequest {
        /** approve / reject */
        @NotBlank(message = "决策动作不能为空")
        private String action;

        /** 拒绝原因,action=reject 时必填 */
        private String reason;
    }
}
