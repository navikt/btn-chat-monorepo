# Used for building apps on github.
# Not using gradle-builder image as docker-layer-caching is not setup yet.
FROM navikt/java:11

ARG APPNAMEPATH
ENV APPNAMEPATH $APPNAMEPATH

COPY $APPNAMEPATH/build/libs/*.jar ./
