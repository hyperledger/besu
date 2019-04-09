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

The steps to create a permissoned network are displayed on the right.

### 1. Create Folders 

Each node requires a data directory for the blockchain data. When the node is started, the [node key](../Configuring-Pantheon/Node-Keys.md)
is saved in this directory. 

Create directories for your permissioned network, each of the three nodes, and a data directory for each node: 

```bash
Permissioned-Network/
├── Node-1
│   ├── data
├── Node-2
│   ├── data
└── Node-3
    ├── data
```

### 2. Get Address of Node-1 

In networks using Clique, the address of at least one initial signer must be included in the genesis file. 
For this network, we will use Node-1 as the initial signer. This requires obtaining the address for Node-1. 

To obtain the address for Node-1, in the `Node-1` directory, use the [`public-key export-address`](../Reference/Pantheon-CLI-Syntax.md#public-key)
subcommand to write the node address to the specified file (`nodeAddress1` in this example)

```bash tab="MacOS"
pantheon --data-path=data public-key export-address --to=data/nodeAddress1
```

```bash tab="Windows"
pantheon --data-path=data public-key export-address --to=data\nodeAddress1
```

### 3. Create Genesis File 

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

### 4. Create Permissions Configuration File 

The permissions configuration file defines the nodes and accounts whitelists. 

Copy the following permissions configuration to a file called `permissions_config.toml` and save a copy in the `Node-1/data`, 
`Node-2/data`, and `Node-3/data` directories:

!!! example "permissions_config.toml"
    ```toml 
    accounts-whitelist=["0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "0x627306090abaB3A6e1400e9345bC60c78a8BEf57"]
   
    nodes-whitelist=[]
    ```
The permissions configuration file includes the first two accounts from the genesis file.

Permissioned nodes are added using the JSON-RPC API after starting the nodes. 

!!! note
    Permissions are specified at the node level. The [`permissions_config.toml`](../Permissions/Local-Permissioning.md#permissions-configuration-file) 
    file must be saved in the data directory for each node. 
    
    On-chain permissioning is under development. On-chain permissioning will use one on-chain 
    nodes whitelist and accounts whitelist. 

### 5. Start Node-1 

Start Node-1:

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=*      
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=*    
```

!!!note
    The [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) option is not used when running 
    Pantheon from the [Docker image](../Getting-Started/Run-Docker-Image.md). Use a bind mount to 
    [specify a configuration file with Docker](../Getting-Started/Run-Docker-Image.md#custom-genesis-file).

The command line specifies: 

* Nodes and accounts permissions are enabled using the [`--permissions-nodes-config-file-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-config-file-enabled)
and [`--permissions-accounts-config-file-enabled`](../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-config-file-enabled) options
* JSON-RPC API is enabled using the [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) option
* ADMIN,ETH,NET,PERM, and CLIQUE APIs are enabled using the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) option
* All hosts can access the HTTP JSON-RPC API using the [`--host-whitelist`](../Reference/Pantheon-CLI-Syntax.md#host-whitelist) option 
* All domains can access the node using the HTTP JSON-RPC API using the [`--rpc-http-cors-origins`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-cors-origins) option. 

When the node starts, the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) is displayed.
The enode URL is required specify Node-1 as a peer and update the permissions configuration file in 
the following steps. 

![Node 1 Enode URL](../images/EnodeStartup.png)

### 6. Start Node-2 

Start another terminal, change to the `Node-2` directory and start Node-2:

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30304 --rpc-http-port=8546    
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30304 --rpc-http-port=8546   
```

The command line specifies:
 
* Different port to Node-1 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option
* Different port to Node-1 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option
* Data directory for Node-2 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option
* Other options as for as for Node-1.  

When the node starts, the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) is displayed.
The enode URL is required to update the permissions configuration file in the following steps. 

### 7. Start Node-3

Start another terminal, change to the `Node-3` directory and start Node-3:

```bash tab="MacOS"
pantheon --data-path=data --genesis-file=../cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30305 --rpc-http-port=8547    
```

```bash tab="Windows"
pantheon --data-path=data --genesis-file=..\cliqueGenesis.json --permissions-nodes-config-file-enabled --permissions-accounts-config-file-enabled --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30305 --rpc-http-port=8547   
```

The command line specifies:
 
* Different port to Node-1 and Node-2 for P2P peer discovery using the [`--p2p-port`](../Reference/Pantheon-CLI-Syntax.md#p2p-port) option
* Different port to Node-1 and Node-2 for HTTP JSON-RPC using the [`--rpc-http-port`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) option
* Data directory for Node-3 using the [`--data-path`](../Reference/Pantheon-CLI-Syntax.md#data-path) option
* Other options as for as for Node-1.  

When the node starts, the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) is displayed.
The enode URL is required to update the permissions configuration file in the following steps. 

### 8. Add Enode URLs for Nodes to Permissions Configuration File 

In another terminal, use the [`perm_addNodesToWhitelist`](../Reference/JSON-RPC-API-Methods.md#perm_addnodestowhitelist) 
JSON-RPC API method to add the nodes to the permissions configuration file for each node. 

Replace `<EnodeNode1>`, `<EnodeNode2>`, and `<EnodeNode3>` with the enode URL displayed when starting each node. 

Node-1:
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"perm_addNodesToWhitelist","params":[["<EnodeNode1>","<EnodeNode2>","<EnodeNode3>"]], "id":1}' http://127.0.0.1:8545
```

Node-2:
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"perm_addNodesToWhitelist","params":[["<EnodeNode1>","<EnodeNode2>","<EnodeNode3>"]], "id":1}' http://127.0.0.1:8546
```

Node 3:
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"perm_addNodesToWhitelist","params":[["<EnodeNode1>","<EnodeNode2>","<EnodeNode3>"]], "id":1}' http://127.0.0.1:8547
```

!!! tip
    The curl call is the same for each node except for the JSON-RPC endpoint. 

### 9. Add Nodes as Peers

Use the [`admin_addPeer`](../Reference/JSON-RPC-API-Methods.md#admin_addpeer) JSON-RPC API method to add 
Node-1 as a peer for Node-2 and Node-3. 

Replace `<EnodeNode1>` with the enode URL displayed when starting Node-1. 

Node 2: 
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"admin_addPeer","params":["<EnodeNode1>"],"id":1}' http://127.0.0.1:8546
```

Node 3:
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"admin_addPeer","params":["<EnodeNode1>"],"id":1}' http://127.0.0.1:8547
```

!!! tip
    The curl call is the same for both nodes except for the JSON-RPC endpoint. 

Replace `<EnodeNode2>` with the enode URL displayed when starting Node-2. 

Node 3:
```bash
curl -X POST --data '{"jsonrpc":"2.0","method":"admin_addPeer","params":["<EnodeNode2>"],"id":1}' http://127.0.0.1:8547
```

### 10. Confirm Permissioned Network is Working 

#### Check Peer Count 

Use curl to call the JSON-RPC API [`net_peerCount`](../Reference/JSON-RPC-API-Methods.md#net_peercount) method and confirm the nodes are functioning as peers: 

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

!!! example "Account 1"
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

In your `Permissioned-Network` directory, create a `Node-4` directory and `data` directory inside it. 

Change to the `Node-4` directory and start Node-4 specifying the Node-1 enode URL as the bootnode:

```bash tab="MacOS"
pantheon --data-path=data --bootnodes="<EnodeNode1>" --genesis-file=../cliqueGenesis.json --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30306 --rpc-http-port=8548    
```

```bash tab="Windows"
pantheon --data-path=data --bootnodes="<EnodeNode1>" --genesis-file=..\cliqueGenesis.json --rpc-http-enabled --rpc-http-api=ADMIN,ETH,NET,PERM,CLIQUE --host-whitelist=* --rpc-http-cors-origins=* --p2p-port=30306 --rpc-http-port=8548    
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
    To restart the permissioned network in the future, start from [5. Start Node-1](#5-start-node-1). 
