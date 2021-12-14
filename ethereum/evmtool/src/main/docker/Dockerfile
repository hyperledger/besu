
FROM ubuntu:20.04
ARG VERSION="dev"

RUN apt-get update && \
 apt-get install --no-install-recommends -q --assume-yes openjdk-17-jre-headless=17* && \
 apt-get clean  && \
 rm -rf /var/lib/apt/lists/*  && \
 adduser --disabled-password --gecos "" --home /opt/besu besu && \
    chown besu:besu /opt/besu

USER besu
WORKDIR /opt/besu-evmtool

COPY --chown=besu:besu besu-evmtool /opt/besu-evmtool/

ENV PATH="/opt/besu-evmtool/bin:${PATH}"
ENTRYPOINT ["evm"]

# Build-time metadata as defined at http://label-schema.org
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Besu EVMTool" \
      org.label-schema.description="EVM Execution Tool" \
      org.label-schema.url="https://besu.hyperledger.org/" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/hyperledger/besu.git" \
      org.label-schema.vendor="Hyperledger" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"