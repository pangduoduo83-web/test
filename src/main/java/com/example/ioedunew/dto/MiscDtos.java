package com.example.ioedunew.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
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
    }
}
