description: Pantheon IBFT 2.0 Proof-of-Authority (PoA) private network tutorial 
<!--- END of page meta data -->

*[Byzantine fault tolerant]: Ability to function correctly and reach consensus despite nodes failing or propagating incorrect information to peers.

# Creating a Private Network using IBFT 2.0 (Proof of Authority) Consensus Protocol

A private network provides a configurable network for testing. This private network uses the [IBFT 2.0 (Proof of Authority)
consensus protocol](../Consensus-Protocols/IBFT.md). 

!!!important
    An Ethereum private network created as described here is isolated but not protected or secure. 
    We recommend running the private network behind a properly configured firewall.
    
    This tutorial configures a private network using IBFT 2.0 for educational purposes only. 
    IBFT 2.0 requires 4 validators to be Byzantine fault tolerant.

## Prerequisites 

[Pantheon](../Installation/Install-Binaries.md) 

[Curl (or similar web service client)](https://curl.haxx.se/download.html) 

## Steps

The steps to create a private network using IBFT 2.0 with four nodes are on the right. The four nodes are 
all validators.   

### 1. Create Folders 

Each node requires a data directory for the blockchain data. 

Create directories for your private network, each of the four nodes, and a data directory for each node: 

```bash
IBFT-Network/
├── Node-1
│   ├── data
├── Node-2
│   ├── data
├── Node-3
│   ├── data
└── Node-4
    ├── data
```

### 2. Create Configuration File 

The configuration file defines the [IBFT 2.0 genesis file](../Consensus-Protocols/IBFT.md#genesis-file) 
and the number of node key pairs to generate.    

The configuration file has 2 subnested JSON nodes. The first is the `genesis` property defining 
the IBFT 2.0 genesis file except for the `extraData` string. The second is the `blockchain` property 
defining the number of key pairs to generate. 
    
Copy the following configuration file definition to a file called `ibftConfigFile.json` and save it in the `IBFT-Network` directory: 

```json
{
 "genesis": {
   "config": {
      "chainId": 2018,
      "constantinoplefixblock": 0,
      "ibft2": {
        "blockperiodseconds": 2,
        "epochlength": 30000,
        "requesttimeoutseconds": 10
      }
    },
    "nonce": "0x0",
    "timestamp": "0x58ee40ba",
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
 },
 "blockchain": {
   "nodes": {
     "generate": true,
       "count": 4
   }
 }
}
```

!!! warning 
    Do not use the accounts in `alloc` in the genesis file on mainnet or any public network except for testing.
        
    The private keys are displayed which means the accounts are not secure.  

### 3. Generate Node Keys and Genesis File 

In the `IBFT-Network` directory, generate the node key and genesis file: 

```bash tab="MacOS"
pantheon operator generate-blockchain-config --config-file=ibftConfigFile.json --to=networkFiles --private-key-file-name=key
```

In the `networkFiles` directory, the following are created: 

* `genesis.json` - genesis file including the `extraData` property specifying the four nodes are validators
* Directory for each node named with the node address and containing the public and private key for each node 

```bash
networkFiles/
├── genesis.json
└── keys
    ├── 0x438821c42b812fecdcea7fe8235806a412712fc0
    │   ├── key
    │   └── key.pub
    ├── 0xca9c2dfa62f4589827c0dd7dcf48259aa29f22f5
    │   ├── key
    │   └── key.pub
    ├── 0xcd5629bd37155608a0c9b28c4fd19310d53b3184
    │   ├── key
    │   └── key.pub
    └── 0xe96825c5ab8d145b9eeca1aba7ea3695e034911a
        ├── key
        └── key.pub
```
    
### 4. Copy the Genesis File to the IBFT-Network Directory 

Copy the `genesis.json` file to the `IBFT-Network` directory. 
    
### 5. Copy Node Private Keys to Node Directories 

For each node, copy the key files to the `data` directory for that node 


```bash
IBFT-Network/
├── genesis.json
├── Node-1
│   ├── data
│   │    ├── key
│   │    ├── key.pub
├── Node-2
│   ├── data
│   │    ├── key
│   │    ├── key.pub
├── Node-3
│   ├── data
│   │    ├── key
│   │    ├── key.pub
├── Node-4
│   ├── data
│   │    ├── key
│   │    ├── key.pub
```

### 6. Start First Node as Bootnode

In the `Node-1` directory, start Node-1:

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../genesis.json --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all"      
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\genesis.json --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all"    
```

The command line specifies: 

* Data directory for Node-1 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
* JSON-RPC API is enabled using the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option
* ETH,NET, and IBFT APIs are enabled using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option
* All hosts can access the HTTP JSON-RPC API using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option
* All domains can access the node using the HTTP JSON-RPC API using the [`--rpc-http-cors-origins`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-cors-origins) option 

When the node starts, the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) is displayed.
Copy the enode URL to specify Node-1 as the bootnode in the following steps. 

![Node 1 Enode URL](../images/EnodeStartup.png)

### 7. Start Node-2 

Start another terminal, change to the `Node-2` directory and start Node-2 specifying the Node-1 enode URL copied when starting Node-1 as the bootnode:
 
```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30304 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8546     
```

The command line specifies: 

* Data directory for Node-2 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
* Different port to Node-1 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
* Different port to Node-1 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
* Enode URL for Node-1 using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option.
* Other options as for [Node-1](#5-start-first-node-as-bootnode).


### 8. Start Node-3

Start another terminal, change to the `Node-3` directory and start Node-3 specifying the Node-1 enode URL copied when starting Node-1 as the bootnode: 

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30305 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8547    
```

The command line specifies: 

 * Data directory for Node-3 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
 * Different port to Node-1 and Node-2 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
 * Different port to Node-1 and Node-2 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
 * Bootnode as for [Node-2](#6-start-node-2).
 * Other options as for [Node-1](#5-start-first-node-as-bootnode). 
 
### 9. Start Node-4

Start another terminal, change to the `Node-4` directory and start Node-4 specifying the Node-1 enode URL copied when starting Node-1 as the bootnode: 

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30306 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8548    
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\genesis.json --bootnodes=<Node-1 Enode URL> --p2p-port=30306 --rpc-http-enabled --rpc-http-api=ETH,NET,IBFT --host-whitelist="*" --rpc-http-cors-origins="all" --rpc-http-port=8548    
```

The command line specifies: 

 * Data directory for Node-4 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option.
 * Different port to Node-1, Node-2, and Node-3 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option.
 * Different port to Node-1, Node-2, and Node-3 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option.
 * Bootnode as for [Node-2](#6-start-node-2).
 * Other options as for [Node-1](#5-start-first-node-as-bootnode). 

### 10. Confirm Private Network is Working 

Start another terminal, use curl to call the JSON-RPC API [`net_peerCount`](../Reference/Pantheon-API-Methods.md#net_peercount) method and confirm the nodes are functioning as peers: 

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' localhost:8545
```

The result confirms Node-1 has three peers (Node-2, Node-3, and Node-4):
```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x3"
}
```

## Next Steps 

Look at the logs displayed to confirm blocks are being produced. 

Use the [IBFT API](../Reference/Pantheon-API-Methods.md#ibft-20-methods) to remove or add validators.

!!! note
    To add or remove nodes as validators you need the node address. The directory [created for each node](#3-generate-node-keys-and-genesis-file) 
    is named with the node address.

    This tutorial configures a private network using IBFT 2.0 for educational purposes only. IBFT 2.0 requires 4 validators to be Byzantine fault tolerant.

Import accounts to MetaMask and send transactions as described in the [Private Network Quickstart Tutorial](Private-Network-Quickstart.md#creating-a-transaction-using-metamask)

!!! info 
    Pantheon does not implement [private key management](../Using-Pantheon/Account-Management.md).

## Stop Nodes

When finished using the private network, stop all nodes using ++ctrl+c++ in each terminal window. 

!!!tip
    To restart the IBFT 2.0 network in the future, start from [6. Start First Node as Bootnode](#6-start-first-node-as-bootnode). 
