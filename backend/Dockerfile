# 배포용: Spring Boot 애플리케이션만 단독 이미지로 빌드한다.
# 외부 MySQL/Redis 접속 정보는 런타임 환경변수로 주입한다.
#   빌드: docker build -t windfarmonm .
#   실행: docker run -p 8080:8080 \
#           -e SPRING_PROFILES_ACTIVE=prod \
#           -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... \
#           -e REDIS_HOST=... -e REDIS_PORT=6379 -e REDIS_PASSWORD=... \
#           windfarmonm

# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시 레이어: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능한 jar 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 사용자로 실행
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
