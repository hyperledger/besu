description: Local Permissioning 
<!--- END of page meta data -->

# Local Permissioning 

Local permissioning supports node and account whitelisting. 

## Node Whitelisting 

Node whitelisting is specified by the nodes whitelist in the [permissions configuration file](#permissions-configuration-file) file. 
When node whitelisting is enabled, communication is restricted to only nodes in the whitelist. 

!!! example "Nodes Whitelist in Permissions Configuration File"
    `nodes-whitelist=["enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.9:4567","enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.169.0.9:4568"]`

Node whitelisting is at the node level. That is, each node in the network has a [permissions configuration file](#permissions-configuration-file)
file in the [data directory](../Reference/Pantheon-CLI-Syntax.md#data-path) for the node.  

To update the nodes whitelist when the node is running, use the JSON-RPC API methods:
 
* [perm_addNodesToWhitelist](../Reference/Pantheon-API-Methods.md#perm_addnodestowhitelist)
* [perm_removeNodesFromWhitelist](../Reference/Pantheon-API-Methods.md#perm_removenodesfromwhitelist)

Alternatively, update the [`permissions_config.toml`](#permissions-configuration-file) file directly and use the 
[`perm_reloadPermissionsFromFile`](../Reference/Pantheon-API-Methods.md#perm_reloadpermissionsfromfile) method 
to update the whitelists. 

Updates to the permissions configuration file persist across node restarts. 

To view the nodes whitelist, use the [perm_getNodesWhitelist](../Reference/Pantheon-API-Methods.md#perm_getnodeswhitelist) method. 

!!! note
    Each node has a [permissions configuration file](#permissions-configuration-file) which means nodes can have different nodes whitelists. 
    This means nodes may be participating in the network that are not on the whitelist of other nodes in the network. 
    We recommend each node in the network has the same nodes whitelist. 
    
    On-chain permissioning is under development. On-chain permissioning will use one on-chain 
    nodes whitelist. 
    
!!! example "Example Different Node Whitelists" 

    Node 1 Whitelist = [Node 2, Node 3] 
    
    Node 2 Whitelist = [Node 3, Node 5] 
    
    Node 5 is participating in the same network as Node 1 even though Node 1 does not have Node 5 on their whitelist.

### Bootnodes

The bootnodes must be included in the nodes whitelist or Pantheon does not start when node permissions are enabled. 

!!! example 
    If you start Pantheon with specified bootnodes and have node permissioning enabled:
    
     ```bash
     --bootnodes="enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304","enode://7b61d5ee4b44335873e6912cb5dd3e3877c860ba21417c9b9ef1f7e500a82213737d4b269046d0669fb2299a234ca03443f25fe5f706b693b3669e5c92478ade@127.0.0.1:30305" 
     ```
    
    The `nodes-whitelist` in the [permissions configuration file](#permissions-configuration-file) must contain the specified bootnodes. 

### Enabling Node Whitelisting     

To enable node whitelisting, specify the [`--permissions-nodes-config-file-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file-enabled) option 
when starting Pantheon. 

The `PERM` API methods are not enabled by default. Use the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) 
or [`--rpc-ws-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `PERM` API methods.

## Account Whitelisting 

Account whitelisting is specified by the accounts whitelist in the [permissions configuration file](#permissions-configuration-file).
A node with account permissioning accepts transactions only from accounts in the accounts whitelist. 

!!! example "Accounts Whitelist in Permissions Configuration File"
    `accounts-whitelist=["0x0000000000000000000000000000000000000009"]`
    
Account whitelisting is at the node level. That is, each node in the network has a [permisssions configuration file](#permissions-configuration-file)
in the [data directory](../Reference/Pantheon-CLI-Syntax.md#data-path) for the node.    
    
Transactions are validated against the accounts whitelist at the following points: 

1. Submitted by JSON-RPC API method [`eth_sendRawTransaction`](../Reference/Pantheon-API-Methods.md#eth_sendrawtransaction) 
1. Received via propagation from another node 
1. Added to a block by a mining node 

Once added to a block, the transactions are not validated against the whitelist when received by another node. That is, a node 
can synchronise and add blocks containing transactions from accounts that are not on the accounts whitelist of that node.      
    
!!! example "Example Different Account Whitelists"
    Node 1 Whitelist = [Account A, Account B]
    
    Node 2 Whitelist = [Account B, Account C]
    
    Mining Node Whitelist = [Account A, Account B]
    
    Account A submits a transaction on Node 1. Node 1 validates and propagates the transaction. The Mining Node receives the transaction, 
    validates it is from an account in the Mining Node accounts whitelist, and includes the transaction in the block. Node 2 receives and 
    adds the block created by the Mining Node.
     
    Node 2 now has a transaction in the blockchain from Account A which is not on the accounts whitelist for Node 2.   

!!! note
    Each node has a [permissions configuration file](#permissions-configuration-file) which means nodes in the network can have different accounts whitelists. 
    This means a transaction can be successfully submitted by Node A from an account in the Node A whitelist but rejected by 
    Node B to which it is propagated if the account is not in the Node B whitelist. 
    We recommend each node in the network has the same accounts whitelist. 

To update the accounts whitelist when the node is running, use the JSON-RPC API methods: 

* [`perm_addAccountsToWhitelist`](../Reference/Pantheon-API-Methods.md#perm_addaccountstowhitelist)
* [`perm_removeAccountsFromWhitelist`](../Reference/Pantheon-API-Methods.md#perm_removeaccountsfromwhitelist)

Alternatively, update the [`permissions_config.toml`](#permissions-configuration-file) file directly and use the 
[`perm_reloadPermissionsFromFile`](../Reference/Pantheon-API-Methods.md#perm_reloadpermissionsfromfile) method 
to update the whitelists.

Updates to the permissions configuration file persist across node restarts.

To view the accounts whitelist, use the [`perm_getAccountsWhitelist`](../Reference/Pantheon-API-Methods.md#perm_getaccountswhitelist) method.

### Enabling Account Whitelisting 

To enable account whitelisting, specify the [`--permissions-accounts-config-file-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-enabled) option 
when starting Pantheon. 

The `PERM` API methods are not enabled by default. Use the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) 
or [`--rpc-ws-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `PERM` API methods.

## Permissions Configuration File 

The permissions configuration file contains the nodes and accounts whitelists. If the [`--permissions-accounts-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file)
and [`permissions-nodes-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file) 
options are not specified, the permissions configuration file must be called `permissions_config.toml` and
must be in the [data directory](../Reference/Pantheon-CLI-Syntax.md#data-path) for the node.

The accounts and nodes whitelists can be specified in the same file or in separate files for accounts and nodes. 

Use the [`--permissions-accounts-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file) 
and [`permissions-nodes-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file) 
options to specify a permissions configuration file (or separate files for accounts and nodes) in any location.
 
!!!note
    The [`--permissions-accounts-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file) 
    and [`permissions-nodes-config-file`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file) 
    options are not used when running Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md). 
    Use a bind mount to [specify a permissions configuration file with Docker](../Getting-Started/Run-Docker-Image.md#permissions-configuration-file).

!!! example "Example Permissions Configuration File"  
    ```toml 
    accounts-whitelist=["0xb9b81ee349c3807e46bc71aa2632203c5b462032", "0xb9b81ee349c3807e46bc71aa2632203c5b462034"]
    
    nodes-whitelist=["enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304","enode://7b61d5ee4b44335873e6912cb5dd3e3877c860ba21417c9b9ef1f7e500a82213737d4b269046d0669fb2299a234ca03443f25fe5f706b693b3669e5c92478ade@127.0.0.1:30305"]
    ```


