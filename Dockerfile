FROM eclipse-temurin:17-jdk AS builder
COPY . .
RUN ./gradlew --no-daemon build

FROM eclipse-temurin:17-jre
WORKDIR /data
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:-UseBASE64Intrinsics"
COPY --from=builder build/libs/fint-flyt-altinn-gateway-*.jar /data/app.jar

USER 1337
ENTRYPOINT ["java", "-jar"]
CMD ["/data/app.jar"]
