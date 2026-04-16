FROM gradle:8.14.4-jdk17-alpine AS builder
USER root
COPY . .
RUN gradle --no-daemon build

FROM gcr.io/distroless/java17
ENV JAVA_TOOL_OPTIONS=-XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-flyt-altinn-gateway-*.jar /data/app.jar
CMD ["/data/app.jar"]
