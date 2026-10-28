# Reproducible Linux build/server-test environment; graphical clients run natively or in CI with Xvfb.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon build

FROM scratch AS artifacts
COPY --from=build /workspace/build/libs/ /
