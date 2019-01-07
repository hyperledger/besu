# Creating a Private Network

A private network provides a configurable network for testing. By configuring a low difficulty and enabling 
mining, blocks are created quickly. 

You can test multi-block and multi-user scenarios on a private network before moving to one of the public testnets. 

!!!important
    An Ethereum private network created as described here is isolated but not protected or secure. 
    We recommend running the private network behind a properly configured firewall.

## Prerequisites 

[Pantheon](../Installation/Install-Binaries.md) 

[Curl (or similar web service client)](https://curl.haxx.se/download.html) 

## Steps

To create a private network: 

1. [Create Folders](#1-create-folders)
1. [Create Genesis File](#2-create-genesis-file)
1. [Start First Node and Get Node Public Key](#3-start-first-node-and-get-node-public-key)
1. [Restart First Node as Bootnode](#4-restart-first-node-as-bootnode)
1. [Start Additional Nodes](#5-start-additional-nodes)
1. [Confirm Private Network Working](#6-confirm-private-network-working)

### 1. Create Folders 

Each node requires a data directory for the blockchain data. When the node is started, the node key is saved in this directory. 

Create directories for your private network, each of the three nodes, and a data directory for each node: 

```bash
Private-Network/
├── Node-1
│   ├── Node-1-Datadir
├── Node-2
│   ├── Node-2-Datadir
└── Node-3
    ├── Node-3-Datadir
```

### 2. Create Genesis File 

The genesis file defines the genesis block of the blockchain (that is, the start of the blockchain).
The genesis file includes entries for configuring the blockchain such as the mining difficulty and initial 
accounts and balances.    

All nodes in a network must use the same genesis file. 

Copy the following genesis definition to a file called `privateNetworkGenesis.json` and save it in the `Private-Network` directory: 

```json
{
  "config": {
    "ethash": {
    }
  },
  "nonce": "0x42",
  "gasLimit": "0x1000000",
  "difficulty": "0x10000",
  "alloc": {
    "fe3b557e8fb62b89f4916b721be55ceb828dbd73": {
      "privateKey": "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
      "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
      "balance": "0xad78ebc5ac6200000"
    },
    "f17f52151EbEF6C7334FAD080c5704D77216b732": {
      "privateKey": "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
      "comment": "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
      "balance": "90000000000000000000000"
    }
  }
}
```

!!! warning
    Do not use the accounts in the genesis file above on mainnet or any public network except for testing.
    The private keys are displayed so the accounts are not secure. 
   
### 3. Start First Node and Get Node Public Key 

To enable nodes to discover each other, a network requires one or more nodes to be bootnodes. 
For this private network, we will use Node-1 as the bootnode. This requires starting and stopping the 
node and obtaining the node public key for the enode URL. 

In the `Node-1` directory, start the node specifying the data directory created in step 1, 
the genesis file created in step 2, and a network ID for your private network (`123` in this example):

```bash tab="MacOS"
pantheon --genesis=../privateNetworkGenesis.json --network-id 123 --datadir=Node-1-DataDir
``` 

```bash tab="Windows"
pantheon --genesis=..\privateNetworkGenesis.json --network-id 123 --datadir=Node-1-DataDir
``` 

A node private key for Node-1 is generated and written to the `key` file. 

Wait until the node public key is displayed. For example:  

`2019-01-01 05:03:27.430+10:00 | main | INFO  | KeyPairUtil | Generated new key 0x5ca7eca7a9b6b8128d8b9375fe2683c43023fd1504bca31f5123bd9b086155d81d0b05a08b63c7fa1027bbd9cfa338b6c54539c72b05a56c1f52f802c87ad09c and stored it to /Users/username/Private-Network/Node-3/Node-3-DataDir/key
20`

Stop the node using ++ctrl+c++. 

Use the [`export-pub-key` subcommand](../Reference/Pantheon-CLI-Syntax.md#export-pub-key) to write 
the [node public key](../Configuring-Pantheon/Node-Keys.md) to the specified file (`publicKeyNode1` in this example): 

```bash tab="MacOS"
pantheon --datadir=Node-1-Datadir --genesis=../privateNetworkGenesis.json export-pub-key Node-1-Datadir/publicKeyNode1
```

```bash tab="Windows"
pantheon --datadir=Node-1-Datadir --genesis=..\privateNetworkGenesis.json export-pub-key Node-1-Datadir\publicKeyNode1
```

Your node 1 directory now contains: 
```bash
├── Node-1
    ├── Node-1-Datadir
        ├── database
        ├── key
        ├── publicKeyNode1
```
 
The `database` directory is created when the node is started and contains the blockchain data. 
      
### 4. Restart First Node as Bootnode 

Restart Node-1 specifying:

* An empty string for the [`--bootnodes` option](../Reference/Pantheon-CLI-Syntax.md#bootnodes) because this is your bootnode.
* Mining is enabled and the account to which mining rewards are paid using the [`--miner-enabled`](../Reference/Pantheon-CLI-Syntax.md#miner-enabled) 
and [`--miner-coinbase` options](../Reference/Pantheon-CLI-Syntax.md#miner-coinbase).
* JSON-RPC API is enabled using the [`--rpc-enabled` option](../Reference/Pantheon-CLI-Syntax.md#rpc-enabled). 

```bash tab="MacOS"
pantheon --datadir=Node-1-Datadir --genesis=../privateNetworkGenesis.json --bootnodes="" --network-id 123 --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-enabled      
```

```bash tab="Windows"
pantheon --datadir=Node-1-Datadir --genesis=..\privateNetworkGenesis.json --bootnodes="" --network-id 123 --miner-enabled --miner-coinbase fe3b557e8fb62b89f4916b721be55ceb828dbd73 --rpc-enabled      
```

!!! info
    The miner coinbase account is one of the accounts defined in the genesis file. 

### 5. Start Additional Nodes 

You need the enode URL for Node-1 to specify Node-1 as the bootnode for Node-2 and Node-3. 

The enode URL is `enode://<id>@<host:port>` where:

* `<id>` is the node public key excluding the initial 0x. The node public node for Node-1 was written to `publicKeyNode1` in [step 3](#3-start-first-node-and-get-node-public-key). 
* `<host:port>` is the host and port the bootnode is listening on for P2P peer discovery. Node-1 is using the default host and port of `127.0.0.1:30303`. 

!!! example
    If the default host and port are used for P2P peer discovery and the node public key exported is: `0xc35c3ec90a8a51fd5703594c6303382f3ae6b2ecb9589bab2c04b3794f2bc3fc2631dabb0c08af795787a6c004d8f532230ae6e9925cbbefb0b28b79295d615f`
    
    The enode URL is:
    `enode://c35c3ec90a8a51fd5703594c6303382f3ae6b2ecb9589bab2c04b3794f2bc3fc2631dabb0c08af795787a6c004d8f532230ae6e9925cbbefb0b28b79295d615f@127.0.0.1:30303` 

Start another terminal, change to the `Node-2` directory and start Node-2 specifying:
 
* Different port to Node-1 for P2P peer discovery using the [`--p2p-listen` option](../Reference/Pantheon-CLI-Syntax.md#p2p-listen).
* Enode URL for Node-1 using the [`--bootnodes` option](../Reference/Pantheon-CLI-Syntax.md#bootnodes).
* Data directory for Node-2 using the [`--datadir` option](../Reference/Pantheon-CLI-Syntax.md#datadir).
* Genesis file and network ID as for Node-1.  

```bash tab="MacOS"
pantheon --datadir=Node-2-Datadir --genesis=../privateNetworkGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-listen=127.0.0.1:30304      
```

```bash tab="Windows"
pantheon --datadir=Node-2-Datadir --genesis=..\privateNetworkGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-listen=127.0.0.1:30304      
```

Start another terminal, change to the `Node-3` directory and start Node-3 specifying: 
 
 * Different port to Node-1 and Node-2 for P2P peer discovery.
 * Data directory for Node-3 using the [`--datadir` option](../Reference/Pantheon-CLI-Syntax.md#datadir).
 * Bootnode, genesis file, and network ID as for Node-2. 

```bash tab="MacOS"
pantheon --datadir=Node-3-Datadir --genesis=../privateNetworkGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-listen=127.0.0.1:30305      
```

```bash tab="Windows"
pantheon --datadir=Node-3-Datadir --genesis=..\privateNetworkGenesis.json --bootnodes="enode://<node public key ex 0x>@127.0.0.1:30303" --network-id 123 --p2p-listen=127.0.0.1:30305      
```

### 6. Confirm Private Network is Working 

Start another terminal, use curl to call the JSON-RPC API [`net_peerCount`](../Reference/JSON-RPC-API-Methods.md#net_peercount) method and confirm the nodes are functioning as peers: 

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' 127.0.0.1:8545
```

The result confirms Node-1 (the node running the JSON-RPC service) has two peers (Node-2 and Node-3):
```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x2"
}
```

## Next Steps 

Import accounts to MetaMask and send transactions as described in the [Private Network Quickstart Tutorial](../Getting-Started/Private-Network-Quickstart.md#creating-a-transaction-using-metamask)

!!! info 
    Pantheon does not implement [private key management](../Using-Pantheon/Account-Management.md).
    
Send transactions using `eth_sendRawTransaction` to [send ether or, deploy or invoke contracts](../Using-Pantheon/Transactions.md).

Use the [JSON-RPC API](../Reference/Using-JSON-RPC-API.md). 

Start a node with the `--ws-enabled` option and use the [RPC Pub/Sub API](../Using-Pantheon/RPC-PubSub.md).       

## Stop Nodes

When finished using the private network, stop all nodes using ++ctrl+c++ in each terminal window. 

!!!tip
    To restart the private network in the future, start from [4. Restart First Node as Bootnode](#4-restart-first-node-as-bootnode). 