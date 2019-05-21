description: Configuring Pantheon at the network level compared to the node level 
<!--- END of page meta data -->

# Network vs Node Configuration 

Pantheon is configured at the network level and the node level. 

Network wide settings are specified in the [genesis file](Config-Items.md).  Examples include `evmStackSize` and the 
[consensus mechanism](../Consensus-Protocols/Overview-Consensus.md). 

Node settings are specified on the command line or in the [node configuration file](Using-Configuration-File.md). 
For example, the [JSON-RPC API methods to enable](../Reference/Pantheon-API-Methods.md) or the 
[data directory](../Reference/Pantheon-CLI-Syntax.md#data-path) for the node. 