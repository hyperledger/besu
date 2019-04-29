description: Onchain Permissioning
<!--- END of page meta data -->

# Onchain Permissioning 

Onchain permissioning uses smart contracts to store and maintain the node whitelist. Using onchain permissioning
enables all nodes to read the node whitelist from one location. 
                                                                       
!!! note
    Pantheon supports onchain permissioning for nodes. Onchain permissioning for accounts will be available 
    in a future Pantheon release. 

We have provided a set of smart contracts that interface with onchain permissioning in the 
[PegaSysEng/permissioning-smart-contracts](https://github.com/PegaSysEng/permissioning-smart-contracts) repository. 
We have also provided a management interface to interact with the contracts. 

## Provided Contracts

The following smart contracts are provided in the [PegaSysEng/permissioning-smart-contracts](https://github.com/PegaSysEng/permissioning-smart-contracts) repository: 

* Ingress - a simple contract functioning as a gateway to the Admin and Rules contracts. The Ingress contract is deployed 
to a static address. 

* Rules - stores the node whitelist and node whitelist operations (for example, add and remove). 

* Admin - stores the list of admin accounts and admin list operations (for example, add and remove).

## Pre-requisites 

For nodes that are going to maintain the nodes whitelist or the list of admin accounts: 

* [NodeJS](https://nodejs.org/en/) v8.9.4 or later 

* [Truffle](https://truffleframework.com/docs/truffle/getting-started/installation)

## Add Ingress Contract to Genesis File

Add the Ingress contract to the genesis file for your network by copying it from [`genesis.json`](https://github.com/PegaSysEng/permissioning-smart-contracts/blob/master/genesis.json) 
in the [`permissioning-smart-contracts` repository](https://github.com/PegaSysEng/permissioning-smart-contracts): 
   
```json
"0x0000000000000000000000000000000000009999": {
      "comment": "Ingress smart contract",
      "balance": "0",
      "code": <stripped>,
      "storage": {
         <stripped>
      }
}
```

!!! important 
    To support the permissioning contracts, ensure your genesis file includes at least the `constantinopleFixBlock` milestone. 
    
## Onchain Permissioning Setup
   
1. Start your Pantheon node including command line options: 

    * [--permissions-nodes-contract-enabled](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-enabled)
      to enable onchain permissioning

    * [--permissions-nodes-contract-address](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-address)
      set to the address of the Ingress contract in the genesis file (`"0x0000000000000000000000000000000000009999"`)
      
    * [--rpc-http-enabled](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) to enable JSON-RPC

1. Create the following environment variables and set to the specified values: 

    * `PANTHEON_NODE_PERM_ACCOUNT` - address of account used to interact with the permissioning contracts. 

    * `PANTHEON_NODE_PERM_KEY` - private key of the account used to interact with the permissioning contracts.

    * `INGRESS_CONTRACT_ADDRESS` - address of the Ingress contract in the genesis file.  

    * `PANTHEON_NODE_PERM_ENDPOINT` - required only if your node is not using the default JSON-RPC host and port (`http://127.0.0.1:8545`). 
       Set to JSON-RPC host and port. 

    !!! note
        If your network is not a [free gas network](../Configuring-Pantheon/FreeGas.md), the account used to 
        interact with the permissioning contracts must have a balance. 

1. Clone the `permissioning-smart-contracts` repository: 

    ```bash 
    git clone https://github.com/PegaSysEng/permissioning-smart-contracts.git
    ```

1. Change into the `permissioning-smart-contracts` directory and run: 

    ```bash
    npm install
    ```

## Deploy Admin and Rules Contracts 

!!! important 
    Only the first node deploys the admin and rules contract. Subsequent nodes do not migrate the contracts. 
    
    The node deploying the admin and rules contracts must be a miner (PoW networks) or validator (PoA networks). 

In the `permissioning-smart-contracts` directory, deploy the Admin and Rules contracts: 

```bash
truffle migrate --reset
```

The Admin and Rules contracts are deployed and the Ingress contract updated with the name and version of the contracts. 
The migration logs the addresses of the Admin and Rules contracts. 

!!! important 
    The account that deploys the contracts is automatically an [Admin account](#add-and-remove-admin-accounts). 

## Add and Remove Nodes from the Whitelist 

!!! note  
    Only [Admin accounts](#add-and-remove-admin-accounts) can add or remove nodes from the whitelist. 

    After deploying the admin and rules contracts, the first node must add itself to the whitelist before 
    adding other nodes.
    
To add or remove nodes: 

1. In the `permissioning-smart-contracts` directory, run the Truffle console: 

    ```bash
    truffle console 
    ```

1. Open https://permissioning-tools.pegasys.tech/

1. Enter the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) of the node to be added or removed. 

1. Click the *Add Node* or *Remove Node* button. The truffle command is displayed.

1. Click the *Copy to clipboard* button. 

1. Paste the copied command into the Truffle Console. 

   When the transaction is included in a block, the transaction receipt is displayed. 
      

!!! tip
    If you add a running node, the node does not attempt to reconnect to the bootnode and synchronize until 
    peer discovery restarts.  To add a whitelisted node as a peer without waiting for peer discovery to restart, use [`admin_addPeer`](../Reference/JSON-RPC-API-Methods.md#admin_addpeer). 

    If the node is added to the whitelist before starting the node, using `admin_addPeer` is not required because
    peer discovery is run on node startup. 

## Display Nodes Whitelist 
 
To display the nodes whitelist, paste the following into the Truffle Console: 

```javascript
Rules.deployed().then(function(instance) {instance.getSize().then(function(txCount) {console.log("size of whitelist: " + txCount); var i=txCount; while(i>=0) {instance.getByIndex(i--).then(function(tx) {console.log(tx)})}});});
```

## Start Other Network Nodes 

For participating nodes that are not going to add or remove nodes from the whitelist:

1. Start the nodes including the following options: 

    * [--permissions-nodes-contract-enabled](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-enabled)
     to enable onhcain permissioning
    
    * [--permissions-nodes-contract-address](../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-address)
     set to the address of the Ingress contract in the genesis file (`"0x0000000000000000000000000000000000009999"`)

    * [--bootnodes](../Reference/Pantheon-CLI-Syntax.md#bootnodes) set to the first node (that is, the node that [deployed
     the Admin and Rules contracts](#deploy-admin-and-rules-contracts).      

1. Copy the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) and [have node added to the whitelist](#add-and-remove-nodes-from-the-whitelist).

For participating nodes that are going to add or remove nodes from the whitelist: 

1. Ensure [prerequisites installed](#pre-requisites).

1. Complete [permissioning setup](#onchain-permissioning-setup).

1. Copy the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) and [have node added to the whitelist](#add-and-remove-nodes-from-the-whitelist).

1. Have account for node that will interact with permissioning contracts added as an [admin account](#add-and-remove-admin-accounts). 

## Add and Remove Admin Accounts      

The account that deploys the Rules contract is automatically an Admin account. Only Admin accounts can
add or remove nodes from the whitelist.

To add Admin accounts, paste the following commands into Truffle Console:

```javascript tab="Truffle Console Command"
Admin.deployed().then(function(instance) {instance.addAdmin("<Admin Account>").then(function(tx) {console.log(tx)});});
```

```javascript tab="Example"
Admin.deployed().then(function(instance) {instance.addAdmin("0x627306090abaB3A6e1400e9345bC60c78a8BEf57").then(function(tx) {console.log(tx)});});
```

To remove Admin accounts, paste the following commands into Truffle Console:

```javascript tab="Truffle Console Command"
Admin.deployed().then(function(instance) {instance.removeAdmin("<Admin Account>").then(function(tx) {console.log(tx)});});
```

```javascript tab="Example"
Admin.deployed().then(function(instance) {instance.removeAdmin("0x627306090abaB3A6e1400e9345bC60c78a8BEf57").then(function(tx) {console.log(tx)});});
```

### Display Admin Accounts 

To display the list of admin accounts, paste the following into the Truffle Console: 

```javascript
Admin.deployed().then(function(instance) {instance.getAdmins().then(function(tx) {console.log(tx)});});
```