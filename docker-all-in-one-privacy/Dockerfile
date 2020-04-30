ARG BESU_VERSION=1.4.3
ARG ORION_VERSION=1.5.1

FROM hyperledger/besu:$BESU_VERSION AS besu
FROM pegasyseng/orion:1.5.1 AS orion

COPY --from=besu /opt/besu/ /opt/besu/

RUN mkdir /config/
RUN mkdir /config/orion/
RUN mkdir /config/besu/
RUN mkdir /opt/besu/public-keys/

ADD ./log-config.xml /config/log-config.xml

ADD ./orion.conf /config/orion/orion.conf

###
### A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=
###
ADD ./nodeKey.pub /config/orion/nodeKey.pub

###
### {"data":{"bytes":"hBsuQsGJzx4QHmFmBkNoI7YGnTmaZP4P+wBOdu56ljk="},"type":"unlocked"}
###
ADD ./nodeKey.key /config/orion/nodeKey.key

ADD ./bootnode_start.sh /opt/besu/bootnode_start.sh


###
### 0xfd5a230bc6b636cef22ed8ddee3810ae67dd8c6c8145b7b56749522459a38bc1
###
ADD ./key /opt/besu/keys/key

###
### 0x05e2aab6df08db103fd75c4fb2b8582fe43eebce6a0f077b590a5e7f44ed081e498fa2c57788372d7bc0c41a34394f34c5c11332f4473a1bdf83589316edc2c4
###
ADD ./key.pub /opt/besu/keys/key.pub


RUN apt-get update && apt-get install -y supervisor
RUN mkdir -p /var/log/supervisor
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 9001

ENTRYPOINT ["/usr/bin/supervisord"]
CMD ["--nodaemon"]

# ENTRYPOINT [ \
#   # launch orion first
#   "/opt/orion/bin/orion", \
#   "/config/orion/orion.conf", \
#   ";", \
#   # "sleep 5;", \
#   "/opt/besu/bootnode_start.sh", \
#   # besu bootnode
#   "--network=dev", \
#   "--revert-reason-enabled=true", \
#   "--rpc-http-enabled", \
#   "--rpc-http-host=0.0.0.0", \
#   "--rpc-http-port=8545", \
#   "--rpc-http-cors-origins=*", \
#   "--rpc-http-api=EEA,WEB3,ETH,NET,PRIV", \
#   "--rpc-ws-enabled", \
#   "--rpc-ws-host=0.0.0.0", \
#   "--rpc-ws-port=8546", \
#   "--host-whitelist=*", \
#   "--metrics-enabled", \
#   "--metrics-host=0.0.0.0", \
#   "--metrics-port=9545", \
#   "--min-gas-price=0", \
#   # Besu miner node
#   "--miner-enabled", \
#   "--miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", \
#   # besu validator node
#   "--node-private-key-file=/opt/besu/keys/key", \
#   "--privacy-enabled=true", \
#   "--privacy-url=http://localhost:8888", \
#   "--privacy-public-key-file=/config/orion/nodeKey.pub" \
# ]
