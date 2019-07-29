description: Pantheon command line interface reference
<!--- END of page meta data -->

# Pantheon Command Line

This reference describes the syntax of the Pantheon Command Line Interface (CLI) options and subcommands.

```bash
pantheon [OPTIONS] [COMMAND]
```

Starts a Pantheon node.

## Specifying Options

Pantheon options can be specified: 

* On the command line 
* As an [environment variable](#pantheon-environment-variables) 
* In a [configuration file](../Configuring-Pantheon/Using-Configuration-File.md).

If an option is specified in multiple places, the order of priority is command line, environment variable, 
configuration file. 

### Pantheon Environment Variables

For each command line option, the equivalent environment variable is: 

* Upper-case
* `-` is replaced by `_` 
* Has a `PANTHEON_` prefix

For example, set `--miner-coinbase` using the `PANTHEON_MINER_COINBASE` environment variable. 

## Options

### banned-node-ids

```bash tab="Syntax"
--banned-node-ids=<bannedNodeId>[,<bannedNodeId>...]...
```

```bash tab="Command Line"
--banned-nodeids=0xc35c3...d615f,0xf42c13...fc456
```

```bash tab="Environment Variable"
PANTHEON_BANNED_NODEIDS=0xc35c3...d615f,0xf42c13...fc456
```

```bash tab="Configuration File"
banned-nodeids=["0xc35c3...d615f","0xf42c13...fc456"]
```

List of node IDs with which this node will not peer. The node ID is the public key of the node. You can specify the banned node IDs with or without the `0x` prefix.

!!!tip
    The singular `--banned-node-id` and plural `--banned-node-ids` are available and are two
    names for the same option.
 
### bootnodes

```bash tab="Syntax"
--bootnodes[=<enode://id@host:port>[,<enode://id@host:port>...]...]
```

```bash tab="Command Line"
--bootnodes=enode://c35c3...d615f@1.2.3.4:30303,enode://f42c13...fc456@1.2.3.5:30303
```

```bash tab="Environment Variable"
PANTHEON_BOOTNODES=enode://c35c3...d615f@1.2.3.4:30303,enode://f42c13...fc456@1.2.3.5:30303
```

```bash tab="Example Configuration File"
bootnodes=["enode://c35c3...d615f@1.2.3.4:30303","enode://f42c13...fc456@1.2.3.5:30303"]
```
  
```bash tab="Example Node Acting as Bootnode"
--bootnodes
```  
  
List of comma-separated enode URLs for P2P discovery bootstrap. 
  
When connecting to MainNet or public testnets, the default is a predefined list of enode URLs. 

On custom networks defined by [`--genesis-file`](#genesis-file) option,
an empty list of bootnodes is defined by default unless you define custom bootnodes as described in 
[private network documentation](../Configuring-Pantheon/Networking/Bootnodes.md#bootnodes).

!!! note
    Specifying that a node is a [bootnode](../Configuring-Pantheon/Networking/Bootnodes.md#bootnodes) 
    must be done on the command line using [`--bootnodes`](#bootnodes) option without value,
    not in a [configuration file](../Configuring-Pantheon/Using-Configuration-File.md).  

### config-file

```bash tab="Syntax"
--config-file=<FILE>
```

```bash tab="Command Line"
--config-file=/home/me/me_node/config.toml
```

```bash tab="Environment Variable"
PANTHEON_CONFIG_FILE=/home/me/me_node/config.toml
```

The path to the [TOML configuration file](../Configuring-Pantheon/Using-Configuration-File.md).
The default is `none`.
        
### data-path

```bash tab="Syntax"
--data-path=<PATH>
```

```bash tab="Command Line"
--data-path=/home/me/me_node
```

```bash tab="Environment Variable"
PANTHEON_DATA_PATH=/home/me/me_node
```

```bash tab="Configuration File"
data-path="/home/me/me_node"
```

The path to the Pantheon data directory. The default is the directory in which Pantheon is installed
or `/opt/pantheon/database` if using the [Pantheon Docker image](../Getting-Started/Run-Docker-Image.md).

### discovery-enabled

```bash tab="Syntax"
--discovery-enabled=false
```

```bash tab="Environment Variable"
PANTHEON_DISCOVERY_ENABLED=false
```

```bash tab="Example Configuration File"
discovery-enabled=false
```

Enables or disables P2P peer discovery.
The default is `true`.

### genesis-file

Genesis file is used to create a custom network.

!!!tip
    To use a public Ethereum network such as Rinkeby, use the [`--network`](#network) option.
    The network option defines the genesis file for public networks.

```bash tab="Syntax"
--genesis-file=<FILE>
```

```bash tab="Command Line"
--genesis-file=/home/me/me_node/customGenesisFile.json
```

```bash tab="Environment Variable"
PANTHEON_GENESIS_FILE=/home/me/me_node/customGenesisFile.json
```

```bash tab="Configuration File"
genesis-file="/home/me/me_node/customGenesisFile.json"
```

The path to the genesis file.

!!!important
    The [`--genesis-file`](#genesis-file) and [`--network`](#network) option can't be used at the same time.

### graphql-http-cors-origins

```bash tab="Syntax"
--graphql-http-cors-origins=<graphQLHttpCorsAllowedOrigins>
```

```bash tab="Command Line"
--graphql-http-cors-origins="http://medomain.com","https://meotherdomain.com"
```

```bash tab="Environment Variable"
PANTHEON_GRAPHQL_HTTP_CORS_ORIGINS="http://medomain.com","https://meotherdomain.com"
```

```bash tab="Configuration File"
graphql-http-cors-origins=["http://medomain.com","https://meotherdomain.com"]
```

Comma separated origin domain URLs for CORS validation. The default is none. 

### graphql-http-enabled

```bash tab="Syntax"
--graphql-http-enabled
```

```bash tab="Environment Variable"
PANTHEON_GRAPHQL_HTTP_ENABLED=true
```

```bash tab="Configuration File"
graphql-http-enabled=true
```

Set to `true` to enable the GraphQL HTTP service.
The default is `false`.

### graphql-http-host

```bash tab="Syntax"
--graphql-http-host=<HOST>
```

```bash tab="Command Line"
# to listen on all interfaces
--graphql-http-host=0.0.0.0
```

```bash tab="Environment Variable"
# to listen on all interfaces
PANTHEON_GRAPHQL_HTTP_HOST=0.0.0.0
```

```bash tab="Configuration File"
graphql-http-host="0.0.0.0"
```

Host for GraphQL HTTP to listen on.
The default is 127.0.0.1.

To allow remote connections, set to `0.0.0.0`
    
### graphql-http-port

```bash tab="Syntax"
--graphql-http-port=<PORT>
```

```bash tab="Command Line"
# to listen on port 6175
--graphql-http-port=6175
```

```bash tab="Environment Variable"
# to listen on port 6175
PANTHEON_GRAPHQL_HTTP_PORT=6175
```

```bash tab="Configuration File"
graphql-http-port="6175"
```

Specifies GraphQL HTTP listening port (TCP).
The default is 8547. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md).

### host-whitelist

```bash tab="Syntax"
--host-whitelist=<hostname>[,<hostname>...]... or "*"
```

```bash tab="Command Line"
--host-whitelist=medomain.com,meotherdomain.com
```

```bash tab="Environment Variable"
PANTHEON_HOST_WHITELIST=medomain.com,meotherdomain.com
```

```bash tab="Configuration File"
host-whitelist=["medomain.com", "meotherdomain.com"]
```

Comma-separated list of hostnames to allow [access to the JSON-RPC API](../Pantheon-API/Using-JSON-RPC-API.md#host-whitelist). 
By default, access from `localhost` and `127.0.0.1` is accepted. 

!!!tip
    To allow all hostnames, use `"*"`. We don't recommend allowing all hostnames for production code.

### max-peers

```bash tab="Syntax"
--max-peers=<INTEGER>
```

```bash tab="Command Line"
--max-peers=42
```

```bash tab="Environment Variable"
PANTHEON_MAX_PEERS=42
```

```bash tab="Configuration File"
max-peers=42
```

Specifies the maximum P2P connections that can be established.
The default is 25.

### metrics-category

```bash tab="Syntax"
--metrics-category=<metrics-category>[,metrics-category...]...
```

```bash tab="Command Line"
--metrics-category=BLOCKCHAIN,PEERS,PROCESS
```

```bash tab="Environment Variable"
PANTHEON_METRICS_CATEGORY=BLOCKCHAIN,PEERS,PROCESS
```

```bash tab="Configuration File"
metrics-category=["BLOCKCHAIN","PEERS","PROCESS"]
```

Comma separated list of categories for which to track metrics. The default is all categories: 
`BIG_QUEUE`, `BLOCKCHAIN`, `EXECUTORS`, `JVM`, `NETWORK`, `PEERS`, `PROCESS`, `ROCKSDB`, `RPC`, `SYNCHRONIZER`. 

### metrics-enabled

```bash tab="Syntax"
--metrics-enabled
```

```bash tab="Environment Variable"
PANTHEON_METRICS_ENABLED=true
```

```bash tab="Configuration File"
metrics-enabled=true
```

Set to `true` to enable the [metrics exporter](../Monitoring/Monitoring-Performance.md#monitor-node-performance-using-prometheus).
The default is `false`.

`--metrics-enabled` cannot be specified with `--metrics-push-enabled`. That is, either Prometheus polling or Prometheus 
push gateway support can be enabled but not both at once. 

### metrics-host

```bash tab="Syntax"
--metrics-host=<HOST>
```

```bash tab="Command Line"
--metrics-host=127.0.0.1
```

```bash tab="Environment Variable"
PANTHEON_METRICS_HOST=127.0.0.1
```

```bash tab="Configuration File"
metrics-host="127.0.0.1"
```

Specifies the host on which [Prometheus](https://prometheus.io/) accesses [Pantheon metrics](../Monitoring/Monitoring-Performance.md#monitor-node-performance-using-prometheus). 
The metrics server respects the [`--host-whitelist` option](#host-whitelist).

The default is `127.0.0.1`. 

### metrics-port

```bash tab="Syntax"
--metrics-port=<PORT>
```

```bash tab="Command Line"
--metrics-port=6174
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PORT=6174
```

```bash tab="Configuration File"
metrics-port="6174"
```

Specifies the port (TCP) on which [Prometheus](https://prometheus.io/) accesses [Pantheon metrics](../Monitoring/Monitoring-Performance.md#monitor-node-performance-using-prometheus).
The default is `9545`. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md).

### metrics-push-enabled 

```bash tab="Syntax"
--metrics-push-enabled[=<true|false>]
```

```bash tab="Command Line"
--metrics-push-enabled
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PUSH_ENABLED=true
```

```bash tab="Configuration File"
metrics-push-enabled="true"
```

Set to `true` to start the [push gateway integration](../Monitoring/Monitoring-Performance.md#running-prometheus-with-pantheon-in-push-mode).

`--metrics-push-enabled` cannot be specified with `--metrics-enabled`. That is, either Prometheus polling or Prometheus 
push gateway support can be enabled but not both at once.

### metrics-push-host

```bash tab="Syntax"
--metrics-push-host=<HOST>
```

```bash tab="Command Line"
--metrics-push-host=127.0.0.1
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PUSH_HOST=127.0.0.1
```

```bash tab="Configuration File"
metrics-push-host="127.0.0.1"
```

Host of the [Prometheus Push Gateway](https://github.com/prometheus/pushgateway).
The default is `127.0.0.1`. 
The metrics server respects the [`--host-whitelist` option](#host-whitelist).

!!! note
    When pushing metrics, ensure `--metrics-push-host` is set to the machine on which the push gateway is. 
    Generally, this will be a different machine to the machine on which Pantheon is running.  

### metrics-push-interval

```bash tab="Syntax"
--metrics-push-interval=<INTEGER>
```

```bash tab="Command Line"
--metrics-push-interval=30
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PUSH_INTERVAL=30
```

```bash tab="Configuration File"
metrics-push-interval=30
```

Interval in seconds to push metrics when in `push` mode. The default is 15.

### metrics-push-port

```bash tab="Syntax"
--metrics-push-port=<PORT>
```

```bash tab="Command Line"
--metrics-push-port=6174
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PUSH_PORT=6174
```

```bash tab="Configuration File"
metrics-push-port="6174"
```

Port (TCP) of the [Prometheus Push Gateway](https://github.com/prometheus/pushgateway).
The default is `9001`. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md).

### metrics-push-prometheus-job

```bash tab="Syntax"
--metrics-prometheus-job=<metricsPrometheusJob>
```

```bash tab="Command Line"
--metrics-prometheus-job="my-custom-job"
```

```bash tab="Environment Variable"
PANTHEON_METRICS_PROMETHEUS_JOB="my-custom-job"
```

```bash tab="Configuration File"
metrics-prometheus-job="my-custom-job"
```

Job name when in `push` mode. The default is `pantheon-client`. 

### miner-coinbase

```bash tab="Syntax"
--miner-coinbase=<Ethereum account address>
```

```bash tab="Command Line"
--miner-coinbase=fe3b557e8fb62b89f4916b721be55ceb828dbd73
```

```bash tab="Environment Variable"
PANTHEON_MINER_COINBASE=fe3b557e8fb62b89f4916b721be55ceb828dbd73
```

```bash tab="Configuration File"
--miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
```

Account to which mining rewards are paid.
You must specify a valid coinbase when you enable mining using the [`--miner-enabled`](#miner-enabled) 
option or the [`miner_start`](Pantheon-API-Methods.md#miner_start) JSON RPC-API method.

!!!note
    This option is ignored in networks using [Clique](../Consensus-Protocols/Clique.md) and [IBFT 2.0](../Consensus-Protocols/IBFT.md) consensus protocols. 

### miner-enabled

```bash tab="Syntax"
--miner-enabled
```

```bash tab="Environment Variable"
PANTHEON_MINER_ENABLED=true
```

```bash tab="Configuration File"
miner-enabled=true
```

Enables mining when the node is started.
Default is `false`.
  
### miner-extra-data

```bash tab="Syntax"
--miner-extra-data=<Extra data>
```

```bash tab="Command Line"
--miner-extra-data=0x444F4E27542050414E4943202120484F444C2C20484F444C2C20484F444C2021
```

```bash tab="Environment Variable"
PANTHEON_MINER_EXTRA_DATA=0x444F4E27542050414E4943202120484F444C2C20484F444C2C20484F444C2021
```

```bash tab="Configuration File"
miner-extra-data="0x444F4E27542050414E4943202120484F444C2C20484F444C2C20484F444C2021"
```

A hex string representing the 32 bytes to be included in the extra data field of a mined block.
The default is 0x.

### min-gas-price

```bash tab="Syntax"
--min-gas-price=<minTransactionGasPrice>
```

```bash tab="Command Line"
--min-gas-price=1337
```

```bash tab="Environment Variable"
PANTHEON_MIN_GAS_PRICE=1337
```

```bash tab="Configuration File"
min-gas-price=1337
```

The minimum price that a transaction offers for it to be included in a mined block.
The default is 1000.

### nat-method

```bash tab="Syntax"
--nat-method=<METHOD>
```

```bash tab="Command Line"
--nat-method=upnp
```

```bash tab="Example Configuration File"
nat-method="upnp"
```

Specifies the method for handling [NAT environments](../Configuring-Pantheon/Networking/Using-UPnP.md). 
Options are `upnp` and `none`. The default is `none` (that is, NAT functionality is disabled).

### network

```bash tab="Syntax"
--network=<NETWORK>
```

```bash tab="Command Line"
--network=rinkeby
```

```bash tab="Command Line"
PANTHEON_NETWORK=rinkeby
```

```bash tab="Configuration File"
network="rinkeby"
```

Predefined network configuration.
The default is `mainnet`.

Possible values are :

`mainnet`
:   Main Ethereum network

`ropsten`
:   PoW test network similar to current main Ethereum network. 

`rinkeby`
:   PoA test network using Clique.

`goerli`
:   PoA test network using Clique.

`dev`
:   PoW development network with a very low difficulty to enable local CPU mining.

!!!note
    Values are case insensitive, so either `mainnet` or `MAINNET` works.
    
!!!important
    The [`--network`](#network) and [`--genesis-file`](#genesis-file) option cannot be used at the same time.
    
### network-id

```bash tab="Syntax"
--network-id=<INTEGER>
```

```bash tab="Command Line"
--network-id=8675309
```

```bash tab="Environment Variable"
PANTHEON_NETWORK_ID=8675309
```

```bash tab="Configuration File"
network-id="8675309"
```

[P2P network identifier](../Configuring-Pantheon/NetworkID-And-ChainID.md).

This option can be used to override the default network ID.
The default value is the network chain ID defined in the genesis file.

### node-private-key-file

```bash tab="Syntax"
--node-private-key-file=<FILE>
```

```bash tab="Command Line"
--node-private-key-file=/home/me/me_node/myPrivateKey
```

```bash tab="Environment Variable"
PANTHEON_NODE_PRIVATE_KEY_FILE=/home/me/me_node/myPrivateKey
```

```bash tab="Configuration File"
node-private-key-file="/home/me/me_node/myPrivateKey"
```

`<FILE>` is the path of the private key file of the node.
The default is the key file in the [data directory](#data-path).
If no key file exists, a key file containing the generated private key is created;
otherwise, the existing key file specifies the node private key.

!!!attention
    The private key is not encrypted.

### p2p-enabled

```bash tab="Syntax"
--p2p-enabled=<true|false>
```

```bash tab="Command line"
--p2p-enabled=false
```

```bash tab="Environment Variable"
PANTHEON_P2P_ENABLED=false
```

```bash tab="Configuration File"
p2p-enabled=false
```

Enables or disables all p2p communication.
The default is true.

### p2p-host

```bash tab="Syntax"
--p2p-host=<HOST>
```

```bash tab="Command Line"
# to listen on all interfaces
--p2p-host=0.0.0.0
```

```bash tab="Environment Variable"
# to listen on all interfaces
PANTHEON_P2P_HOST=0.0.0.0
```

```bash tab="Configuration File"
p2p-host="0.0.0.0"
```

Specifies the host on which P2P listens.
The default is 127.0.0.1.

### p2p-port

```bash tab="Syntax"
--p2p-port=<PORT>
```

```bash tab="Command Line"
# to listen on port 1789
--p2p-port=1789
```

```bash tab="Environment Variable"
# to listen on port 1789
PANTHEON_P2P_PORT=1789
```

```bash tab="Configuration File"
p2p-port="1789"
```

Specifies the P2P listening ports (UDP and TCP).
The default is 30303. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md).

### nat-method

```bash tab="Syntax"
--nat-method=UPNP
```

```bash tab="Example Configuration File"
nat-method="UPNP"
```

Specify the method for handling NAT environments. Options are: `UPNP` and `NONE`.
The default is `NONE`, which disables NAT functionality.

!!!tip
    `UPNP` works well with a typical home or small office environment where a wireless router or modem provides NAT isolation. This should provide
    automatic detection and port-forwarding. UPnP support is often disabled by default in networking equipment firmware, however, any may need to be
    explicitly enabled.

!!!note
    Option `UPNP` may introduce delays during node startup, especially on networks where no UPnP gateway device can be found.

### permissions-accounts-config-file-enabled

```bash tab="Syntax"
--permissions-accounts-config-file-enabled[=<true|false>]
```

```bash tab="Command Line"
--permissions-accounts-config-file-enabled
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_ACCOUNTS_CONFIG_FILE_ENABLED=true
```

```bash tab="Configuration File"
permissions-accounts-config-file-enabled=true
```

Set to enable file-based account level permissions. Default is `false`. 

### permissions-accounts-config-file    

```bash tab="Syntax"
--permissions-accounts-config-file=<FILE>
```

```bash tab="Command Line"
--permissions-accounts-config-file=/home/me/me_configFiles/myPermissionsFile
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_ACCOUNTS_CONFIG_FILE=/home/me/me_configFiles/myPermissionsFile
```

```bash tab="Configuration File"
permissions-accounts-config-file="/home/me/me_configFiles/myPermissionsFile"
```

Path to the [accounts permissions configuration file](../Permissions/Local-Permissioning.md#permissions-configuration-file).
Default is the `permissions_config.toml` file in the [data directory](#data-path).

!!! tip
    `--permissions-accounts-config-file` and [`--permissions-nodes-config-file`](#permissions-nodes-config-file)
    can use the same file. 

### permissions-accounts-contract-address

```bash tab="Syntax"
--permissions-accounts-contract-address=<ContractAddress>
```

```bash tab="Command Line"
--permissions-accounts-contract-address=xyz
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_ACCOUNTS_CONTRACT_ADDRESS=xyz
```

```bash tab="Configuration File"
permissions-accounts-contract-address=xyz
```

Specifies the contract address for [onchain account permissioning](../Permissions/Onchain-Permissioning/Onchain-Permissioning.md).

### permissions-accounts-contract-enabled

```bash tab="Syntax"
--permissions-accounts-contract-enabled[=<true|false>]
```

```bash tab="Command Line"
--permissions-accounts-contract-enabled
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_ACCOUNTS_CONTRACT_ENABLED=true
```

```bash tab="Configuration File"
permissions-accounts-contract-enabled=true
```

Enables contract-based [onchain account permissioning](../Permissions/Onchain-Permissioning/Onchain-Permissioning.md). Default is `false`.

### permissions-nodes-config-file-enabled

```bash tab="Syntax"
--permissions-nodes-config-file-enabled[=<true|false>]
```

```bash tab="Command Line"
--permissions-nodes-config-file-enabled
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_NODES_CONFIG_FILE_ENABLED=true
```

```bash tab="Configuration File"
permissions-nodes-config-file-enabled=true
```

Set to enable file-based node level permissions. Default is `false`.

### permissions-nodes-config-file    

```bash tab="Syntax"
--permissions-nodes-config-file=<FILE>
```

```bash tab="Command Line"
--permissions-nodes-config-file=/home/me/me_configFiles/myPermissionsFile
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_NODES_CONFIG_FILE=/home/me/me_configFiles/myPermissionsFile
```

```bash tab="Configuration File"
permissions-nodes-config-file="/home/me/me_configFiles/myPermissionsFile"
```

Path to the [nodes permissions configuration file](../Permissions/Local-Permissioning.md#permissions-configuration-file).
Default is the `permissions_config.toml` file in the [data directory](#data-path).

!!! tip
    `--permissions-nodes-config-file` and [`--permissions-accounts-config-file`](#permissions-accounts-config-file)
    can use the same file. 

### permissions-nodes-contract-address

```bash tab="Syntax"
--permissions-nodes-contract-address=<ContractAddress>
```

```bash tab="Command Line"
--permissions-nodes-contract-address=xyz
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_NODES_CONTRACT_ADDRESS=xyz
```

```bash tab="Configuration File"
permissions-nodes-contract-address=xyz
```

Specifies the contract address for [onchain node permissioning](../Permissions/Onchain-Permissioning/Onchain-Permissioning.md).

### permissions-nodes-contract-enabled

```bash tab="Syntax"
--permissions-nodes-contract-enabled[=<true|false>]
```

```bash tab="Command Line"
--permissions-nodes-contract-enabled
```

```bash tab="Environment Variable"
PANTHEON_PERMISSIONS_NODES_CONTRACT_ENABLED=true
```

```bash tab="Configuration File"
permissions-nodes-contract-enabled=true
```

Enables contract-based [onchain node permissioning](../Permissions/Onchain-Permissioning/Onchain-Permissioning.md). Default is `false`.

### privacy-enabled

```bash tab="Syntax"
--privacy-enabled[=<true|false>]
```

```bash tab="Command Line"
--privacy-enabled=false
```

```bash tab="Environment Variable"
PANTHEON_PRIVACY_ENABLED=false
```

```bash tab="Configuration File"
privacy-enabled=false
```

Set to enable [private transactions](../Privacy/Explanation/Privacy-Overview.md). 
The default is false.  

### privacy-precompiled-address

```bash tab="Syntax"
--privacy-precompiled-address=<privacyPrecompiledAddress>
```

Address to which the [privacy pre-compiled contract](../Privacy/Explanation/Private-Transaction-Processing.md) is mapped.
The default is 126.     
    
### privacy-public-key-file

```bash tab="Syntax"
--privacy-public-key-file=<privacyPublicKeyFile>
```

```bash tab="Command Line"
--privacy-public-key-file=Orion/nodeKey.pub
```

```bash tab="Environment Variable"
PANTHEON_PRIVACY_PUBLIC_KEY_FILE=Orion/nodeKey.pub
```

```bash tab="Configuration File"
privacy-public-key-file="Orion/nodeKey.pub"
```

Path to the [public key of the Orion node](../Privacy/Explanation/Privacy-Overview.md#pantheon-and-orion-keys).     

### privacy-url

```bash tab="Syntax"
--privacy-url=<privacyUrl>
```

```bash tab="Command Line"
--privacy-url=http://127.0.0.1:8888
```

```bash tab="Environment Variable"
PANTHEON_PRIVACY_URL=http://127.0.0.1:8888
```

```bash tab="Configuration File"
privacy-url="http://127.0.0.1:8888"
```

URL on which the [Orion node](../Privacy/Tutorials/Configuring-Privacy.md#4-create-orion-configuration-files) is running.    

### revert-reason-enabled

```bash tab="Syntax"
--revert-reason-enabled[=<true|false>]
```

```bash tab="Command Line"
--revert-reason-enabled=true
```

```bash tab="Environment Variable"
REVERT_REASON_ENABLED=true
```

```bash tab="Configuration File"
revert-reason-enabled=true
```

Enables including the [revert reason](../Using-Pantheon/Transactions/Revert-Reason.md) in the transaction 
receipt. Default is `false`. 

!!! caution 
    Enabling revert reason may use a significant amount of memory. We do not recommend enabling revert
    reason when connected to public Ethereum networks. 

### remote-connections-limit-enabled

```bash tab="Syntax"
--remote-connections-limit-enabled[=<true|false>]
```

```bash tab="Command Line"
--remote-connections-limit-enabled=false
```

```bash tab="Environment Variable"
PANTHEON_REMOTE_CONNECTIONS_LIMIT_ENABLED=false
```

```bash tab="Configuration File"
remote-connections-limit-enabled=false
```

Specify to limit the percentage of remote P2P connections initiated by peers. Default is true. 

!!! tip
    In private networks with a level of trust between peers, disabling the remote connection limits 
    may increase the speed at which nodes can join the network.

!!! important
    To prevent eclipse attacks, ensure the remote connections limit is enabled when connecting to 
    any public network and especially when using [fast sync](#fast-sync-options). 

### remote-connections-max-percentage

```bash tab="Syntax"
--remote-connections-max-percentage=<DOUBLE>
```

```bash tab="Command Line"
--remote-connections-max-percentage=25
```

```bash tab="Environment Variable"
PANTHEON_REMOTE_CONNECTIONS_MAX_PERCENTAGE=25
```

```bash tab="Configuration File"
remote-connections-max-percentage=25
```

Percentage of remote P2P connections that can be established with the node. Must be between 0 and 100 inclusive.
Default is 60. 

### rpc-http-api

```bash tab="Syntax"
--rpc-http-api=<api name>[,<api name>...]...
```

```bash tab="Command Line"
--rpc-http-api=ETH,NET,WEB3
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_API=ETH,NET,WEB3
```

```bash tab="Configuration File"
rpc-http-api=["ETH","NET","WEB3"]
```

Comma-separated APIs to enable on the HTTP JSON-RPC channel.
When you use this option, the `--rpc-http-enabled` option must also be specified.
The available API options are: `ADMIN`, `ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`, `PERM`, `DEBUG`, `MINER`, `EEA`, and `TXPOOL`.
The default is: `ETH`, `NET`, `WEB3`.

!!!tip
    The singular `--rpc-http-api` and plural `--rpc-http-apis` are available and are two
    names for the same option.
    
### rpc-http-authentication-credentials-file

```bash tab="Syntax"
--rpc-http-authentication-credentials-file=<FILE>
```

```bash tab="Command Line"
--rpc-http-authentication-credentials-file=/home/me/me_node/auth.toml
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_AUTHENTICATION_CREDENTIALS_FILE=/home/me/me_node/auth.toml
```

```bash tab="Configuration File"
rpc-http-authentication-credentials-file="/home/me/me_node/auth.toml"
```

[Credentials file](../Pantheon-API/Authentication.md#credentials-file) for JSON-RPC API [authentication](../Pantheon-API/Authentication.md). 

### rpc-http-authentication-enabled

```bash tab="Syntax"
--rpc-http-authentication-enabled
```

```bash tab="Command Line"
--rpc-http-authentication-enabled
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_AUTHENTICATION_ENABLED=true
```

```bash tab="Configuration File"
rpc-http-authentication-enabled=true
```

Set to `true` to require [authentication](../Pantheon-API/Authentication.md) for the HTTP JSON-RPC service.  

### rpc-http-cors-origins

```bash tab="Syntax"
--rpc-http-cors-origins=<url>[,<url>...]... or all or "*"
```

```bash tab="Command Line"
# You can whitelist one or more domains with a comma-separated list.

--rpc-http-cors-origins="http://medomain.com","https://meotherdomain.com"
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_CORS_ORIGINS="http://medomain.com","https://meotherdomain.com"
```

```bash tab="Configuration File"
rpc-http-cors-origins=["http://medomain.com","https://meotherdomain.com"]
```

```bash tab="Remix Example"
# The following allows Remix to interact with your Pantheon node.

--rpc-http-cors-origins="http://remix.ethereum.org"
```

Specifies domain URLs for CORS validation.
Domain URLs must be enclosed in double quotes and comma-separated.

Listed domains can access the node using JSON-RPC.
If your client interacts with Pantheon using a browser app (such as Remix or a block explorer), 
you must whitelist the client domains. 

The default value is `"none"`.
If you don't whitelist any domains, browser apps cannot interact with your Pantheon node.

!!!note
    To run a local Pantheon node as a backend for MetaMask and use MetaMask anywhere, set `--rpc-http-cors-origins` to `"all"` or `"*"`. 
    To allow a specific domain to use MetaMask with the Pantheon node, set `--rpc-http-cors-origins` to the client domain. 
        
!!!tip
    For development purposes, you can use `"all"` or `"*"` to accept requests from any domain, 
    but we don't recommend this for production code.

### rpc-http-enabled

```bash tab="Syntax"
--rpc-http-enabled
```

```bash tab="Environement Variable"
PANTHEON_RPC_HTTP_ENABLED=true
```

```bash tab="Configuration File"
rpc-http-enabled=true
```

Set to `true` to enable the HTTP JSON-RPC service.
The default is `false`.

### rpc-http-host

```bash tab="Syntax"
--rpc-http-host=<HOST>
```

```bash tab="Command Line"
# to listen on all interfaces
--rpc-http-host=0.0.0.0
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_HOST=0.0.0.0
```

```bash tab="Configuration File"
rpc-http-host="0.0.0.0"
```

Specifies the host on which HTTP JSON-RPC listens.
The default is 127.0.0.1.

To allow remote connections, set to `0.0.0.0`

!!! caution 
    Setting the host to 0.0.0.0 exposes the RPC connection on your node to any remote connection. In a 
    production environment, ensure you are using a firewall to avoid exposing your node to the internet. 

### rpc-http-port

```bash tab="Syntax"
--rpc-http-port=<PORT>
```

```bash tab="Command Line"
# to listen on port 3435
--rpc-http-port=3435
```

```bash tab="Environment Variable"
PANTHEON_RPC_HTTP_PORT=3435
```

```bash tab="Configuration File"
rpc-http-port="3435"
```

Specifies HTTP JSON-RPC listening port (TCP).
The default is 8545. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md). 

### rpc-ws-api

```bash tab="Syntax"
--rpc-ws-api=<api name>[,<api name>...]...
```

```bash tab="Command Line"
--rpc-ws-api=ETH,NET,WEB3
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_API=ETH,NET,WEB3
```

```bash tab="Configuration File"
rpc-ws-api=["ETH","NET","WEB3"]
```

Comma-separated APIs to enable on WebSockets channel.
When you use this option, the `--rpc-ws-enabled` option must also be specified.
The available API options are: `ADMIN`,`ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`, `PERM', DEBUG`, `MINER`, `EEA`, and `TXPOOL`.
The default is: `ETH`, `NET`, `WEB3`.

!!!tip
    The singular `--rpc-ws-api` and plural `--rpc-ws-apis` are available and are just two
    names for the same option.

### rpc-ws-authentication-credentials-file

```bash tab="Syntax"
--rpc-ws-authentication-credentials-file=<FILE>
```

```bash tab="Command Line"
--rpc-ws-authentication-credentials-file=/home/me/me_node/auth.toml
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_AUTHENTICATION_CREDENTIALS_FILE=/home/me/me_node/auth.toml
```

```bash tab="Configuration File"
rpc-ws-authentication-credentials-file="/home/me/me_node/auth.toml"
```

[Credentials file](../Pantheon-API/Authentication.md#credentials-file) for JSON-RPC API [authentication](../Pantheon-API/Authentication.md).

### rpc-ws-authentication-enabled

```bash tab="Syntax"
--rpc-ws-authentication-enabled
```

```bash tab="Command Line"
--rpc-ws-authentication-enabled
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_AUTHENTICATION_ENABLED=true
```

```bash tab="Configuration File"
rpc-ws-authentication-enabled=true
```

Set to `true` to require [authentication](../Pantheon-API/Authentication.md) for the WebSockets JSON-RPC service.

!!! note 
    `wscat` does not support headers. [Authentication](../Pantheon-API/Authentication.md) requires an authentication token to be passed in the 
    request header. To use authentication with WebSockets, an app that supports headers is required. 

### rpc-ws-enabled

```bash tab="Syntax"
--rpc-ws-enabled
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_ENABLED=true
```

```bash tab="Configuration File"
rpc-ws-enabled=true
```

Set to `true` to enable the WebSockets JSON-RPC service.
The default is `false`.
    
### rpc-ws-host

```bash tab="Syntax"
--rpc-ws-host=<HOST>
```

```bash tab="Command Line"
# to listen on all interfaces
--rpc-ws-host=0.0.0.0
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_HOST=0.0.0.0
```

```bash tab="Configuration File"
rpc-ws-host="0.0.0.0"
```

Host for Websocket WS-RPC to listen on.
The default is 127.0.0.1.

To allow remote connections, set to `0.0.0.0`
    
### rpc-ws-port

```bash tab="Syntax"
--rpc-ws-port=<PORT>
```

```bash tab="Command Line"
# to listen on port 6174
--rpc-ws-port=6174
```

```bash tab="Environment Variable"
PANTHEON_RPC_WS_PORT=6174
```

```bash tab="Configuration File"
rpc-ws-port="6174"
```

Specifies Websockets JSON-RPC listening port (TCP).
The default is 8546. Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Configuring-Ports.md).

### tx-pool-max-size

```bash tab="Syntax"
--tx-pool-max-size=<INTEGER>
```

```bash tab="Command Line"
--tx-pool-max-size=2000
```

```bash tab="Environment Variable"
PANTHEON_TX_POOL_MAX_SIZE=2000
```

```bash tab="Configuration File"
tx-pool-max-size="2000"
```

Maximum number of transactions kept in the transaction pool. Default is 4096. 

### tx-pool-retention-hours

```bash tab="Syntax"
--tx-pool-retention-hours=<INTEGER>
```

```bash tab="Command Line"
--tx-pool-retention-hours=5
```

```bash tab="Environment Variable"
PANTHEON_TX_POOL_RETENTION_HOURS=5
```

```bash tab="Configuration File"
tx-pool-retention-hours="5"
```

Maximum period in hours to retain pending transactions in the transaction pool. Default is 13. 

### help

```bash tab="Syntax"
-h, --help
```

Show the help message and exit.

### logging

```bash tab="Syntax"
-l, --logging=<LEVEL>
```

```bash tab="Command Line"
--logging=DEBUG
```

```bash tab="Environment Variable"
PANTHEON_LOGGING=DEBUG
```
```bash tab="Example Configration File"
logging="DEBUG"
```

Sets the logging verbosity.
Log levels are `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL`.
Default is `INFO`.

### version

```bash tab="Syntax"
  -V, --version
```

Print version information and exit.

## Fast Sync Options 

### sync-mode

```bash tab="Syntax"
--sync-mode=FAST
```

```bash tab="Command Line"
--sync-mode=FAST
```

```bash tab="Environment Variable"
PANTHEON_SYNC_MODE=FAST
```

```bash tab="Configuration File"
sync-mode="FAST"
```

Specifies the synchronization mode. Default is `FULL`.

### fast-sync-min-peers

```bash tab="Syntax"
--fast-sync-min-peers=<INTEGER>
```

```bash tab="Command Line"
--fast-sync-min-peers=2
```

```bash tab="Environment Variable"
PANTHEON_FAST_SYNC_MIN_PEERS=2
```

```bash tab="Example Configuration File"
fast-sync-min-peers=2
```

Minimum number of peers required before starting fast sync. Default is 5. 

## Subcommands

### blocks

Provides blocks related actions.

### import

```bash tab="Syntax"
pantheon blocks import --from=<block-file>
```

```bash tab="Example"
pantheon blocks import --from=/home/me/me_project/mainnet.blocks
```

Imports blocks from the specified file into the blockchain database

### public-key

This command provides node public key related actions.

### export

```bash tab="Syntax"
pantheon public-key export [--to=<key-file>]
```

```bash tab="Example (to standard output)"
pantheon --data-path=<node data path> public-key export
```

```bash tab="Example (to file)"
pantheon --data-path=<node data path> public-key export --to=/home/me/me_project/not_precious_pub_key
```

Outputs the node public key to standard output or writes it to the specified file if 
`--to=<key-file>` is specified. 

### export-address

```bash tab="Syntax"
pantheon public-key export-address [--to=<address-file>]
```

```bash tab="Example (to standard output)"
pantheon --data-path=<node data path> public-key export-address
```

```bash tab="Example (to file)"
pantheon --data-path=<node data path> public-key export-address --to=/home/me/me_project/me_node_address
```

Outputs the node public key address to standard output or writes it to the specified file if  
`--to=<key-file>` is specified. 

### password

Provides password related actions.

### hash

This command generates the hash of a given password. Include the hash in the [credentials file](../Pantheon-API/Authentication.md#credentials-file)
 for JSON-RPC API [authentication](../Pantheon-API/Authentication.md). 

```bash tab="Syntax"
pantheon password hash --password=<my-password>
```

```bash tab="Example"
pantheon password hash --password=myPassword123
```

### operator

Provides operator actions.

### generate-blockchain-config

This command generates [IBFT 2.0 configuration files](../Tutorials/Create-IBFT-Network.md). 

```bash tab="Syntax"
pantheon operator generate-blockchain-config --config-file=<FILE> --to=<DIRECTORY> [--genesis-file-name=<FILE>] [--private-key-file-name=<FILE>] [--public-key-file-name=<FILE>]
```

```bash tab="Example"
pantheon operator generate-blockchain-config --config-file=config.json --to=myNetworkFiles
```

The configuration file has 2 subnested JSON nodes. The first is the `genesis` property defining 
the [IBFT 2.0 genesis file](../Consensus-Protocols/IBFT.md#genesis-file) except for the `extraData` string. The 
second is the `blockchain` property defining the number of key pairs to generate.  

### rlp

Provides RLP related actions.

### encode

This command encodes a typed JSON value from a file or from the standard input into an RLP hexadecimal string.

```bash tab="Syntax"
pantheon rlp encode [--from=<FILE>] [--to=<FILE>] [--type=<type>]
```

```bash tab="File Example"
pantheon rlp encode --from=ibft_extra_data.json --to=extra_data_for_ibft_genesis.txt --type=IBFT_EXTRA_DATA
```

```bash tab="Standart Input/Output Example"
cat extra_data.json | pantheon rlp encode > rlp.txt
```

The `IBFT_EXTRA_DATA` type is the only type supported for RLP encoding.
This data is included in the [IBFT 2.0 genesis file](../Consensus-Protocols/IBFT.md#genesis-file).

???+ summary "IBFT 2.0 Extra Data"
    To generate the RLP encoded `extraData` string, specify a JSON input that is array of validator addresses 
    in ascending order.

    ??? tip "JSON Schema for IBFT_EXTRA_DATA"
        The following JSON Schema can be used to validate that your JSON data is well formed. You can use an online validation tool
        such as https://www.jsonschemavalidator.net/ to validate your JSON content.
        
        ```json
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "$id": "http://tech.pegasys.pantheon/cli_rlp_ibft_extra_data.json",
          "type": "array",
          "definitions": {},
          "title": "IBFT extra data",
          "description":"JSON format used as input to generate an IBFT extra data RLP string",
          "items": {
            "$id": "#/address",
            "type": "string",
            "title": "Validator address",
            "description":"The validator node address",
            "default": "",
            "examples": [
              "be068f726a13c8d46c44be6ce9d275600e1735a4",
              "5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193"
            ],
            "pattern":"^([0-9a-f]{40})$"
          }
        }
        ``` 
        
    !!!example "Example IBFT_EXTRA_DATA encoding"
        ```json tab="JSON Input"
        [
          "be068f726a13c8d46c44be6ce9d275600e1735a4",
          "5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193"
        ]
        ```
        
        ``` tab="RLP Output"
        0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0
        ```

