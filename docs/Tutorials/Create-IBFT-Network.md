# Creating a Private Network using IBFT 2.0 (Proof of Authority) Consensus Protocol

A private network provides a configurable network for testing. This private network uses the [IBFT 2.0 (Proof of Authority)
consensus protocol](../Consensus-Protocols/IBFT.md). 

!!!important
    An Ethereum private network created as described here is isolated but not protected or secure. 
    We recommend running the private network behind a properly configured firewall.

## Prerequisites 

[Pantheon](../Installation/Install-Binaries.md) 

[Curl (or similar web service client)](https://curl.haxx.se/download.html) 

## Steps

To create a private network using IBFT 2.0 with three nodes and one initial validator: 

1. [Create Folders](#1-create-folders)
1. [Get Public Key for Node-1](#2-get-public-key-for-node-1)
1. [Get Node Addresses](#3-get-node-addresses)
1. [Create JSON File to RLP Encode](#4-create-json-file-to-rlp-encode)
1. [RLP Encode Extra Data](#5-rlp-encode-extra-data)
1. [Create Genesis File](#6-create-genesis-file)
1. [Delete Database Directory](#7-delete-database-directory)
1. [Start First Node as Bootnode](#8-start-first-node-as-bootnode)
1. [Start Node-2](#9-start-node-2)
1. [Start Node-3](#10-start-node-3)
1. [Confirm Private Network is Working](#11-confirm-private-network-is-working)

### 1. Create Folders 

Each node requires a data directory for the blockchain data. When the node is started, the [node key](../Configuring-Pantheon/Node-Keys.md) is saved in this directory. 

Create directories for your private network, each of the three nodes, and a data directory for each node: 

```bash
IBFT-Network/
├── Node-1
│   ├── data
├── Node-2
│   ├── data
└── Node-3
    ├── data
```

### 2. Get Public Key for Node-1

The public key of Node-1 is needed for the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url). The enode URL
 is used when starting Node-2 and Node-3 to specify Node-1 is the bootnode. 

In the `Node-1` directory, use the [`public-key export`](../Reference/Pantheon-CLI-Syntax.md#public-key) subcommand to write 
the [node public key](../Configuring-Pantheon/Node-Keys.md) to the specified file (`publicKey`):

```bash tab="MacOS"
pantheon --data-path=data public-key export --to=data/publicKey
```

```bash tab="Windows"
pantheon --data-path=path public-key export --to=data\publicKey
```

Your node 1 directory now contains: 
```bash
├── Node-1
    ├── data
        ├── database
        ├── key
        ├── publicKey
```
      
The `database` directory contains the blockchain data. 

### 3. Get Node Addresses 

In IBFT 2.0 networks, the address of at least one initial validator must be included in the genesis file in the
RLP encoded `extraData` string. For this network, we will use Node-1 as the initial validator. This requires obtaining the address for Node-1. 

To obtain the address for Node-1, in the `Node-1` directory, use the [`public-key export-address`](../Reference/Pantheon-CLI-Syntax.md#public-key)
subcommand to write the node address to the specified file (`nodeAddress`)

```bash tab="MacOS"
pantheon --data-path=data public-key export-address --to=data/nodeAddress
```

```bash tab="Windows"
pantheon --data-path=data public-key export-address --to=data\nodeAddress
```

To vote in validators once the network is running, the node address for the proposed validator is required. In the `Node-2` 
and `Node-3` directories, write the node address for each node to the specified file using the `public-key export-address`
command as for `Node-1`. 

### 4. Create JSON File to RLP Encode

Create a file called `toEncode.json` in the `IBFT-Network` directory that contains the [Node 1 address excluding the 0x prefix](#3-get-node-addresses)
from the `nodeAddress` file in the `Node-1/data` directory: 

```json tab="toEncode.json"
[
 "<Node 1 Address>"
]
```

```json tab="Example"
[
 "9b9f91039843450927b0043ae71cd803e0db0c30"
]
```


### 5. RLP Encode Extra Data 

The `extraData` property in IBFT 2.0 genesis files is an RLP encoding of `[32 Bytes Vanity, List<Validators>, No Vote, Round=Int(0), 0 Seals]`. 

In the `IBFT-Network` directory, use the Pantheon subcommand [`rlp encode`](../Reference/Pantheon-CLI-Syntax.md#rlp) to generate the `extraData` 
RLP string to include in the genesis file. 

```bash tab="MacOS"
pantheon rlp encode --from=toEncode.json --to=rlpEncodedExtraData
```

```bash tab="Windows"
pantheon rlp encode --from=toEncode.json --to=rlpEncodedExtraData
```


### 6. Create Genesis File 

The genesis file defines the genesis block of the blockchain (that is, the start of the blockchain).
The [IBFT 2.0 genesis file](../Consensus-Protocols/IBFT.md#genesis-file) includes the address of Node-1 as the initial validator in the
RLP encoded `extraData` string.    

All nodes in a network must use the same genesis file. 

Copy the following genesis definition to a file called `ibftGenesis.json` and save it in the `IBFT-Network` directory: 

```json
{
  "config": {
    "chainId": 1981,
    "constantinoplefixblock": 0,
    "ibft2": {
      "blockperiodseconds": 2,
      "epochlength": 30000,
      "requesttimeoutseconds": 10
    }
  },
  "nonce": "0x0",
  "timestamp": "0x58ee40ba",
  "extraData": "<RLP Encoded Extra Data>",
  "gasLimit": "0x47b760",
  "difficulty": "0x1",
  "mixHash": "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
  "coinbase": "0x0000000000000000000000000000000000000000",
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
   }
}
```

In `extraData`, copy the [RLP encoded data](#5-rlp-encode-extra-data) from the `rlpEncodedExtraData` file. 

!!! example
    
    ```json
    {
      ...
    "extraData":0xf83ea00000000000000000000000000000000000000000000000000000000000000000d5949b9f91039843450927b0043ae71cd803e0db0c30808400000000c0",
      ...
    }
    ```

!!! warning 
    Do not use the accounts in `alloc` in the genesis file on mainnet or any public network except for testing.
        
    The private keys are displayed which means the accounts are not secure.  

### 7. Delete Database Directories

Delete the `database` directories created when [getting the public key for Node-1](#2-get-public-key-for-node-1) and [addresses for Node-2 and Node-3](#3-get-node-addresses).
The nodes cannot be started with the IBFT 2.0 genesis file while the previously generated data is in the `database` directories. 

### 8. Start First Node as Bootnode

In the `Node-1` directory, start Node-1:

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../ibftGenesis.json --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all"      
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\ibftGenesis.json --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all"    
```

The command line specifies: 

* Data directory for Node-1 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
* JSON-RPC API is enabled using the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option
* ETH,NET, and IBFT APIs are enabled using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option
* All hosts can access the HTTP JSON-RPC API using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option
* All domains can access the node using the HTTP JSON-RPC API using the [`--rpc-http-cors-origins`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-cors-origins) option 

### 9. Start Node-2 

You need the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) for Node-1 to specify Node-1 as a bootnode. 

Start another terminal, change to the `Node-2` directory and start Node-2 replacing the enode URL with your bootonde:
 
```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../ibftGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\ibftGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

The command line specifies: 

* Data directory for Node-2 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
* Different port to Node-1 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
* Different port to Node-1 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
* Enode URL for Node-1 using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option.
* Other options as for [Node-1](#5-start-first-node-as-bootnode).


### 10. Start Node-3

Start another terminal, change to the `Node-3` directory and start Node-3 replacing the enode URL with your bootnode: 

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../ibftGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\ibftGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist=* --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

The command line specifies: 

 * Data directory for Node-3 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
 * Different port to Node-1 and Node-2 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
 * Different port to Node-1 and Node-2 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
 * Bootnode as for [Node-2](#6-start-node-2).
 * Other options as for [Node-1](#5-start-first-node-as-bootnode). 

### 11. Confirm Private Network is Working 

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

Use the [IBFT API to add](../Consensus-Protocols/IBFT.md#adding-and-removing-validators) Node-2 or Node-3 as a validator. 

!!! note
    To add Node-2 or Node-3 as a validator you need the [node address as when specifying Node-1](#2-get-address-for-node-1) as the initial validator. 

Import accounts to MetaMask and send transactions as described in the [Private Network Quickstart Tutorial](Private-Network-Quickstart.md#creating-a-transaction-using-metamask)

!!! info 
    Pantheon does not implement [private key management](../Using-Pantheon/Account-Management.md).

## Stop Nodes

When finished using the private network, stop all nodes using ++ctrl+c++ in each terminal window. 

!!!tip
    To restart the IBFT 2.0 network in the future, start from [8. Start First Node as Bootnode](#8-start-first-node-as-bootnode). 
