description: Onchain Permissioning
<!--- END of page meta data -->

# Onchain Permissioning 

Onchain permissioning uses smart contracts to store and maintain the node, account, and admin whitelists. 
Using onchain permissioning enables all nodes to read the whitelists from a single source, the blockchain.                                        

!!! important 
    The dependency chain for our implementation of onchain permissioning includes [web3js](https://github.com/ethereum/web3.js/) which is 
    LGPL licensed.  

## Permissioning Contracts

The permissioning smart contracts are provided in the [PegaSysEng/permissioning-smart-contracts](https://github.com/PegaSysEng/permissioning-smart-contracts) repository: 

* Ingress contracts for nodes and accounts - proxy contracts defined in the genesis file that defer the permissioning logic to the 
Node Rules and Account Rules contracts. The Ingress contracts are deployed to static addresses. 

* Node Rules - stores the node whitelist and node whitelist operations (for example, add and remove). 

* Account Rules - stores the accounts whitelist and account whitelist operations (for example, add and remove). 

* Admin - stores the list of admin accounts and admin list operations (for example, add and remove). There is 
one list of admin accounts for node and accounts.

!!! note
    The permissioning smart contracts are currently in the process of going through a third party audit. 
    Please [contact us](https://pegasys.tech/contact/) before using in a production environment.

## Permissioning Management Dapp

The [Permissioning Management Dapp](Getting-Started-Onchain-Permissioning.md) is provided to view 
and maintain the whitelists. 

!!! tip 
    Before v1.2, we provided a [management interface using Truffle](https://docs.pantheon.pegasys.tech/en/1.1.4/Permissions/Onchain-Permissioning/).
    The management interface using Truffle is deprecated and we recommend using the Dapp for an improved user experience. 

### Whitelists 

Permissioning implements three whitelists: 

* Accounts can submit transactions to the network

* Nodes can participate in the network 

* Admins are accounts that can update the accounts and nodes whitelists 

## Bootnodes

When a node is added to the network, it connects to the bootnodes until it synchronizes to the chain head regardless of
node permissions. Once in sync, the permissioning rules in the Account Rules and Node Rules smart contracts are applied.  

If a sychronized node loses all peer connections (that is, it has 0 peers), it reconnects to the bootnodes to 
rediscover peers.  

