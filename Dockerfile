# ---------- 构建阶段:Maven + JDK8 ----------
FROM maven:3.8.6-openjdk-8 AS build
WORKDIR /build

# 先拷贝 pom 拉依赖,利用镜像层缓存加速后续构建
COPY pom.xml .
RUN mvn -q dependency:go-offline || true

COPY src ./src
RUN mvn -q package -Dmaven.test.skip=true

# ---------- 运行阶段:JRE8 ----------
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
