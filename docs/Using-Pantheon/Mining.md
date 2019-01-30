description: Using Pantheon for PoW CPU mining
<!--- END of page meta data -->

# Mining

Start Pantheon with the following options to enable CPU mining: 
```bash
pantheon --miner-enabled --miner-coinbase <account>
```

Where `<account>` is the account to which mining rewards are to be paid. For example, `fe3b557e8fb62b89f4916b721be55ceb828dbd73`.

JSON-RPC API methods for mining are:

* [`miner_start`](../Reference/JSON-RPC-API-Methods.md#miner_start) to start mining. 
* [`miner_stop`](../Reference/JSON-RPC-API-Methods.md#miner_stop) to stop mining. 
* [`eth_mining`](../Reference/JSON-RPC-API-Methods.md#eth_mining) to determine whether the client is actively mining new blocks.   
* [`eth_hashrate`](../Reference/JSON-RPC-API-Methods.md#eth_hashrate) to get the number of hashes per second with which the node is mining. 