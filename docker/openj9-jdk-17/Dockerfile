FROM ubuntu:22.04

ARG VERSION="dev"
ARG TARGETPLATFORM

RUN apt-get update && \
 apt-get install --no-install-recommends -q --assume-yes curl=7* libjemalloc-dev=5.* && \
 apt-get clean  && \
 rm -rf /var/lib/apt/lists/*

RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "aarch64" ]; then \
        curl -kL -o jdk-17.tar.gz https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.9%2B9_openj9-0.41.0/ibm-semeru-open-jre_aarch64_linux_17.0.9_9_openj9-0.41.0.tar.gz ; \
    elif [ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ]; then \
        curl -kL -o jdk-17.tar.gz https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.9%2B9_openj9-0.41.0/ibm-semeru-open-jre_aarch64_mac_17.0.9_9_openj9-0.41.0.tar.gz ; \
    elif [ "$ARCH" = "x86_64" ]; then \
        curl -kL -o jdk-17.tar.gz https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.9%2B9_openj9-0.41.0/ibm-semeru-open-jre_x64_linux_17.0.9_9_openj9-0.41.0.tar.gz ; \
    else \
        echo "Unsupported platform: $ARCH"; exit 1; \
    fi

RUN tar -xzf jdk-17.tar.gz && \
    rm jdk-17.tar.gz && \
    mv jdk-17.0.9+9-jre /usr/bin/ && \
    update-alternatives --install "/usr/bin/java" "java" "/usr/bin/jdk-17.0.9+9-jre/bin/java" 1 && \
    adduser --disabled-password --gecos "" --home /opt/besu besu && \
    chown besu:besu /opt/besu && \
    chmod 0755 /opt/besu

ENV JAVA_HOME /usr/bin/jdk-17.0.9+9-jre/
RUN export JAVA_HOME

USER besu
WORKDIR /opt/besu

COPY --chown=besu:besu besu /opt/besu/

# Expose services ports
# 8545 HTTP JSON-RPC
# 8546 WS JSON-RPC
# 8547 HTTP GraphQL
# 8550 HTTP ENGINE JSON-RPC
# 8551 WS ENGINE JSON-RPC
# 30303 P2P
EXPOSE 8545 8546 8547 8550 8551 30303

# defaults for host interfaces
ENV BESU_RPC_HTTP_HOST 0.0.0.0
ENV BESU_RPC_WS_HOST 0.0.0.0
ENV BESU_GRAPHQL_HTTP_HOST 0.0.0.0
ENV BESU_PID_PATH "/tmp/pid"

ENV OTEL_RESOURCE_ATTRIBUTES="service.name=besu,service.version=$VERSION"

ENV OLDPATH="${PATH}"
ENV PATH="/opt/besu/bin:${OLDPATH}"

ENTRYPOINT ["besu"]
HEALTHCHECK --start-period=5s --interval=5s --timeout=1s --retries=10 CMD bash -c "[ -f /tmp/pid ]"

# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Besu" \
      org.label-schema.description="Enterprise Ethereum client" \
      org.label-schema.url="https://besu.hyperledger.org/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/hyperledger/besu.git" \
      org.label-schema.vendor="Hyperledger" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"
