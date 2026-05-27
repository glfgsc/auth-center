FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="auth-center"
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8090
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
