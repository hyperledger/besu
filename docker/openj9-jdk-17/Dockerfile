
FROM ubuntu:22.04
ARG VERSION="dev"
ARG OPENJ9_RPM="https://github.com/ibmruntimes/semeru17-binaries/releases/download/jdk-17.0.6%2B10_openj9-0.36.0/ibm-semeru-open-17-jdk-17.0.6.10_0.36.0-1.__ARCH__.rpm"
ENV OPENJ9_RPM_ENVAR=$OPENJ9_RPM

RUN apt-get update  && \
     apt-get install --no-install-recommends -q --assume-yes ca-certificates curl=7* wget=1.21* jq=1.6* net-tools=1.60* libjemalloc-dev=5.* alien && \
     apt-get clean  && \
     rm -rf /var/lib/apt/lists/*  && \
     adduser --disabled-password --gecos "" --home /opt/besu besu && \
        chown besu:besu /opt/besu && \
        chmod 0755 /opt/besu

SHELL ["/bin/bash", "-c"]
RUN export OPENJ9_URL=${OPENJ9_RPM_ENVAR/"__ARCH__"/`uname -m`} && \
    echo "Fetching $OPENJ9_URL" && \
    wget $OPENJ9_URL -O openj9-jdk17.rpm && \
    echo "Installing OpenJ9 JDK" && \
    alien -i openj9-jdk17.rpm && \
    update-alternatives --install /usr/bin/java java /lib/jvm/ibm-semeru-open-17-jdk/bin/java 1 && \
    update-alternatives --install /usr/bin/javac javac /lib/jvm/ibm-semeru-open-17-jdk/bin/javac 1 && \
    update-alternatives --install /usr/bin/jshell jshell /lib/jvm/ibm-semeru-open-17-jdk/bin/jshell 1 && \
    update-alternatives --install /usr/bin/jps jps /lib/jvm/ibm-semeru-open-17-jdk/bin/jps 1 && \
    update-alternatives --install /usr/bin/jcmd jcmd /lib/jvm/ibm-semeru-open-17-jdk/bin/jcmd 1 && \
    update-alternatives --install /usr/bin/jar jar /lib/jvm/ibm-semeru-open-17-jdk/bin/jar 1 && \
    rm -f openj9-jdk17.rpm

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
