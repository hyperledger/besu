
FROM ubuntu:20.04

ARG VERSION="dev"
RUN apt-get update  && \
 apt-get install --no-install-recommends -q --assume-yes curl=7* wget=1.20* jq=1.6* net-tools=1.60* openjdk-11-jdk-headless=11* libjemalloc-dev=5.* && \
 apt-get clean  && \
 rm -rf /var/lib/apt/lists/*  && \
 adduser --disabled-password --gecos "" --home /opt/besu besu && \
    chown besu:besu /opt/besu

USER besu
WORKDIR /opt/besu

COPY --chown=besu:besu besu /opt/besu/

# Expose services ports
# 5005 JDWP for attaching remote debuggers
# 8545 HTTP JSON-RPC
# 8546 WS JSON-RPC
# 8547 HTTP GraphQL
# 8550 HTTP ENGINE JSON-RPC
# 8551 WS ENGINE JSON-RPC
# 9545 Metrics
# 1098 JMX / JMX-RMI
# 30303 P2P
EXPOSE 5005 8545 8546 8547 8550 8551 9545 1098 30303

# defaults for host interfaces
ENV BESU_RPC_HTTP_HOST 0.0.0.0
ENV BESU_RPC_WS_HOST 0.0.0.0
ENV BESU_GRAPHQL_HTTP_HOST 0.0.0.0
ENV BESU_METRICS_HOST 0.0.0.0
ENV BESU_JMX_HOST 0.0.0.0
ENV BESU_PID_PATH "/tmp/pid"
ENV BESU_HOST_ALLOWLIST "*"

#debug options for maximum observability
ENV BESU_LOGGING "INFO"
ENV BESU_RPC_HTTP_API  "ETH,NET,TRACE,DEBUG,ADMIN,TXPOOL"
ENV JDWP_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ENV JAVA_OPTS "${JDWP_OPTS} "

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
