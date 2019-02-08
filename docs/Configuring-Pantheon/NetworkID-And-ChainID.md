description: Pantheon network ID and chain ID implementation
<!--- END of page meta data -->

# Network ID and Chain ID

Ethereum networks have a network ID and a chain ID. The network ID defaults to the chain ID specified 
in the genesis file.

!!! example "Chain ID in Gensis File"
    ```json
    {
      "config": {
        "ethash": {
        },
         "chainID": 1981
      },
      ...
    }
    ```

For most networks including MainNet and the public testnets, the network ID and the chain ID are the
same and are specified in the genesis file.

The network ID and chain ID are automatically defined by Pantheon when connecting to networks specified 
using the [`--network`](../Reference/Pantheon-CLI-Syntax.md#network) option:

- **MainNet:** chain-id ==1==, network-id ==1==
- **Rinkeby:** chain-id  ==4==, network-id ==4==
- **Ropsten:** chain-id ==3==, network-id ==3==
- **Dev:** chain-id ==2018==, network-id ==2018==

When using the [`--network=dev`](../Reference/Pantheon-CLI-Syntax.md#network) or 
[`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) options, you can override the 
network ID using the [`--network-id`](../Reference/Pantheon-CLI-Syntax.md#network-id) option. 

