description: Pantheon Clique Proof-of-Authority (PoA) consensus protocol implementation
path: blob/master/ethereum/core/src/main/resources/
source: rinkeby.json
<!--- END of page meta data -->

# Testing and Developing Nodes

## Bootnodes

Bootnodes are used to initially discover peers. 

### Mainnet and Public Testnets

For mainnet and Rinkeby, Pantheon predefines a list of enonde URLs. For Ropsten, bootnodes are specified using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option. 

### Private Networks

To start a bootnode for a private network:

1.  Export the public key to a file:

    !!! example
        ```bash
        pantheon export-pub-key bootnode
        ```
        The node public key is exported to the `bootnode` file.
    
2. Start the bootnode, specifying:

    * An empty string for the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option because this is the bootnode. 
    * The network ID for your private network.
    
    !!! example
        ```bash
        pantheon --bootnodes="" --network-id 123 
         ```
     
To specify this bootnode for another node, the enode URL for the `--bootnodes` option is `enode://<id>@<host:port>` where:

* `<id>` is the node public key written to the specified file (`bootnode` in the above example) excluding the initial 0x. 
* `<host:port>` is the host and port the bootnode is listening on for P2P peer discovery. Specified by the `--p2p-listen` option for the bootnode (default is `127.0.0.1:30303`).

!!! example
    If the `--p2p-listen` option is not specified and the node public key exported is `0xc35c3ec90a8a51fd5703594c6303382f3ae6b2ecb9589bab2c04b3794f2bc3fc2631dabb0c08af795787a6c004d8f532230ae6e9925cbbefb0b28b79295d615f`
    
    Then the enode URL is:
    `enode://c35c3ec90a8a51fd5703594c6303382f3ae6b2ecb9589bab2c04b3794f2bc3fc2631dabb0c08af795787a6c004d8f532230ae6e9925cbbefb0b28b79295d615f@127.0.0.1:30303` 

!!! info
    The default host and port for P2P peer discovery is `127.0.0.1:30303`. The `--p2p-listen` option can be used to specify a host and port. 

To start a node specifying the bootnode for P2P discovery:

!!! example
    ```bash
    pantheon --datadir=/tmp/pantheon/30301 --p2p-listen=127.0.0.1:30301 --network-id=123 --bootnodes=enode://c35c3ec90a8a51fd5703594c6303382f3ae6b2ecb99bab2c04b3794f2bc3fc2631dabb0c08af795787a6c004d8f532230ae6e9925cbbefb0b28b79295d615f@127.0.0.1:30303
    ``` 