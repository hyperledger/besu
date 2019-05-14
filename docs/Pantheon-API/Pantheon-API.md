description: Pantheon API
<!--- END of page meta data -->

Access the [Pantheon API](../Reference/Pantheon-API-Methods.md) using:

* [JSON-RPC over HTTP or WebSockets](Using-JSON-RPC-API.md) 
* [RPC Pub/Sub over WebSockets](RPC-PubSub.md)
* GraphQL RPC over HTTP

Information applying to JSON-RPC, RPC Pub/Sub, and GraphQL is included below. 

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