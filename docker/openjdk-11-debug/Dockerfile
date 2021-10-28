
FROM openjdk:11-slim-buster

ARG VERSION="dev"
RUN apt update
RUN apt install -q --assume-yes curl wget jq procps net-tools
RUN adduser --disabled-password --gecos "" --home /opt/besu besu && \
    chown besu:besu /opt/besu

USER besu
WORKDIR /opt/besu

COPY --chown=besu:besu besu /opt/besu/

# Expose services ports
# 8000 JDWP for attaching remote debuggers
# 8545 HTTP JSON-RPC
# 8546 WS JSON-RPC
# 8547 HTTP GraphQL
# 9545 Metrics
# 30303 P2P
EXPOSE 5005 8545 8546 8547 9545 9999 30303

# defaults for host interfaces
ENV BESU_RPC_HTTP_HOST=0.0.0.0
ENV BESU_RPC_WS_HOST=0.0.0.0
ENV BESU_GRAPHQL_HTTP_HOST=0.0.0.0
ENV BESU_METRICS_HOST=0.0.0.0
ENV BESU_PID_PATH="/tmp/pid"

#debug options for maximum observability
ENV BESU_LOGGING="DEBUG"
ENV BESU_RPC_HTTP_API="ETH,NET,TRACE,DEBUG,ADMIN,TXPOOL"
ENV BESU_METRICS_CATEGORY="BLOCKCHAIN,ETHEREUM,EXECUTORS,JVM,NETWORK,PEERS,PERMISSIONING,PROCESS,PRUNER,RPC,SYNCHRONIZER,TRANSACTION_POOL,KVSTORE_ROCKSDB,KVSTORE_PRIVATE_ROCKSDB,KVSTORE_ROCKSDB_STATS,KVSTORE_PRIVATE_ROCKSDB_STATS"
ENV JDWP_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ENV JAVA_NATIVE_MEM_OPTS="-XX:NativeMemoryTracking=detail -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics"
ENV JMX_OPTS="-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
ENV JAVA_OPTS="${JDWP_OPTS} ${JAVA_NATIVE_MEM_OPTS} ${JMX_OPTS}"

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
