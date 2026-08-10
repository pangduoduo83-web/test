@echo off
rem 启动后端(使用 IntelliJ 自带 Maven;如已自装 Maven 可直接 mvn spring-boot:run)
cd /d %~dp0
set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
%MVN% spring-boot:run
