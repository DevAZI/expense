# syntax=docker/dockerfile:1

# ---- build stage -----------------------------------------------------------
# Gradle で bootJar を作る。テストは実データCSVに依存するためイメージビルド時は
# 走らせない（-x test）。CI 側で ./gradlew test を回す前提。
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 依存解決を先にキャッシュさせるため、まずビルドスクリプトだけコピー
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# ソースを入れて bootJar
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- run stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# H2 のファイルDBは ./data 配下（application.yml: jdbc:h2:file:./data/expense）。
# ここを volume でマウントして永続化する。
RUN mkdir -p /app/data

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
