FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace
COPY --chmod=0755 gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM gcr.io/distroless/java17-debian13:nonroot AS runtime

WORKDIR /app
COPY --from=build --chown=65532:65532 /workspace/build/libs/payment-gateway.jar ./payment-gateway.jar
USER 65532:65532
EXPOSE 8090
CMD ["/app/payment-gateway.jar"]
