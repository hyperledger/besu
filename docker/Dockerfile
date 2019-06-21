FROM openjdk:11.0.2-jre-slim-stretch

COPY pantheon-*.tar.gz /tmp/.
RUN tar xzf /tmp/pantheon-*.tar.gz -C /tmp && \
  rm /tmp/pantheon-*.tar.gz && \
  mv /tmp/pantheon-* /opt/pantheon


RUN mkdir /var/lib/pantheon
RUN mkdir /etc/pantheon/
COPY entrypoint.sh /opt/pantheon/pantheon-entrypoint.sh
RUN chmod +x /opt/pantheon/pantheon-entrypoint.sh

WORKDIR /var/lib/pantheon
VOLUME ["/var/lib/pantheon"]

EXPOSE 8545 8546 30303

ENV PANTHEON_OPTS="-Dpantheon.docker=true"

ENTRYPOINT ["/opt/pantheon/pantheon-entrypoint.sh"]
