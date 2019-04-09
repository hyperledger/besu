description: Managing Pantheon peers 
<!--- END of page meta data -->

# Managing Peers 
 
## Limiting Peers

Limiting peers reduces the bandwidth used by Pantheon. It also reduces the CPU time and disk access 
used to manage and respond to peers.  
 
Use the [`--max-peers`](../../Reference/Pantheon-CLI-Syntax.md#max-peers) command line option to reduce 
the maximum number of peers. The default is 25.

## No Discovery

The [`--discovery-enabled`](../../Reference/Pantheon-CLI-Syntax.md#discovery-enabled) command line option 
can be used to disable P2P peer discovery.
Set this option to `false` if you are running a test node or a network with [static nodes](#static-nodes).

## Static Nodes

Static nodes are configured nodes that remain connected rather than nodes connected through P2P discovery. 
Static nodes attempt to maintain connectivity. If a connection goes down to a static node, 
the node attempts to reconnect every 60 seconds.

To configure a network of static nodes: 

1. List [enode URLs](../Node-Keys.md#enode-url) of the nodes in the [`static-nodes.json` file](#static-nodesjson-file).

1. Save the `static-nodes.json` file in the data directory of each node. 

1. Start Pantheon with discovery disabled using [`--discovery-enabled=false`](../../Reference/Pantheon-CLI-Syntax.md#discovery-enabled).   

To modify the static peers at run time, use the [`admin_addPeer`](../../Reference/JSON-RPC-API-Methods.md#admin_addpeer) 
and [`admin_removePeer`](../../Reference/JSON-RPC-API-Methods.md#admin_removepeer) JSON-RPC API methods. 

!!! note
    Runtime modifications of static nodes are not persisted between runs. The `static-nodes.json` file
    is not updated by `admin_addPeer` and `admin_removePeer` methods. 
    
    Nodes outside of the static nodes are not prevented from connecting.  To prevent nodes from connecting,
    use [Permissioning](../../Permissions/Permissioning-Overview.md). 
    
!!! caution 
    If the added peer does not appear in the peer list (returned by [`admin_peers`](../../Reference/JSON-RPC-API-Methods.md#admin_peers)),
    check the supplied [enode URL](../Node-Keys.md#enode-url) is correct, the node is running, the node is listening for 
    TCP connections on the endpoint, and has not reached the [maximum number of peers](#limiting-peers).
    
### static-nodes.json File

The `static-nodes.json` file must be located in the data directory (specified by [`--data-path`](../../Reference/Pantheon-CLI-Syntax.md#data-path))
and contain a JSON array of [enode URLs](../Node-Keys.md#enode-url).

!!! example 
    ```json
    [
    "enode://cea71cb65a471037e01508cebcc178f176f9d5267bf29507ea1f6431eb6a5dc67d086dc8dc54358a72299dab1161febc5d7af49d1609c69b42b5e54544145d4f@127.0.0.1:30303",
    "enode://ca05e940488614402705a6b6836288ea902169ecc67a89e1bd5ef94bc0d1933f20be16bc881ffb4be59f521afa8718fc26eec2b0e90f2cd0f44f99bc8103e60f@127.0.0.1:30304"    
    ]
    ``` 

!!! note
    Each node has a `static-nodes.json` file. We recommend each node in the network has the same `static-nodes.json` file. 

## Monitoring Peer Connections

JSON-RPC API methods to monitor peer connections include: 

* [`net_peerCount`](../../Reference/JSON-RPC-API-Methods.md#net_peercount)
* [`admin_peers`](../../Reference/JSON-RPC-API-Methods.md#admin_peers)
* [`debug_metrics`](../../Reference/JSON-RPC-API-Methods.md#debug_metrics)

## Node Connections

The default logging configuration does not list node connection and disconnection messages.  

To enable listing of node connection and disconnection messages, specify the 
[`--logging`](../../Reference/Pantheon-CLI-Syntax.md#logging) command line option `--logging=DEBUG`.
For more verbosity, specify `--logging=TRACE`.  

The console logs connection and disconnection events when the log level is `DEBUG` or higher.  
If `Successfully accepted connection from ...` is displayed, connections are getting through the firewalls. 

!!! example "Example log output"
    `2018-10-16 12:37:35.479-04:00 | nioEventLoopGroup-3-1 | INFO  | NettyP2PNetwork | Successfully accepted connection from 0xa979fb575495b8d6db44f750317d0f4622bf4c2aa3365d6af7c284339968eef29b69ad0dce72a4d8db5ebb4968de0e3bec910127f134779fbcb0cb6d3331163c`
