description: Pantheon commande line interface reference
<!--- END of page meta data -->

# Pantheon Command Line

!!! important "Breaking Changes in v0.9"
    In v0.9, changes will be made to the command line options to improve usability. These will be breaking changes; that is, 
    in many cases the v0.8 command line options will no longer work. This reference and the rest of the documentation will be 
    updated to reflect these changes. Any further information required about the changes will be included in the v0.9 release notes. 

This reference describes the syntax of the Pantheon Command Line Interface (CLI) options and subcommands.

```bash
pantheon [OPTIONS] [COMMAND]
```

Runs the Pantheon Ethereum full node client.

!!!tip
    Use a [configuration file](../Configuring-Pantheon/Using-Configuration-File.md) to save the command line options in a file.

## Options

### accounts-whitelist

```bash tab="Syntax"
--accounts-whitelist[=<hex string of account public key>[,<hex string of account public key>...]...
```

```bash tab="Example"
 --accounts-whitelist=[0xfe3b557e8fb62b89f4916b721be55ceb828dbd73, 0x627306090abaB3A6e1400e9345bC60c78a8BEf57]
```

Comma separated account public keys for permissioned transactions. You can specify an empty list.

!!!note
    Permissioning is under development and will be available in v1.0.

### banned-nodeids

```bash tab="Syntax"
--banned-nodeids=<bannedNodeId>[,<bannedNodeId>...]...
```

```bash tab="Example Command Line"
--banned-nodeids=0xc35c3...d615f,0xf42c13...fc456
```

```bash tab="Example Configuration File"
banned-nodeids=["0xc35c3...d615f","0xf42c13...fc456"]
```

List of node IDs with which this node will not peer. The node ID is the public key of the node. You can specify the banned node IDs with or without the `0x` prefix.
  
!!!info
    This option is only available from v0.8.2. 
 
### bootnodes

```bash tab="Syntax"
--bootnodes=<enode://id@host:port>[,<enode://id@host:port>...]...
```

```bash tab="Example Command Line"
--bootnodes=enode://c35c3...d615f@1.2.3.4:30303,enode://f42c13...fc456@1.2.3.5:30303
```

```bash tab="Example Configuration File"
bootnodes=["enode://c35c3...d615f@1.2.3.4:30303","enode://f42c13...fc456@1.2.3.5:30303"]
```
  
List of comma-separated enode URLs for P2P discovery bootstrap. 
  
When connecting to mainnet or public testnets, the default is a predefined list of enode URLs. 
Specify bootnodes when connecting to a [private network](../Configuring-Pantheon/Testing-Developing-Nodes.md#bootnodes).

### config

```bash tab="Syntax"
--config=<PATH>
```

```bash tab="Example Command Line"
--config=/home/me/me_node/config.toml
```

The path to the [TOML configuration file](../Configuring-Pantheon/Using-Configuration-File.md).
The default is `none`.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#custom-configuration-file) or in a [configuration file](../Configuring-Pantheon/Using-Configuration-File.md).
    
### datadir

```bash tab="Syntax"
--datadir=<PATH>
```

```bash tab="Example Command Line"
--datadir=/home/me/me_node
```

```bash tab="Example Configuration File"
datadir="/home/me/me_node"
```

The path to the Pantheon data directory. The default is the `/build/distributions/pantheon-<version>` directory in the Pantheon installation directory.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#persisting-data). 


### dev-mode

```bash tab="Syntax"
--dev-mode
```

```bash tab="Example Configuration File"
dev-mode=true
```
  
Set this option to `true` to run in development mode. 
For example, specify this option to perform CPU mining more easily in a private test network. 
In development mode, a custom genesis configuration specifies the chain ID. 
When using this option, also set the [`--network-id`](#network-id) option to the network you use for development.
Default is `false`.
  
  
!!!note
    The [`--dev-mode`](#dev-mode) option overrides the [`--genesis`](#genesis) option. If both are specified, the development mode configuration is used.  


### genesis

```bash tab="Syntax"
--genesis=<PATH>
```

```bash tab="Example Command Line"
--genesis=/home/me/me_node/customGenesisFile.json
```

```bash tab="Example Configuration File"
genesis="/home/me/me_node/customGenesisFile.json"
```

The path to the genesis file. The default is the embedded genesis file for the Ethereum mainnet. 
When using this option, it is recommended to also set the [`--network-id`](#network-id) option.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#custom-genesis-file). 

!!!note
    The [`--genesis`](#genesis) option is overridden by the [`--dev-mode`](#dev-mode) option. 
    If both are specified, the specified genesis file is ignored and the development mode configuration used. 


### goerli

```bash tab="Syntax"
--goerli
```

```bash tab="Example Configuration File"
goerli=true
```

Uses the Goerli test network. Default is false.

!!!note
    This option is only available from v0.8.3.


### host-whitelist

```bash tab="Syntax"
--host-whitelist=<hostname>[,<hostname>...]... or * or all
```

```bash tab="Example Command Line"
--host-whitelist=medomain.com,meotherdomain.com
```

```bash tab="Example Configuration File"
host-whitelist=["medomain.com", "meotherdomain.com"]
```

Comma-separated list of hostnames to allow access to the HTTP JSON-RPC API. Default is `localhost`. 

!!!tip
    To allow all hostnames, use `*` or `all`. We don't recommend allowing all hostnames for production code.

!!!note
    This option is only available from v0.8.3. Earlier versions allow access by all hostnames. 

### max-peers

```bash tab="Syntax"
--max-peers=<INTEGER>
```

```bash tab="Example Command Line"
--max-peers=42
```

```bash tab="Example Configuration File"
max-peers=42
```

Specifies the maximum P2P peer connections that can be established.
The default is 25.

### max-trailing-peers

```bash tab="Syntax"
--max-trailing-peers=<INTEGER>
```

```bash tab="Example Command Line"
--max-trailing-peers=2
```

```bash tab="Example Configuration File"
max-trailing-peers=2
```

Specifies the maximum P2P peer connections for peers that are trailing behind the local chain head. 
The default is unlimited but the number of trailing peers cannot exceed the value specified by [`--max-peers`](#max-peers).

### metrics-enabled

```bash tab="Syntax"
--metrics-enabled
```

```bash tab="Example Configuration File"
metrics-enabled=true
```

Set to `true` to enable the [Prometheus](https://prometheus.io/) monitoring service to access [Pantheon metrics](../Using-Pantheon/Debugging.md#monitor-node-performance-using-third-party-clients).
The default is `false`.

### metrics-listen

```bash tab="Syntax"
--metrics-listen=<HOST:PORT>
```

```bash tab="Example Command Line"
--metrics-listen=127.0.0.1:6174
```

```bash tab="Example Configuration File"
metrics-listen="127.0.0.1:6174"
```

Specifies the host and port on which the [Prometheus](https://prometheus.io/) monitoring service accesses Pantheon
metrics. The default is `127.0.0.1:9545`. The metrics server respects the [`--host-whitelist` option](#host-whitelist).

### miner-coinbase

```bash tab="Syntax"
--miner-coinbase=<Ethereum account address>
```

```bash tab="Example Command Line"
--miner-coinbase=fe3b557e8fb62b89f4916b721be55ceb828dbd73
```

```bash tab="Example Configuration File"
--miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
```

Account to which mining rewards are paid.
You must specify a valid coinbase when you enable mining using the [`--miner-enabled`](#miner-enabled) 
option or the [`miner_start`](JSON-RPC-API-Methods.md#miner_start) JSON RPC-API method.

!!!note
    This option is ignored in networks using the [Clique Proof-of-Authority (PoA) consensus protocol](../Configuring-Pantheon/Proof-of-Authority.md). 

### miner-enabled

```bash tab="Syntax"
--miner-enabled
```

```bash tab="Example Configuration File"
miner-enabled=true
```

Enables mining when the node is started.
Default is `false`.
  
### miner-extraData

```bash tab="Syntax"
--miner-extraData=<Extra data>
```

```bash tab="Example Command Line"
--miner-extraData=0x444F4E27542050414E4943202120484F444C2C20484F444C2C20484F444C2021
```

```bash tab="Example Configuration File"
miner-extraData="0x444F4E27542050414E4943202120484F444C2C20484F444C2C20484F444C2021"
```

A hex string representing the 32 bytes to be included in the extra data field of a mined block.
The default is 0x.

### miner-minTransactionGasPriceWei

```bash tab="Syntax"
--miner-minTransactionGasPriceWei=<minTransactionGasPrice>
```

```bash tab="Example Command Line"
--miner-minTransactionGasPriceWei=1337
```

```bash tab="Example Configuration File"
miner-minTransactionGasPriceWei="1337"
```

The minimum price that a transaction offers for it to be included in a mined block.
The default is 1000.

### network-id

```bash tab="Syntax"
--network-id=<INTEGER>
```

```bash tab="Example Command Line"
--network-id=8675309
```

```bash tab="Example Configuration File"
network-id="8675309"
```

P2P network identifier.
The default is set to mainnet with value `1`.

### no-discovery

```bash tab="Syntax"
--no-discovery
```

```bash tab="Example Configuration File"
no-discovery=true
```

Disables P2P peer discovery.
The default is `false`.

### node-private-key

```bash tab="Syntax"
--node-private-key=<PATH>
```

```bash tab="Example Command Line"
--node-private-key=/home/me/me_node/myPrivateKey
```

```bash tab="Example Configuration File"
node-private-key="/home/me/me_node/myPrivateKey"
```

`<PATH>` is the path of the private key file of the node.
The default is the key file in the data directory.
If no key file exists, a key file containing the generated private key is created;
otherwise, the existing key file specifies the node private key.


!!!attention
    The private key is not encrypted.
  
!!!note
    This option is only available from v0.8.2. 

### nodes-whitelist

```bash tab="Syntax"
--nodes-whitelist[=<enode://id@host:port>[,<enode://id@host:port>...]...]
```

```bash tab="Example Command Line"
--nodes-whitelist=enode://c35c3...d615f@3.14.15.92:30303,enode://f42c13...fc456@65.35.89.79:30303
```

```bash tab="Example Configuration File"
nodes-whitelist=["enode://c35c3...d615f@3.14.15.92:30303","enode://f42c13...fc456@65.35.89.79:30303"]
```

Comma-separated enode URLs for permissioned networks.
Not intended for use with mainnet or public testnets. 


!!!note
    This option is only available from v0.8.3. 

!!!note
    Permissioning is under development and will be available in v1.0.

### ottoman

```bash tab="Syntax"
--ottoman
```

```bash tab="Example Configuration File"
ottoman=true
```

Synchronize against the Ottoman test network. This is only useful if you are using an IBFT genesis file.  The default is `false`.

!!!note
    :construction: IBFT is not currently supported. Support for IBFT is in active development. 

### p2p-listen

```bash tab="Syntax"
--p2p-listen=<HOST:PORT>
```

```bash tab="Example Command Line"
# to listen on all interfaces on port 1789
--p2p-listen=0.0.0.0:1789
```

```bash tab="Example Configuration File"
p2p-listen="0.0.0.0:1789"
```

Specifies the host and port on which P2P peer discovery listens.
The default is 127.0.0.1:30303.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#exposing-ports). 

### rinkeby

```bash tab="Syntax"
--rinkeby
```

```bash tab="Example Configuration File"
rinkeby=true
```

Uses the Rinkeby test network.
Default is `false`.
  
  
### ropsten

```bash tab="Syntax"
--ropsten
```

```bash tab="Example Configuration File"
ropsten=true
```

Uses the Ropsten test network.
Default is `false`.

!!!note
    This option is only available only from v0.8.2. For v0.8.1, refer to [Starting Pantheon](../Getting-Started/Starting-Pantheon.md#run-a-node-on-ropsten-testnet). 

### rpc-enabled

```bash tab="Syntax"
--rpc-enabled
```

```bash tab="Example Configuration File"
rpc-enabled=true
```

Set to `true` to enable the JSON-RPC service (RPC over HTTP).
The default is `false`.

### rpc-listen

```bash tab="Syntax"
--rpc-listen=<HOST:PORT>
```

```bash tab="Example Command Line"
# to listen on all interfaces on port 3435
--rpc-listen=0.0.0.0:3435
```

```bash tab="Example Configuration File"
rpc-listen="0.0.0.0:3435"
```

Specifies the host and port on which JSON-RPC listens.
The default is 127.0.0.1:8545.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#exposing-ports). 

### rpc-api

```bash tab="Syntax"
--rpc-api=<api name>[,<api name>...]...
```

```bash tab="Example Command Line"
--rpc-api=ETH,NET,WEB3
```

```bash tab="Example Configuration File"
rpc-api=["ETH","NET","WEB3"]
```

Comma-separated APIs to enable on the JSON-RPC channel.
When you use this option, the `--rpc-enabled` option must also be specified.
The available API options are: `ADMIN`, `ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`, `DEBUG`, and `MINER`.
The default is: `ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`.

!!!note
    :construction: IBFT is not currently supported. Support for IBFT is in active development. 

### rpc-cors-origins

```bash tab="Syntax"
--rpc-cors-origins=<rpcCorsAllowedOrigins> or all
```

```bash tab="Example Command Line"
# You can whitelist one or more domains with a comma-separated list.

--rpc-cors-origins="http://medomain.com","https://meotherdomain.com"
```

```bash tab="Example Configuration File"
rpc-cors-origins=["http://medomain.com","https://meotherdomain.com"]
```

```bash tab="Remix IDE domain example"
# The following allows Remix to interact with your Pantheon node without using MetaMask.

--rpc-cors-origins="http://remix.ethereum.org"
```

Specifies domain URLs for CORS validation.
Domain URLs must be enclosed in double quotes and comma-separated.

Listed domains will be allowed access to node data (whitelisted).
If your client interacts with Pantheon using a browser app (such as Remix using a direct connection or a block explorer), 
you must whitelist the client domains. 

The default value is `"none"`.
If you don't whitelist any domains, you won't be able to use webapps to interact with your Pantheon node.

!!!note
    MetaMask runs as native code so does not require CORS validation.
    If Remix is connecting to the node through MetaMask, it also does not require CORS validation.
    
!!!tip
    For development purposes, you can use `"all"` to accept requests from any domain, but we don't recommend this for production code.

### ws-enabled

```bash tab="Syntax"
--ws-enabled
```

```bash tab="Example Configuration File"
ws-enabled=true
```

Set to `true` to enable the WS-RPC (WebSockets) service.
The default is `false`.

### ws-api

```bash tab="Syntax"
--ws-api=<api name>[,<api name>...]...
```

```bash tab="Example Command Line"
--ws-api=ETH,NET,WEB3
```

```bash tab="Example Configuration File"
ws-api=["ETH","NET","WEB3"]
```

Comma-separated APIs to enable on Websockets channel.
When you use this option, the `--ws-enabled` option must also be specified.
The available API options are: `ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`, `DEBUG`, and `MINER`.
The default is: `ETH`, `NET`, `WEB3`, `CLIQUE`, `IBFT`.

!!!note
    :construction: IBFT is not currently supported. Support for IBFT is in active development. 

### ws-listen

```bash tab="Syntax"
--ws-listen=<HOST:PORT>
```

```bash tab="Example Command Line"
# to listen on all interfaces on port 6174
--ws-listen=0.0.0.0:6174
```

```bash tab="Example Configuration File"
ws-listen="0.0.0.0:6174"
```

Host and port for WS-RPC (Websocket) to listen on.
The default is 127.0.0.1:8546.

!!!note
    This option is not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md#exposing-ports). 

### ws-refresh-delay

```bash tab="Syntax"
--ws-refresh-delay=<refresh delay>
```

```bash tab="Example"
--ws-refresh-delay="10000"
```

Refresh delay for Websocket synchronizing subscription in milliseconds. 
The default is 5000. 


### help

```bash tab="Syntax"
-h, --help
```

Show the help message and exit.

### logging

```bash tab="Syntax"
-l, --logging=<LEVEL>
```

```bash tab="Example Command Line"
--logging=DEBUG
```

```bash tab="Example Configration File"
logging="DEBUG"
```

Sets the logging verbosity.
Log levels are `OFF`, `FATAL`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL`.
Default is `INFO`.

### version

```bash tab="Syntax"
  -V, --version
```

Print version information and exit.

## Commands

Pantheon subcommands are: 

### import

```bash tab="Syntax"
$ pantheon import <block-file>
```

```bash tab="Example"
$ pantheon import /home/me/me_project/mainnet.blocks
```

Imports blocks from the specified file into the blockchain database

### export-pub-key

```bash tab="Syntax"
$ pantheon export-pub-key <key-file>
```

```bash tab="Example"
$ pantheon export-pub-key /home/me/me_project/not_precious_pub_key
```

Exports node public key to the specified file. 