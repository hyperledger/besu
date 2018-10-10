FROM openjdk:8-jdk as builder
COPY . /tmp/pantheon
WORKDIR /tmp/pantheon
RUN ./gradlew installDist -x test

FROM openjdk:8-jre

# copy application (with libraries inside)
COPY quickstart/pantheon/*_start.sh /opt/pantheon/
COPY --from=builder /tmp/pantheon/build/install/pantheon /opt/pantheon/

# List Exposed Ports
EXPOSE 8546 8545 30303

# specify default command
ENTRYPOINT ["/opt/pantheon/node_start.sh"]
