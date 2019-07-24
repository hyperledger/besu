description: web3.js-eea Client Library 
<!--- END of page meta data -->

# web3.js-eea Client Library

The [web3.js-eea library](https://github.com/PegaSysEng/eeajs) adds an additional property to your web3 
instance by extending [web3](https://github.com/ethereum/web3.js/). Use the library to create and send 
RLP-encoded transactions using JSON-RPC.

!!! note
    web3.js-eea supports JSON-RPC over HTTP only. 

## Prerequisites

- [Node.js (version > 10)](https://nodejs.org/en/download/)  

## Add web3.js-eea to Project 

```bash
npm install web3-eea
```

## Initialize EEA Client 

Initilize your EEA client where: 

* `<JSON-RPC HTTP endpoint>` is the JSON-RPC HTTP endpoint of your Pantheon node. Specified by the 
[`--rpc-http-host`](../../Reference/Pantheon-CLI-Syntax.md#rpc-http-host) and [`--rpc-http-port`](../../Reference/Pantheon-CLI-Syntax.md#rpc-http-port) 
command line options.
* `<chain_id>` is the [chain ID](../../Configuring-Pantheon/NetworkID-And-ChainID.md) of your network. 

!!! example
    ```js tab="Syntax"
    const EEAClient = require("web3-eea");
    const web3 = new EEAClient(new Web3("<JSON-RPC HTTP endpoint>", <chain_id>);
    ```
    
    ```js tab="Example"
    const EEAClient = require("web3-eea");
    const web3 = new EEAClient(new Web3("http://localhost:8545", 2018);
    ```

## Deploying a Contract with sendRawTransaction 

To deploy a private contract, you need the contract binary. You can use [Solidity](https://solidity.readthedocs.io/en/develop/using-the-compiler.html)
to get the contract binary. 

!!! example "Deploying a Contract with sendRawTransaction"  
    ```js
    const contractOptions = {
      data: `0x123`, // contract binary
      privateFrom: "orionNode1PublicKey",
      privateFor: ["orionNode3PublicKey"],
      privateKey: "pantheonNode1PrivateKey"
    };
    return web3.eea.sendRawTransaction(contractOptions);
    ```

The transaction hash is returned. To get the private transaction receipt, use `web3.eea.getTransactionReceipt(txHash)`. 

## Reference 

Refer to [web3.js-eea reference](../../Reference/web3js-eea-Methods.md) for more methods and examples. 