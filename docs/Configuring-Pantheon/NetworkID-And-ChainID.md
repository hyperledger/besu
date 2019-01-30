description: Pantheon network ID and chain ID implementation
<!--- END of page meta data -->

# Network ID and Chain ID

Ethereum networks have a **network ID** and a **chain ID**. The network ID can be specified using the 
[`--network-id`](../Reference/Pantheon-CLI-Syntax.md#network-id) option and the chain ID is specified 
in the genesis file.

For most networks including MainNet and the public testnets, the network ID and the chain ID are the
same and Pantheon network ID values are defined from the genesis chain ID.

The network ID is automatically set by Pantheon to the chain ID when connecting to the Ethereum networks:

- **MainNet:** chain-id ==1==, network-id ==1==
- **Rinkeby:** chain-id  ==4==, network-id ==4==
- **Ropsten:** chain-id ==3==, network-id ==3==
- **Dev:** chain-id ==2018==, network-id ==2018==

When using the [`--network=dev`](../Reference/Pantheon-CLI-Syntax.md#network) or 
[`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) options, you can override the 
network ID using the [`--network-id`](../Reference/Pantheon-CLI-Syntax.md#network-id) option. 

