description: Starting Pantheon
<!--- END of page meta data -->

# Starting Pantheon

Pantheon nodes can be used for varying purposes as described in the [Overview](../index.md).
Nodes can connect to the Ethereum mainnet, public testnets such as Ropsten, or private networks.

## Prerequisites

[Pantheon Installed](../Installation/Overview.md)

## Local Block Data

When connecting to a network other than the network previously connected to, you must either delete the local block data 
or use the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option to specify a different data directory. 

To delete the local block data, delete the `database` directory in the `pantheon/build/distribution/pantheon-<version>` directory.

## Genesis Configuration 

Pantheon specifies the genesis configuration, and sets the network ID and bootnodes when connecting 
to [Mainnet](#run-a-node-on-ethereum-mainnet), [Goerli](#run-a-node-on-goerli-testnet), [Rinkeby](#run-a-node-on-rinkeby-testnet), and [Ropsten](#run-a-node-on-ropsten-testnet). 

When [`--network=dev`](../Reference/Pantheon-CLI-Syntax.md#network) is specified, Pantheon uses the 
development mode genesis configuration with a fixed low difficulty.
A node started with [`--network=dev`](../Reference/Pantheon-CLI-Syntax.md#network) has an empty bootnodes list by default.

The genesis files defining the genesis configurations are in the [Pantheon source files](https://github.com/PegaSysEng/pantheon/tree/master/config/src/main/resources). 

To define a genesis configuration, create a genesis file (for example, `genesis.json`) and specify the file 
using the [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) option.

## Confirm Node is Running

If you have started Pantheon with the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option, use [cURL](https://curl.haxx.se/) to 
call [JSON-RPC API methods](../Reference/Pantheon-API-Methods.md) to confirm the node is running.

!!!example

    * `eth_chainId` returns the chain ID of the network. 
    
        ```bash
        curl -X POST --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' localhost:8545
        ```
    
    * `eth_syncing` returns the starting, current, and highest block. 
    
        ```bash
        curl -X POST --data '{"jsonrpc":"2.0","method":"eth_syncing","params":[],"id":1}' localhost:8545
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

## Run a Node for Testing 

To run a node that mines blocks at a rate suitable for testing purposes: 

```bash
pantheon --network=dev --miner-enabled --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-http-cors-origins="all" --host-whitelist="*" --rpc-ws-enabled --rpc-http-enabled --data-path=/tmp/tmpDatdir
```

Alternatively, use the following [configuration file](../Configuring-Pantheon/Using-Configuration-File.md) 
on the command line to start a node with the same options as above: 
```toml
network="dev"
miner-enabled=true
miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
rpc-http-cors-origins=["all"]
host-whitelist=["*"]
rpc-ws-enabled=true
rpc-http-enabled=true
data-path="/tmp/tmpdata-path"
```

## Run a Node on Ropsten Testnet 

To run a node on Ropsten: 

```bash
pantheon --network=ropsten
```

To run a node on Ropsten with the HTTP JSON-RPC service enabled and allow Remix to access the node: 

```bash
pantheon  --network=ropsten --rpc-http-enabled --rpc-http-cors-origins "http://remix.ethereum.org"
```
    
## Run a Node on Rinkeby Testnet

To run a node on Rinkeby specifying a data directory: 

```bash
pantheon --network=rinkeby --data-path=<path>/<rinkebydata-path>
```
Where `<path>` and `<rinkebydata-path>` are the path and directory where the Rinkeby chain data is to be saved.

## Run a Node on Goerli Testnet

To run a node on [Goerli](https://github.com/goerli/testnet) specifying a data directory: 

```bash
pantheon --network=goerli --data-path=<path>/<goerlidata-path>
```

Where `<path>` and `<goerlidata-path>` are the path and directory where the Goerli chain data is to be saved. 
   
## Run a Node on Ethereum Mainnet 

To run a node on the Ethereum mainnet: 

```bash
pantheon
```

To run a node on mainnet with the HTTP JSON-RPC service enabled and available for localhost only: 

```bash
pantheon --rpc-http-enabled
```
