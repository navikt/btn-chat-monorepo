FROM gradle:5.3.1-jdk11-slim AS builder
RUN gradle --version && java -version
WORKDIR /home/gradle/src
COPY build.gradle.kts settings.gradle.kts /home/gradle/src/
COPY btn-chat-fss/build.gradle.kts btn-chat-fss/settings.gradle.kts /home/gradle/src/btn-chat-fss/
COPY btn-chat-sbs/build.gradle.kts btn-chat-sbs/settings.gradle.kts /home/gradle/src/btn-chat-sbs/

RUN gradle clean build --no-daemon > /dev/null 2>&1 || true

COPY --chown=gradle:gradle . /home/gradle/src
RUN gradle clean build --no-daemon

FROM navikt/java:11

ARG APPNAMEPATH
ENV APPNAMEPATH $APPNAMEPATH

COPY --from=builder /home/gradle/src/$APPNAMEPATH/build/libs/*.jar ./
