description: Pantheon API
<!--- END of page meta data -->

Access the [Pantheon API](../Reference/Pantheon-API-Methods.md) using:

* [JSON-RPC over HTTP or WebSockets](Using-JSON-RPC-API.md) 
* [RPC Pub/Sub over WebSockets](RPC-PubSub.md)
* [GraphQL over HTTP](GraphQL.md)

Information applying to JSON-RPC, RPC Pub/Sub, and GraphQL is included below. 

## Enabling API Access 

Use the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled), [`--ws-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-enabled),
and [`--graphql-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#graphql-http-enabled) options to enable API access.

## Service Hosts

Use the [`--rpc-http-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host), [`--rpc-ws-host`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-host),
and [`--graphql-http-host`](../Reference/Pantheon-CLI-Syntax.md#graphql-http-host) options to specify the host on which the API service listens. 
The default host is 127.0.0.1.  

Set the host to `0.0.0.0` to allow remote connections. 

!!! caution 
    Setting the host to 0.0.0.0 exposes the API service connection on your node to any remote connection. In a 
    production environment, ensure you use a firewall to avoid exposing your node to the internet.  

## Service Ports

Use the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port), [`--rpc-ws-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-port),
and [`--graphql-http-port`](../Reference/Pantheon-CLI-Syntax.md#graphql-http-port) options to specify the port on which the API service listens. 

The default ports are: 

* 8545 for JSON-RPC over HTTP
* 8546 for JSON-RPC over WebSockets
* 8547 for GraphQL over HTTP

Ports must be [exposed appropriately](../Configuring-Pantheon/Networking/Managing-Peers.md#port-configuration).

## Host Whitelist 

To prevent DNS rebinding, incoming HTTP requests, WebSockets connections, and GraphQL requests are only accepted from hostnames 
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
    
## Not Supported by Pantheon

### Account Management 

Account management relies on private key management in the client which is not implemented by Pantheon. 

Use [`eth_sendRawTransaction`](../Reference/Pantheon-API-Methods.md#eth_sendrawtransaction) to send signed transactions; `eth_sendTransaction` is not implemented. 

Use third-party wallets for [account management](../Using-Pantheon/Account-Management.md). 

### Protocols

Pantheon does not implement the Whisper and Swarm protocols.