description: Starting Pantheon
<!--- END of page meta data -->

# Starting Pantheon

Pantheon nodes can be used for varying purposes as described in the [Overview](../index.md).
Nodes can connect to the Ethereum mainnet, public testnets such as Ropsten, or private networks.

## Prerequisites

[Pantheon Installed](../Installation/Overview.md)

## Local Block Data

When connecting to a network other than the network previously connected to, you must either delete the local block data 
or use the [`--datadir`](../Reference/Pantheon-CLI-Syntax.md#datadir) option to specify a different data directory. 

To delete the local block data, delete the `database` directory in the `pantheon/build/distribution/pantheon-<version>` directory.

## Genesis Configuration 

Pantheon specifies the genesis configuration, and sets the network ID and bootnodes when connecting to [Mainnet](#run-a-node-on-ethereum-mainnet), 
[Rinkeby](#run-a-node-on-rinkeby-testnet), and [Ropsten](#run-a-node-on-ropsten-testnet). 

When [`--dev-mode`](../Reference/Pantheon-CLI-Syntax.md#dev-mode) is specified, Pantheon uses the development mode genesis configuration.

The genesis files defining the genesis configurations are in the [Pantheon source files](https://github.com/PegaSysEng/pantheon/tree/master/config/src/main/resources). 

To define a genesis configuration, create a genesis file (for example, `genesis.json`) and specify the file 
using the [`--genesis`](../Reference/Pantheon-CLI-Syntax.md#genesis) option.

## Confirm Node is Running

If you have started Pantheon with the [`--rpc-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-enabled) option, use [cURL](https://curl.haxx.se/) to 
call [JSON-RPC API methods](../Reference/JSON-RPC-API-Methods.md) to confirm the node is running.

!!!example

    * `eth_chainId` returns the chain ID of the network. 
    
        ```bash
        $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' 127.0.0.1:8545
        ```
    
    * `eth_syncing` returns the starting, current, and highest block. 
    
        ```bash
        $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_syncing","params":[],"id":1}' 127.0.0.1:8545
        ``` 
          
        For example, after connecting to mainnet `eth_syncing` will return something similar to: 
        
        ```json
        {
          "jsonrpc" : "2.0",
          "id" : 1,
          "result" : {
            "startingBlock" : "0x0",
            "currentBlock" : "0x2d0",
            "highestBlock" : "0x66c0"
          }
        }
        ```

## Run a Node on Ethereum  Mainnet 

To run a node on the Ethereum mainnet: 

```bash
$ bin/pantheon
```

To run a node on mainnet with the HTTP JSON-RPC service enabled: 

```bash
$ bin/pantheon --rpc-enabled
```

## Run a Node on Ropsten Testnet 

!!!note
    From v0.8.2 or when [building from source](../Installation/Overview.md), use the [`--ropsten` option](../Reference/Pantheon-CLI-Syntax.md#options) 
    instead of the following options. For v0.8.1, use the following options.

Replace `<path>` with the path to the `/pantheon` directory. 

To run a node on Ropsten: 

```bash
$ bin/pantheon --network-id=3 --genesis=<path>/pantheon/ethereum/core/src/main/resources/ropsten.json --bootnodes=enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303,enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303
```

To run a node on Ropsten with the HTTP JSON-RPC service enabled and allow Remix to access the node: 

```bash
$ bin/pantheon --rpc-enabled --rpc-cors-origins "http://remix.ethereum.org" --network-id=3 --genesis=<path>/pantheon/ethereum/core/src/main/resources/ropsten.json --bootnodes=enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@52.232.243.152:30303,enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.81.208.223:30303
```

## Run a Node on Rinkeby Testnet

Replace `<path>` with the path where the Rinkeby chain data is to be saved. 

To run a node on Rinkeby specifying a data directory: 

```bash
$ bin/pantheon --rinkeby --datadir=<path>/rinkebyDataDir
```

## Run a Node for Testing 

To run a node that mines blocks at a rate suitable for testing purposes: 

```bash
$ bin/pantheon --dev-mode --network-id="2018" --bootnodes= --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-cors-origins "all" --ws-enabled --rpc-enabled
```