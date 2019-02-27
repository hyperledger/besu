# Creating a Private Network using Clique (Proof of Authority) Consensus Protocol

A private network provides a configurable network for testing. This private network uses the [Clique (Proof of Authority)
consensus protocol](../Consensus-Protocols/Clique.md). 

!!!important
    An Ethereum private network created as described here is isolated but not protected or secure. 
    We recommend running the private network behind a properly configured firewall.

## Prerequisites 

[Pantheon](../Installation/Install-Binaries.md) 

[Curl (or similar web service client)](https://curl.haxx.se/download.html) 

## Steps

To create a private network: 

1. [Create Folders](#1-create-folders)
1. [Get Public Key for Node-1](#2-get-public-key-for-node-1)
1. [Get Address for Node-1](#3-get-address-for-node-1)
1. [Create Genesis File](#4-create-genesis-file)
1. [Delete Database Directory](#5-delete-database-directory)
1. [Start First Node as Bootnode](#6-start-first-node-as-bootnode)
1. [Start Node-2](#7-start-node-2)
1. [Start Node-3](#8-start-node-3)
1. [Confirm Private Network is Working](#9-confirm-private-network-is-working)

### 1. Create Folders 

Each node requires a data directory for the blockchain data. When the node is started, the [node key](../Configuring-Pantheon/Node-Keys.md) is saved in this directory. 

Create directories for your private network, each of the three nodes, and a data directory for each node: 

```bash
Clique-Network/
├── Node-1
│   ├── Node-1-data-path
├── Node-2
│   ├── Node-2-data-path
└── Node-3
    ├── Node-3-data-path
```

### 2. Get Public Key for Node-1

To enable nodes to discover each other, a network requires one or more bootnodes. 
For this private network, we will use Node-1 as the bootnode. This requires obtaining the public key for the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url). 

In the `Node-1` directory, use the [`public-key`](../Reference/Pantheon-CLI-Syntax.md#public-key) subcommand to write 
the [node public key](../Configuring-Pantheon/Node-Keys.md) to the specified file (`publicKeyNode1` in this example):

```bash tab="MacOS"
pantheon --data-path=Node-1-data-path public-key export --to=Node-1-data-path/publicKeyNode1
```

```bash tab="Windows"
pantheon --data-path=Node-1-data-path public-key export --to=Node-1-data-path\publicKeyNode1
```

Your node 1 directory now contains: 
```bash
├── Node-1
    ├── Node-1-data-path
        ├── database
        ├── key
        ├── publicKeyNode1
```
      
The `database` directory contains the blockchain data. 

### 3. Get Address for Node-1 

In Clique networks, the address of at least one initial signer must be included in the genesis file. 
For this Clique network, we will use Node-1 as the initial signer. This requires obtaining the address for Node-1. 

To obtain the address for Node-1, in the `Node-1` directory, use the [`public-key export-address`](../Reference/Pantheon-CLI-Syntax.md#public-key)
subcommand to write the node address to the specified file (`nodeAddress1` in this example)

```bash tab="MacOS"
pantheon --data-path=Node-1-data-path public-key export-address --to=Node-1-data-path/nodeAddress1
```

```bash tab="Windows"
pantheon --data-path=Node-1-data-path public-key export-address --to=Node-1-data-path\nodeAddress1
```

### 4. Create Genesis File 

The genesis file defines the genesis block of the blockchain (that is, the start of the blockchain).
The [Clique genesis file](../Consensus-Protocols/Clique.md#genesis-file) includes the address of Node-1 as the initial signer in the `extraData` field.    

All nodes in a network must use the same genesis file. 

Copy the following genesis definition to a file called `cliqueGenesis.json` and save it in the `Clique-Network` directory: 

```json
{
  "config":{
    "chainId":1981,
    "constantinoplefixblock": 0,
    "clique":{
      "blockperiodseconds":15,
      "epochlength":30000
    }
  },
  "coinbase":"0x0000000000000000000000000000000000000000",
  "difficulty":"0x1",

"extraData":"0x0000000000000000000000000000000000000000000000000000000000000000<Node 1 Address>0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
  "gasLimit":"0xa00000",
  "mixHash":"0x0000000000000000000000000000000000000000000000000000000000000000",
  "nonce":"0x0",
  "timestamp":"0x5c51a607",
  "alloc": {
      "fe3b557e8fb62b89f4916b721be55ceb828dbd73": {
        "privateKey": "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
        "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
        "balance": "0xad78ebc5ac6200000"
      },
      "627306090abaB3A6e1400e9345bC60c78a8BEf57": {
        "privateKey": "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
        "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
        "balance": "90000000000000000000000"
      },
      "f17f52151EbEF6C7334FAD080c5704D77216b732": {
        "privateKey": "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
        "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
        "balance": "90000000000000000000000"
      }
   },
  "number":"0x0",
  "gasUsed":"0x0",
  "parentHash":"0x0000000000000000000000000000000000000000000000000000000000000000"
}
```

In `extraData`, replace `<Node 1 Address>` with the [address for Node-1](#3-get-address-for-node-1) excluding the 0x prefix. 

!!! example
    
    ```json
    {
      ...
    "extraData":"0x0000000000000000000000000000000000000000000000000000000000000000b9b81ee349c3807e46bc71aa2632203c5b4620340000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      ...
    }
    ```

!!! warning 
    Do not use the accounts in `alloc` in the genesis file on mainnet or any public network except for testing.
        
    The private keys are displayed which means the accounts are not secure.  

### 5. Delete Database Directory

Delete the `database` directory created when [getting the public key for Node-1](#2-get-public-key-for-node-1).
The node cannot be started with the Clique genesis file while the previously generated data is in the `database` directory. 

### 6. Start First Node as Bootnode

Start Node-1:

```bash tab="MacOS"
pantheon --data-path=Node-1-data-path --genesis-file=../cliqueGenesis.json --bootnodes --network-id 123 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all"      
```

```bash tab="Windows"
pantheon --data-path=Node-1-data-path --genesis-file=..\cliqueGenesis.json --bootnodes --network-id 123 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all"    
```

The command line specifies: 

* No arguments for the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option because this is your bootnode
* JSON-RPC API is enabled using the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option
* ETH,NET, and CLIQUE APIs are enabled using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option
* All hosts can access the HTTP JSON-RPC API using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option
* All domains can access the node using the HTTP JSON-RPC API using the [`--rpc-http-cors-origins`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-cors-origins) option 

### 7. Start Node-2 

You need the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) for Node-1 to specify Node-1 as a bootnode. 

Start another terminal, change to the `Node-2` directory and start Node-2 replacing the enode URL with your bootonde:
 
```bash tab="MacOS"
pantheon --data-path=Node-2-data-path --genesis-file=../cliqueGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

```bash tab="Windows"
pantheon --data-path=Node-2-data-path --genesis-file=..\cliqueGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

The command line specifies: 

* Different port to Node-1 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
* Different port to Node-1 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
* Enode URL for Node-1 using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option.
* Data directory for Node-2 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
* Other options as for [Node-1](#5-start-first-node-as-bootnode).


### 8. Start Node-3

Start another terminal, change to the `Node-3` directory and start Node-3 replacing the enode URL with your bootnode: 

```bash tab="MacOS"
pantheon --data-path=Node-3-data-path --genesis-file=../cliqueGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

```bash tab="Windows"
pantheon --data-path=Node-3-data-path --genesis-file=..\cliqueGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,CLIQUE --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

The command line specifies: 

 * Different port to Node-1 and Node-2 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
 * Different port to Node-1 and Node-2 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
 * Data directory for Node-3 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
 * Bootnode as for [Node-2](#6-start-node-2).
 * Other options as for [Node-1](#5-start-first-node-as-bootnode). 

### 9. Confirm Private Network is Working 

Start another terminal, use curl to call the JSON-RPC API [`net_peerCount`](../Reference/JSON-RPC-API-Methods.md#net_peercount) method and confirm the nodes are functioning as peers: 

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' localhost:8545
```

The result confirms Node-1 has two peers (Node-2 and Node-3):
```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x2"
}
```

## Next Steps 

Look at the logs displayed to confirm Node-1 is producing blocks and Node-2 and Node-3 are importing blocks. 

Use the [Clique API to add](../Consensus-Protocols/Clique.md#adding-and-removing-signers) Node-2 or Node-3 as a signer. 

!!! note
    To add Node-2 or Node-3 as a signer you need the [node address as when specifying Node-1](#2-get-address-for-node-1) as the initial signer. 

Import accounts to MetaMask and send transactions as described in the [Private Network Quickstart Tutorial](Private-Network-Quickstart.md#creating-a-transaction-using-metamask)

!!! info 
    Pantheon does not implement [private key management](../Using-Pantheon/Account-Management.md).

## Stop Nodes

When finished using the private network, stop all nodes using ++ctrl+c++ in each terminal window. 

!!!tip
    To restart the Clique network in the future, start from [6. Start First Node as Bootnode](#6-start-first-node-as-bootnode). 
