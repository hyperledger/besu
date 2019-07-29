description: Setting up and using onchain Permissioning
<!--- END of page meta data -->

# Getting Started with Onchain Permissioning 

The following steps describe bootstrapping a local permissioned network using a Pantheon node and a 
development server to run the Permissioning Management Dapp. 

!!! note 
    In production, a webserver is required to [host the Permissioning Management Dapp](Production.md). 

To start a network with onchain permissioning: 

1. [Install pre-requisites](#pre-requisites) 
1. [Add the ingress contracts to the genesis file](#add-ingress-contracts-to-genesis-file) 
1. [Set environment variables](#set-environment-variables)
1. [Start first node with onchain permissioning and the JSON-RPC HTTP service enabled](#onchain-permissioning-command-line-options) 
1. [Clone the permissioning contracts repository and install dependencies](#clone-contracts-and-install-dependencies) 
1. [Build project](#build-project)
1. [Deploy the permissioning contracts](#deploy-contracts) 
1. [Start the development server for the Permissioning Management Dapp](#start-the-permissioning-management-dapp) 
1. [Add the first node to the nodes whitelist](#update-nodes-whitelist)

## Pre-requisites 

For the development server to run the dapp: 

* [NodeJS](https://nodejs.org/en/) v10.16.0 or later 
* [Yarn](https://yarnpkg.com/en/) v1.15 or later
* Browser with [MetaMask installed](https://metamask.io/)

To deploy the permissioning contracts: 

* [Truffle](https://truffleframework.com/docs/truffle/getting-started/installation)

## Add Ingress Contracts to Genesis File

!!! tip 
    If the network is using only account or nodes permissioning, add only the relevant ingress contract to the
    genesis file. 

Add the Ingress contracts to the genesis file for your network by copying them from [`genesis.json`](https://github.com/PegaSysEng/permissioning-smart-contracts/blob/master/genesis.json) 
in the [`permissioning-smart-contracts` repository](https://github.com/PegaSysEng/permissioning-smart-contracts): 
   
```json

"0x0000000000000000000000000000000000008888": {
      "comment": "Account Ingress smart contract",
      "balance": "0",
      "code": <stripped>,
      "storage": {
         <stripped>
      }
}

"0x0000000000000000000000000000000000009999": {
      "comment": "Node Ingress smart contract",
      "balance": "0",
      "code": <stripped>,
      "storage": {
         <stripped>
      }
}
```

!!! important 
    To support the permissioning contracts, ensure your genesis file includes at least the `constantinopleFixBlock` milestone. 

## Set Environment Variables 

Create the following environment variables and set to the specified values: 

* `PANTHEON_NODE_PERM_ACCOUNT` - account to deploy the permissioning contracts and become the first admin account. 

* `PANTHEON_NODE_PERM_KEY` - private key of the account to deploy the permissioning contracts.

* `ACCOUNT_INGRESS_CONTRACT_ADDRESS` - address of the Account Ingress contract in the genesis file.  

* `NODE_INGRESS_CONTRACT_ADDRESS` - address of the Node Ingress contract in the genesis file.  

* `PANTHEON_NODE_PERM_ENDPOINT` - required only if your node is not using the default JSON-RPC host and port (`http://127.0.0.1:8545`). 
Set to JSON-RPC host and port. When bootstrapping the network, the specified node is used to deploy the contracts and is the first node
in the network. 

!!! important 
    The account specified must be a miner (PoW networks) or validator (PoA networks).
    
    If your network is not a [free gas network](../../Configuring-Pantheon/FreeGas.md), the account used to 
    interact with the permissioning contracts must have a balance. 

## Onchain Permissioning Command Line Options
   
All nodes participating in a permissioned network must include the command line options to enable account and/or
node permissioning: 

* [--permissions-accounts-contract-enabled](../../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-contract-enabled)
to enable onchain accounts permissioning
          
* [--permissions-accounts-contract-address](../../Reference/Pantheon-CLI-Syntax.md#permissions-accounts-contract-address)
set to the address of the Account Ingress contract in the genesis file (`"0x0000000000000000000000000000000000008888"`)

* [--permissions-nodes-contract-enabled](../../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-enabled)
to enable onchain nodes permissioning

* [--permissions-nodes-contract-address](../../Reference/Pantheon-CLI-Syntax.md#permissions-nodes-contract-address)
set to the address of the Node Ingress contract in the genesis file (`"0x0000000000000000000000000000000000009999"`)  

Start your first node with command line options to enable onchain permissioning and the JSON-RPC HTTP host and port 
matching environment variable `PANTHEON_NODE_PERM_ENDPOINT`. 

## Clone Project and Install Dependencies 

1. Clone the `permissioning-smart-contracts` repository: 

    ```bash 
    git clone https://github.com/PegaSysEng/permissioning-smart-contracts.git
    ```

1. Change into the `permissioning-smart-contracts` directory and run:  

    ```bash
    yarn install
    ```
    
## Build Project

In the `permissioning-smart-contracts` directory, build the project:

```bash
yarn run build
```

## Deploy Contracts 
    
In the `permissioning-smart-contracts` directory, deploy the Admin and Rules contracts: 

```bash
truffle migrate --reset
```

The Admin and Rules contracts are deployed and the Ingress contract updated with the name and version of the contracts. 
The migration logs the addresses of the Admin and Rules contracts. 

!!! important 
    The account that deploys the contracts is automatically an [admin account](#update-accounts-or-admin-accounts-whitelists). 

## Start the Development Server for the Permissioning Management Dapp

!!! note 
    In production, a webserver is required to [host the Permissioning Management Dapp](Production.md). 

1. In the `permissioning-smart-contracts` directory, start the web server serving the Dapp: 

    ```bash
    yarn start
    ```

    The Dapp is displayed at [http://localhost:3000](http://localhost:3000). 

1. Ensure MetaMask is connected to your local node (by default `http://localhost:8545`). 

    A MetaMask notification is displayed requesting permission for Pantheon Permissioning to 
   connect to your account. 

1. Click the _Connect_ button. 

    The Dapp is displayed with the account specified by the `PANTHEON_NODE_PERM_ACCOUNT` environment variable 
   in the _Whitelisted Accounts_ and _Admin Accounts_ tabs. 

!!! note  
    Only [admin accounts](#update-accounts-or-admin-accounts-whitelists) can add or remove nodes from the whitelist. 

## Add First Node to Whitelist 

The first node must [add itself to the whitelist](Updating-Whitelists.md#update-nodes-whitelist) before adding other nodes.

