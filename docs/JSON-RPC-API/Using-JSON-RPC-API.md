description: How to use Pantheon JSON-RPC API
<!--- END of page meta data -->

# Using the JSON-RPC API

## Postman

Use the button to import our collection of examples to [Postman](https://www.getpostman.com/). 

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/c765d7d22b055c42a510)

## Endpoint Host and Port

The placeholder
`<JSON-RPC-http-endpoint:port>` and `<JSON-RPC-ws-endpoint:port>` represents an endpoint (IP address and port) 
of the JSON-RPC service of a Pantheon node for HTTP and WebSocket requests.

To enable JSON-RPC over HTTP or WebSockets, use the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) 
and [`--rpc-ws-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-enabled) options.

Use the [--rpc-http-host](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host) and [--rpc-ws-host](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-host) 
options to specify the host on which the JSON-RPC listens. The default host is 127.0.0.1 for HTTP and WebSockets.  

Set the host to `0.0.0.0` to allow remote connections. 

!!! caution 
    Setting the host to 0.0.0.0 exposes the RPC connection on your node to any remote connection. In a 
    production environment, ensure you use a firewall to avoid exposing your node to the internet.  

Use the [--rpc-http-port](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) and [--rpc-ws-port](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port)
options to specify the port on which the JSON-RPC listens. The default ports are: 

* 8545 for HTTP
* 8546 for WebSockets

Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Managing-Peers.md#port-configuration).

## Geth Console 

The geth console is a REPL (Read, Evaluate, & Print Loop) Javascript console. Use JSON-RPC APIs supported by geth and 
Pantheon directly in the console.  

To use the geth console with Pantheon: 

1. Start Pantheon with the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option. 

1. Specify which APIs to enable using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option. 

1. Start the geth console specifying the JSON-RPC endpoint: 
   ```bash
    geth attach http://localhost:8545
   ``` 
   
Use the geth console to call [JSON-RPC API methods](../Reference/JSON-RPC-API-Methods.md) that geth and Pantheon share. 

!!! example 
    ```bash
    eth.syncing
    ```
    
## Host Whitelist 

To prevent DNS rebinding, incoming HTTP requests and WebSockets connections are only accepted from hostnames 
specified using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option. 
By default, `localhost` and `127.0.0.1` are accepted.

If your application publishes RPC ports, specify the hostnames when starting Pantheon.
 
!!! example
    ```bash
    pantheon --host-whitelist=example.com
    ```
    
Specify * for `--host-whitelist` to effectively disable host protection.

!!! caution 
    Specifying * for `--host-whitelist` is not recommended for production code.

## JSON-RPC Authentication 

[Authentication](Authentication.md) is disabled by default. 

## HTTP and WebSocket Requests

### HTTP

To make RPC requests over HTTP, you can use [`curl`](https://curl.haxx.se/download.html).

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
```

### WebSockets

To make RPC requests over WebSockets, you can use [wscat](https://github.com/websockets/wscat), a Node.js based command-line tool.

First connect to the WebSockets server using `wscat` (you only need to connect once per session):

```bash
wscat -c ws://<JSON-RPC-ws-endpoint:port>
```

After the connection is established, the terminal displays a '>' prompt.
Send individual requests as a JSON data package at each prompt:

```bash
{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":53}
```

!!! note 
    `wscat` does not support headers. [Authentication](Authentication.md) requires an authentication token to be passed in the 
    request header. To use authentication with WebSockets, an app that supports headers is required. 

### API Methods Enabled by Default

The `ETH`, `NET`, and `WEB3` API methods are enabled by default. 

Use the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) or [`--rpc-ws-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) 
options to enable the `ADMIN`, `CLIQUE`, `DEBUG`, `EEA`, `IBFT`, `MINER`, `PERM`, and `TXPOOL` API methods.

## Block Parameter

When you make requests that might have different results depending on the block accessed, 
the block parameter specifies the block. 
Several methods, such as [eth_getTransactionByBlockNumberAndIndex](../Reference/JSON-RPC-API-Methods.md#eth_gettransactionbyblocknumberandindex), have a block parameter.

The block parameter can have the following values:

* `blockNumber` : `quantity` - Block number. Can be specified in hexadecimal or decimal. 0 represents the genesis block.
* `earliest` : `tag` - Earliest (genesis) block. 
* `latest` : `tag` - Last block mined.
* `pending` : `tag` - Last block mined plus pending transactions. Use only with [eth_getTransactionCount](../Reference/JSON-RPC-API-Methods.md#eth_gettransactioncount).  

## Not Supported by Pantheon

### Account Management 

Account management relies on private key management in the client which is not implemented by Pantheon. 

Use [`eth_sendRawTransaction`](../Reference/JSON-RPC-API-Methods.md#eth_sendrawtransaction) to send signed transactions; `eth_sendTransaction` is not implemented. 

Use third-party wallets for [account management](../Using-Pantheon/Account-Management.md). 

### Protocols

Pantheon does not implement the Whisper and Swarm protocols.