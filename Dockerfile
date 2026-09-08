# ---------- 构建阶段:Maven + JDK8 ----------
FROM maven:3.8.6-openjdk-8 AS build
WORKDIR /build

# 先拷贝 pom 拉依赖,利用镜像层缓存加速后续构建
COPY pom.xml .
RUN mvn -q dependency:go-offline || true

COPY src ./src
RUN mvn -q package -Dmaven.test.skip=true

# ---------- 运行阶段:JRE8(8u191+ 才支持容器内存感知) ----------
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

ENV TZ=Asia/Shanghai
# 堆上限按容器内存的 75% 自动计算;compose 里可用 JAVA_OPTS 追加/覆盖
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health || wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
