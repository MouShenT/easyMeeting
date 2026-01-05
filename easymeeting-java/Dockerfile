# ==================== 后端 Dockerfile ====================
# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-23 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:23-jre

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r easymeeting && useradd -r -g easymeeting easymeeting

# 从构建阶段复制 jar 文件
COPY --from=builder /app/target/easymeeting-1.0.jar app.jar

# 创建日志和文件存储目录
RUN mkdir -p /app/logs /app/files && \
    chown -R easymeeting:easymeeting /app

# 切换到非 root 用户
USER easymeeting

# 暴露端口：HTTP(6060) + WebSocket(6061)
EXPOSE 6060 6061

# JVM 优化参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

# 启动命令（使用 docker profile）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
