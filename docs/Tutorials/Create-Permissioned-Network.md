description: Pantheon Create a Permissioned network 
<!--- END of page meta data -->

# Creating a Permissioned Network

The following steps set up a permissioned network with node and account permissions. The network uses the 
[Clique Proof of Authority consensus protocol](../Consensus-Protocols/Clique.md). 

!!!important 
    A permissioned Ethereum network as described here is not protected against all attack vectors.
    We recommend applying defense in depth to protect your infrastructure. 

## Prerequisites 

[Pantheon](../Installation/Install-Binaries.md) 

[Curl (or similar web service client)](https://curl.haxx.se/download.html) 

## Steps

To create a permissoned network: 

1. [Create Folders](#1-create-folders)
1. [Get Node Public Keys](#2-get-node-public-keys)
1. [Get Address of Node-1](#3-get-address-of-node-1)
1. [Create Genesis File](#4-create-genesis-file)
1. [Create Permissions Configuration File](#5-create-permissions-configuration-file)
1. [Delete Database Directories](#6-delete-database-directories)
1. [Start First Node as Bootnode](#7-start-first-node-as-bootnode)
1. [Start Node-2](#8-start-node-2)
1. [Start Node-3](#9-start-node-3)
1. [Confirm Permissioned Network is Working](#10-confirm-permissioned-network-is-working)

### 1. Create Folders 

Each node requires a data directory for the blockchain data. When the node is started, the [node key](../Configuring-Pantheon/Node-Keys.md)
is saved in this directory. 

Create directories for your permissioned network, each of the three nodes, and a data directory for each node: 

```bash
Permissioned-Network/
├── Node-1
│   ├── Node-1-data-path
├── Node-2
│   ├── Node-2-data-path
└── Node-3
    ├── Node-3-data-path
```

### 2. Get Node Public Keys

The [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) of each node is needed for the nodes whitelist.

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

Repeat this for Node-2 and Node-3 in the `Node-2` and `Node-3` directories: 

```bash tab="MacOS"
pantheon --data-path=Node-2-data-path public-key export --to=Node-2-data-path/publicKeyNode2

pantheon --data-path=Node-3-data-path public-key export --to=Node-3-data-path/publicKeyNode3
```

```bash tab="Windows"
pantheon --data-path=Node-2-data-path public-key export --to=Node-2-data-path\publicKeyNode2

pantheon --data-path=Node-3-data-path public-key export --to=Node-3-data-path\publicKeyNode3
``` 

### 3. Get Address of Node-1 

In networks using Clique, the address of at least one initial signer must be included in the genesis file. 
For this network, we will use Node-1 as the initial signer. This requires obtaining the address for Node-1. 

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

Copy the following genesis definition to a file called `cliqueGenesis.json` and save it in the `Permissioned-Network` directory: 

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
    Do not use the accounts in the genesis file on MainNet or any public network except for testing.
        
    The private keys are displayed which means the accounts are not secure.  

### 5. Create Permissions Configuration File 

The permissions configuration file defines the nodes and accounts whitelists. 

Copy the following permissions configuration to a file called `permissions_config.toml` and save a copy in the `Node-1-data-path`, 
`Node-2-data-path`, and `Node-3-data-path` directories:

!!! example "permissions_config.toml"
    ```toml 
    accounts-whitelist=["0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "0x627306090abaB3A6e1400e9345bC60c78a8BEf57"]
     
    nodes-whitelist=["enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303","enode://<publicKeyNode2 ex 0x>@127.0.0.1:30304","enode://<publicKeyNode3 ex 0x>@127.0.0.1:30305"]
    ```

Replace the public key as indicated for each enode URL with the [node public keys](#2-get-node-public-keys). 

The permissions configuration file includes: 

* First two accounts from the genesis file.
* Enode URLs for each node. 

!!! note
    Permissions are specified at the node level. The [`permissions_config.toml`](../Permissions/Permissioning.md#permissions-configuration-file) 
    file must be saved in the data directory for each node. 
    
    On-chain permissioning is under development. On-chain permissioning will use one on-chain 
    nodes whitelist and accounts whitelist. 

### 6. Delete Database Directories

Delete the `database` directories created when [getting the public keys for each node](#2-get-public-keys).
The nodes cannot be started while the previously generated data is in the `database` directory. 

### 7. Start First Node as Bootnode 

Start Node-1:

```bash tab="MacOS"
pantheon --data-path=Node-1-data-path --genesis-file=../cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=*      
```

```bash tab="Windows"
pantheon --data-path=Node-1-data-path --genesis-file=..\cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=*    
```

The command line specifies: 

* Nodes and accounts permissions are enabled using the [`--permissions-nodes-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-enabled)
and [`--permissions-accounts-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-enabled)
* JSON-RPC API is enabled using the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option
* ADMIN,ETH,NET,PERM, and CLIQUE APIs are enabled using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option
* All hosts can access the HTTP JSON-RPC API using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option 
* All domains can access the node using the HTTP JSON-RPC API using the [`--rpc-http-cors-origins`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-cors-origins) option. 


### 8. Start Node-2 

You need the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) for Node-1 to specify Node-1 as a bootnode. 

Start another terminal, change to the `Node-2` directory and start Node-2 replacing the public key in the enode URL with your bootonde:

```bash tab="MacOS"
pantheon --data-path=Node-2-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=../cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30304 --rpc-http-port=8546    
```

```bash tab="Windows"
pantheon --data-path=Node-2-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=..\cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30304 --rpc-http-port=8546   
```

The command line specifies:
 
* Different port to Node-1 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option
* Different port to Node-1 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option
* Enode URL for Node-1 using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option
* Data directory for Node-2 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option
* Other options as for as for Node-1.  


### 9. Start Node-3

Start another terminal, change to the `Node-3` directory and start Node-3 replacing the public key in the enode URL with your bootonde:

```bash tab="MacOS"
pantheon --data-path=Node-3-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=../cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30305 --rpc-http-port=8547    
```

```bash tab="Windows"
pantheon --data-path=Node-3-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=..\cliqueGenesis.json --permissions-nodes-enabled --permissions-accounts-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30305 --rpc-http-port=8547   
```

The command line specifies:
 
* Different port to Node-1 and Node-2 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option
* Different port to Node-1 and Node-2 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option
* Enode URL for Node-1 using the [`--bootnodes`](../Reference/Pantheon-CLI-Syntax.md#bootnodes) option
* Data directory for Node-3 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option
* Other options as for as for Node-1.  

### 10. Confirm Permissioned Network is Working 

#### Check Peer Count 

Start another terminal, use curl to call the JSON-RPC API [`net_peerCount`](../Reference/JSON-RPC-API-Methods.md#net_peercount) method and confirm the nodes are functioning as peers: 

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' localhost:8545
```

The result confirms Node-1 (the node running the JSON-RPC service) has two peers (Node-2 and Node-3):
```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x2"
}
```

#### Send a Transaction from an Account in the Whitelist 

Import the first account from the genesis file into MetaMask and send transactions as described in the [Private Network Quickstart Tutorial](Private-Network-Quickstart.md#creating-a-transaction-using-metamask):

!!! example "Account 1 (Miner Coinbase Account)"
    * Address: `0xfe3b557e8fb62b89f4916b721be55ceb828dbd73`
    * Private key : `0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63`
    * Initial balance : `0xad78ebc5ac6200000` (200000000000000000000 in decimal)   

!!! info 
    Pantheon does not implement [private key management](../Using-Pantheon/Account-Management.md).

### Try Sending a Transaction from an Account Not in the Accounts Whitelist 

Import the last account from the genesis file into MetaMask and try to send a transactions as described in the [Private Network Quickstart Tutorial](Private-Network-Quickstart.md#creating-a-transaction-using-metamask):

!!! example "Account 3"
    * Address: `0xf17f52151EbEF6C7334FAD080c5704D77216b732`
    * Private key : `0xae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f`
    * Initial balance : `0x90000000000000000000000` (2785365088392105618523029504 in decimal)

### Start a Node Not on the Nodes Whitelist   

In your `Permissioned-Network` directory, create a `Node-4` directory and `Node-4-data-path` directory inside it. 

Change to the `Node-4` directory and start Node-4 replacing the public key in the enode URL with your bootnode as when
starting Node-1 and Node-2:

```bash tab="MacOS"
pantheon --data-path=Node-4-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=../cliqueGenesis.json --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30306 --rpc-http-port=8548    
```

```bash tab="Windows"
pantheon --data-path=Node-4-data-path --bootnodes="enode://<publicKeyNode1 ex 0x>@127.0.0.1:30303" --genesis-file=..\cliqueGenesis.json --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30306 --rpc-http-port=8548    
```

Start another terminal, use curl to call the JSON-RPC API [`net_peerCount`](../Reference/JSON-RPC-API-Methods.md#net_peercount) method: 

```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":1}' localhost:8548
```

The result confirms Node-4 has no peers even though it specifies Node-1 as a bootnode:
```json
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : "0x0"
}
```

## Stop Nodes

When finished using the permissioned network, stop all nodes using ++ctrl+c++ in each terminal window. 

!!!tip
    To restart the permissioned network in the future, start from [7. Start First Node as Bootnode](#7-restart-first-node-as-bootnode). 
