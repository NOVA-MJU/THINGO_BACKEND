# 1단계: 의존성 캐시 (build.gradle 미변경 시 재사용)
FROM gradle:8.8-jdk17 AS deps
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle build.gradle ./
COPY --chown=gradle:gradle gradle ./gradle
RUN gradle dependencies --no-daemon || true

# 2단계: 빌드 (소스만 변경 시 의존성 레이어 캐시 적중)
FROM gradle:8.8-jdk17 AS build
WORKDIR /home/gradle/project
COPY --from=deps /home/gradle/.gradle /home/gradle/.gradle
COPY --chown=gradle:gradle . .
RUN gradle build -x test --no-daemon

# 3단계: 실행 (JRE만 사용해 런타임 이미지 축소)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
