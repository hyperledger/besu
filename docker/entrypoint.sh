#!/bin/bash

set -e

if ! { [ "${1#-}" != "$1" ] || [ "$#" == 0 ] || [ "$1" == "blocks" ] || [ "$1" == "public-key" ] || [ "$1" == "password" ]; }; then
    exec "$@"
fi

p2plistenset=false
rpclistenset=false
wslistenset=false

for i in "$@"; do
    case "$i" in
        --rpc-http-host) rpclistenset=true ;;
        --rpc-http-host=*) rpclistenset=true ;;
        --rpc-http-port) rpclistenset=true ;;
        --rpc-http-port=*) rpclistenset=true ;;
        --rpc-ws-host) wslistenset=true ;;
        --rpc-ws-host=*) wslistenset=true ;;
        --rpc-ws-port) wslistenset=true ;;
        --rpc-ws-port=*) wslistenset=true ;;
        --p2p-host) p2plistenset=true ;;
        --p2p-host=*) p2plistenset=true ;;
        --p2p-port) p2plistenset=true ;;
        --p2p-port=*) p2plistenset=true ;;
    esac
done

if $p2plistenset ; then
    echo "ERROR: p2p host and port cannot be set by argument under docker, define your custom port by mapping it into the containers 30303 port"
    exit 1
else
    set -- "--p2p-host=0.0.0.0" "$@"
    set -- "--p2p-port=30303" "$@"
fi

if $rpclistenset ; then
    echo "ERROR: rpc http host and port cannot be set by argument under docker, define your custom port by mapping it into the containers 8545 port"
    exit 1
else
    set -- "--rpc-http-host=0.0.0.0" "$@"
    set -- "--rpc-http-port=8545" "$@"
fi

if $wslistenset ; then
    echo "ERROR: rpc ws host and port cannot be set by argument under docker, define your custom port by mapping it into the containers 8546 port"
    exit 1
else
    set -- "--rpc-ws-host=0.0.0.0" "$@"
    set -- "--rpc-ws-port=8546" "$@"
fi

if [ "$P2P_ENABLED" == "false" ] || [ "$P2P_ENABLED" == "0" ]; then
    set -- "--p2p-enabled=false" "$@"
fi

if [ "$DISCOVERY_ENABLED" == "false" ] || [ "$DISCOVERY_ENABLED" == "0" ]; then
    set -- "--discovery-enabled=false" "$@"
fi

if [[ ! -z "$BOOTNODES" ]]; then
    set -- "--bootnodes=$BOOTNODES" "$@"
fi

if [[ ! -z "$MAX_PEERS" ]]; then
    set -- "$@" "--max-peers=$MAX_PEERS"
fi

if [[ ! -z "$BANNED_NODE_IDS" ]]; then
    set -- "--banned-node-ids=$BANNED_NODE_IDS" "$@"
fi

if [[ ! -z "$BANNED_NODE_ID" ]]; then
    set -- "--banned-node-id=$BANNED_NODE_ID" "$@"
fi

if [[ ! -z "$SYNC_MODE" ]]; then
    set -- "--sync-mode=$SYNC_MODE" "$@"
fi

if [[ ! -z "$NETWORK" ]]; then
    set -- "--network=$NETWORK" "$@"
fi

if [[ ! -z "$NETWORK_ID" ]]; then
    set -- "--network-id=$NETWORK_ID" "$@"
fi

if [ "$RPC_HTTP_ENABLED" == "true" ] || [ "$RPC_HTTP_ENABLED" == "1" ]; then
    set -- "--rpc-http-enabled=true" "$@"
fi

if [[ ! -z "$RPC_HTTP_CORS_ORIGINS" ]]; then
    set -- "--rpc-http-cors-origins=$RPC_HTTP_CORS_ORIGINS" "$@"
fi

if [[ ! -z "$RPC_HTTP_API" ]]; then
    set -- "--rpc-http-api=$RPC_HTTP_API" "$@"
fi

if [[ ! -z "$RPC_HTTP_APIS" ]]; then
    set -- "--rpc-http-apis=$RPC_HTTP_APIS" "$@"
fi

if [ "$RPC_WS_ENABLED" == "true" ] || [ "$RPC_WS_ENABLED" == "1" ]; then
    set -- "--rpc-ws-enabled=true" "$@"
fi

if [[ ! -z "$RPC_WS_API" ]]; then
    set -- "--rpc-ws-api=$RPC_WS_API" "$@"
fi

if [[ ! -z "$RPC_WS_APIS" ]]; then
    set -- "--rpc-ws-apis=$RPC_WS_APIS" "$@"
fi

if [[ ! -z "$RPC_WS_REFRESH_DELAY" ]]; then
    set -- "--rpc-ws-refresh_delay=$RPC_WS_REFRESH_DELAY" "$@"
fi

if [ "$METRICS_ENABLED" == "true" ] || [ "$METRICS_ENABLED" == "1" ]; then
    set -- "--metrics-enabled=true" "$@"
fi

if [[ ! -z "$METRICS_HOST" ]]; then
    set -- "--metrics-host=$METRICS_HOST" "$@"
fi

if [[ ! -z "$METRICS_PORT" ]]; then
    set -- "--metrics-port=$METRICS_PORT" "$@"
fi

if [ "$METRICS_PUSH_ENABLED" == "true" ] || [ "$METRICS_PUSH_ENABLED" == "1" ]; then
    set -- "--metrics-push-enabled=true" "$@"
fi

if [[ ! -z "$METRICS_PUSH_INTERVAL" ]]; then
    set -- "--metrics-push-interval=$METRICS_PUSH_INTERVAL" "$@"
fi

if [[ ! -z "$METRICS_PUSH_HOST" ]]; then
    set -- "--metrics-push-host=$METRICS_PUSH_HOST" "$@"
fi

if [[ ! -z "$METRICS_PUSH_PORT" ]]; then
    set -- "--metrics-push-port=$METRICS_PUSH_PORT" "$@"
fi


if [[ ! -z "$METRICS_PUSH_PROMETHEUS_JOB" ]]; then
    set -- "--metrics-push-prometheus-job=$METRICS_PUSH_PROMETHEUS_JOB" "$@"
fi

if [[ ! -z "$HOST_WHITELIST" ]]; then
    set -- "--host-whitelist=$HOST_WHITELIST" "$@"
fi

if [[ ! -z "$LOGGING" ]]; then
    set -- "--logging=$LOGGING" "$@"
fi

if [ "$MINER_ENABLED" == "true" ] || [ "$MINER_ENABLED" == "1" ]; then
    set -- "--miner-enabled=true" "$@"
fi

if [[ ! -z "$MINER_COINBASE" ]]; then
    set -- "--miner-coinbase=$MINER_COINBASE" "$@"
fi

if [[ ! -z "$MIN_GAS_PRICE" ]]; then
    set -- "--min-gas-price=$MIN_GAS_PRICE" "$@"
fi

if [[ ! -z "$MINER_EXTRA_DATA" ]]; then
    set -- "--miner-extra-data=$MINER_EXTRA_DATA" "$@"
fi

if [ "$PERMISSIONS_NODES_ENABLED" == "true" ] || [ "$PERMISSIONS_NODES_ENABLED" == "1" ]; then
    set -- "--permissions_nodes_enabled=true" "$@"
fi

if [ "$PERMISSIONS_ACCOUNTS_ENABLED" == "true" ] || [ "$PERMISSIONS_ACCOUNTS_ENABLED" == "1" ]; then
    set -- "--permissions_accounts_enabled=true" "$@"
fi

if [ "$PRIVACY_ENABLED" == "true" ] || [ "$PRIVACY_ENABLED" == "1" ]; then
    set -- "--privacy-enabled=true" "$@"
fi

if [[ ! -z "$PRIVACY_URL" ]]; then
    set -- "--privacy-url=$PRIVACY_URL" "$@"
fi

if [[ ! -z "$PRIVACY_PUBLIC_KEY_FILE" ]]; then
    set -- "--privacy-public-key-file=$PRIVACY_PUBLIC_KEY_FILE" "$@"
fi

if [[ ! -z "$PRIVACY_PRECOMPILED_ADDRESS" ]]; then
    set -- "--privacy-precompiled-address=$PRIVACY_PRECOMPILED_ADDRESS" "$@"
fi

set -- "/opt/pantheon/bin/pantheon" "$@"

exec "$@"
