# syntax=docker/dockerfile:1.7
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy
ARG APP_PORT=9081

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -Plocal -pl macula-cloud-system -am clean package -DskipTests \
    && jar_file=$(find macula-cloud-system/target -maxdepth 1 -type f -name '*.jar' \
         ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' | head -n 1) \
    && test -n "$jar_file" \
    && cp "$jar_file" /tmp/application.jar

FROM ${RUNTIME_IMAGE} AS runtime
ARG APP_PORT
RUN groupadd --system --gid 10001 macula \
    && useradd --system --uid 10001 --gid macula --home-dir /opt/macula --create-home macula
WORKDIR /opt/macula
COPY --from=build --chown=macula:macula /tmp/application.jar application.jar
USER 10001:10001
ENV SERVER_PORT=${APP_PORT} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE ${APP_PORT}
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /opt/macula/application.jar"]
