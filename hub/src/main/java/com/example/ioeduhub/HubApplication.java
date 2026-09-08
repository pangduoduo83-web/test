package com.example.ioeduhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目商店(hub):所有客户实例共用的公共中心服务。
 * 客户实例用租户 API Key 调 /api/store/**(浏览、安装、发布),平台管理员用账号登录 /api/hub-admin/**(审核、定向分享、租户管理)。
 */
@SpringBootApplication
public class HubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApplication.class, args);
    }
}
