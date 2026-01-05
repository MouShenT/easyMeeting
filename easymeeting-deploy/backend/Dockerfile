# 后端 Dockerfile - 使用预编译 JAR
FROM eclipse-temurin:23-jre

WORKDIR /app

RUN groupadd -r easymeeting && useradd -r -g easymeeting easymeeting

COPY app.jar app.jar

RUN mkdir -p /app/logs /app/files && \
    chown -R easymeeting:easymeeting /app

USER easymeeting

EXPOSE 6060 6061

ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
