
FROM openjdk:11.0.2-jre-slim-stretch

COPY pantheon /opt/pantheon/
WORKDIR /opt/pantheon

# Expose services ports
# 8545 HTTP JSON-RPC
# 8546 WS JSON-RPC
# 8547 HTTP GraphQL
# 30303 P2P
EXPOSE 8545 8546 8547 30303

# defaults for host interfaces
ENV PANTHEON_RPC_HTTP_HOST 0.0.0.0
ENV PANTHEON_RPC_WS_HOST 0.0.0.0
ENV PANTHEON_GRAPHQL_HTTP_HOST 0.0.0.0

ENTRYPOINT ["/opt/pantheon/bin/pantheon"]

# Build-time metadata as defined at http://label-schema.org
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