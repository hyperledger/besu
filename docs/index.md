title: Pantheon Enterprise Ethereum Client
description: Pantheon is an open-source Enterprise Ethereum client developed under the Apache 2.0 license and written in Java. It runs on the Ethereum public network, private networks, and test networks.
<!--- END of page meta data -->

# Pantheon Enterprise Ethereum Client

## What is Pantheon?

Pantheon is an open-source Ethereum client developed under the Apache 2.0 license and written in Java. 
It runs on the Ethereum public network, private networks, and test networks such as Rinkeby, Ropsten,
and Görli. Pantheon implements Proof of Work (Ethash) and Proof of Authority (IBFT 2.0 and Clique) consensus
mechanisms. 

You can use Pantheon to develop enterprise applications requiring secure, high-performance transaction 
processing in a private network. 

Pantheon supports enterprise features including privacy and permissioning. 

## What can you do with Pantheon?

Pantheon includes a [command line interface](Reference/Pantheon-CLI-Syntax.md) and [JSON-RPC API](JSON-RPC-API/JSON-RPC-API.md)
for running, maintaining, debugging, and monitoring nodes in an Ethereum network. You can use the API via RPC
over HTTP or via WebSockets, and Pub/Sub is supported. The API supports typical Ethereum functionalities such as:

* Ether token mining
* Smart contract development
* Decentralized application (Dapp) development

## What does Pantheon support?

The Pantheon client supports common smart contract and Dapp development, deployment, and operational use cases, using tools such as [Truffle](http://truffleframework.com/), [Remix](https://github.com/ethereum/remix), and [web3j](https://web3j.io/). The client supports common JSON-RPC API methods such as eth, net, web3, debug, and miner.

Pantheon doesn't support [Account management](Using-Pantheon/Account-Management.md).
