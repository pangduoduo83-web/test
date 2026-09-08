package com.example.ioedunew.tenant;

import lombok.Data;

import java.time.LocalDateTime;

/** 租户注册表记录(平台库 tenants 表) */
@Data
public class Tenant {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    private Long id;
    /** 子域名前缀,也用于日志与 JWT 的 tid 声明 */
    private String code;
    private String name;
    /** 该租户独占的 MySQL 库名 */
    private String dbName;
    /** 客户自有域名(可空),优先于子域名匹配 */
    private String customDomain;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
