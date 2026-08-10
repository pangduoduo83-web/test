package com.example.ioedunew.dto;

import lombok.Data;

/**
 * 教师端请求模型。
 */
public class TeacherDtos {

    /** 更新教学资源:resources 为 JSON 数组文本 [{type,name,url}] */
    @Data
    public static class ResourcesUpdateRequest {
        private String resources;
    }

    /** 更新项目封面 */
    @Data
    public static class CoverUpdateRequest {
        private String coverUrl;
    }
}
