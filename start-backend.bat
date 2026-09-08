@echo off
rem 启动后端(本地开发,激活 dev profile:内置开发 JWT 密钥 + 演示数据 + admin@ioedu.cn/admin123)
rem 使用 IntelliJ 自带 Maven;如已自装 Maven 可直接 mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd /d %~dp0
set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
%MVN% spring-boot:run -Dspring-boot.run.profiles=dev
