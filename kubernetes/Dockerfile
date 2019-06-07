# extract image stage
# extractin here reduces the number of layers in the final image
FROM alpine:3.9 AS extract-stage
# Copy Pantheon binaries from previous jenkins artefact step
# or from the result of ./gradlew distTar
# and lett ADD unpack them
ADD pantheon-*.tar.gz /tmp/

# Run image stage
# Use openJDK JRE only for running pantheon
FROM openjdk:11.0.2-jre-slim-stretch
# Copy extracted binaries from the previous step image
COPY --from=extract-stage /tmp/pantheon* /opt/pantheon
WORKDIR /opt/pantheon
# Expose services ports
# 8545 HTTP JSON-RPC
# 8546 WS JSON-RPC
# 8547 HTTP GraphQL
# 30303 P2P
EXPOSE 8545 8546 8547 30303
ENTRYPOINT ["/opt/pantheon/bin/pantheon"]
# Build-time metadata as defined at http://label-schema.org
# Use the build_image.sh script in the kubernetes directory of this project to
# easily build this image or as an example of how to inject build parameters.
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Pantheon" \
      org.label-schema.description="Enterprise Ethereum client" \
      org.label-schema.url="https://docs.pantheon.pegasys.tech/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/PegaSysEng/pantheon.git" \
      org.label-schema.vendor="Pegasys" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"