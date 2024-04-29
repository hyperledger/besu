
FROM ubuntu:22.04
ARG VERSION="dev"

# Clean apt cache
RUN apt-get clean
RUN rm -rf /var/cache/apt/archives/* /var/cache/apt/archives/partial/*
RUN rm -rf /var/lib/apt/lists/*

# Update and install dependencies without using any cache
RUN apt-get update -o Acquire::BrokenProxy="true" \
  -o Acquire::http::No-Cache="true" \
  -o Acquire::http::Pipeline-Depth="0"
RUN apt-get install -o Acquire::BrokenProxy="true" \
  -o Acquire::http::No-Cache="true" \
  -o Acquire::http::Pipeline-Depth="0" --no-install-recommends -q --assume-yes ca-certificates-java=20190909*
RUN apt-get install -o Acquire::BrokenProxy="true" \
  -o Acquire::http::No-Cache="true" \
  -o Acquire::http::Pipeline-Depth="0" --no-install-recommends -q --assume-yes openjdk-17-jre-headless=17*

# Creating a user for besu
RUN adduser --disabled-password --gecos "" --home /opt/besu besu
RUN chown besu:besu /opt/besu

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