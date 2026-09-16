# Stage 1: Spring Boot layertools 提取（分层镜像缓存优化）
# 注意: docker-compose build context 须为 ./auth-center（非 server 子目录）
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /build
COPY auth-center-server/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2: 最终运行镜像
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="auth-center"
RUN addgroup -g 1001 appuser && adduser -D -u 1001 -G appuser appuser
WORKDIR /app
# 按变更频率从低到高拷贝,最大化层缓存命中
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./
# 嵌入管理控制台 SPA 构建产物（本地预构建,无需 Docker 内 npm）
COPY auth-center-admin/dist/ ./BOOT-INF/classes/static/admin/
USER appuser
EXPOSE 8090
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher"]
