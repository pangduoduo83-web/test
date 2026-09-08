package com.example.ioedunew.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 技能评估、项目进度、管理端用户操作等零散请求模型。
 */
public class MiscDtos {

    @Data
    public static class SkillAssessRequest {
        /** 技能名 -> 分数(0-100) */
        @NotNull(message = "评估结果不能为空")
        private Map<String, Integer> scores;
    }

    @Data
    public static class ProgressUpdateRequest {
        @Min(0)
        @Max(100)
        private int progress;

        private String currentTask;
    }

    @Data
    public static class UserAdminUpdateRequest {
        private Boolean enabled;
        /** STUDENT / ADMIN */
        private String role;
    }

    @Data
    public static class DiscussionPostRequest {
        @NotNull(message = "讨论内容不能为空")
        private String content;

        /** 回复的主题帖 id,发主题帖时为空 */
        private Long parentId;

        /** 小程序端 wx.login 临时凭证,用于微信内容安全检测;网页端为空 */
        private String wxCode;
    }

    @Data
    public static class DiscussionReportRequest {
        /** 举报理由,选填 */
        private String reason;
    }

    @Data
    public static class SubmissionRequest {
        @NotNull(message = "成果说明不能为空")
        private String content;

        /** 成果截图地址,可为空 */
        private String attachmentUrl;

        /** 对应考核项名称;项目未设置考核项时留空 */
        private String assessmentName;
    }

    @Data
    public static class GradeRequest {
        @Min(0)
        @Max(100)
        private int score;

        private String feedback;

        /** 教师确认的技能证据(通常由 AI 预评审预填、教师可改),为空则只按项目技能要求计入 */
        private List<SkillEvidenceItem> skillEvidence;
    }

    @Data
    public static class SkillEvidenceItem {
        private String name;

        /** 该维度在本次成果中体现出的水平 0-100 */
        private Integer level;
    }

    @Data
    public static class SkillDimensionRequest {
        @Size(max = 30, message = "技能维度名称不能超过 30 字")
        private String name;

        @Size(max = 200, message = "维度说明不能超过 200 字")
        private String description;

        private Integer sortOrder;
        private Boolean enabled;
    }
}
