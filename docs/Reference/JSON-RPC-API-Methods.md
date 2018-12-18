description: Pantheon JSON-RPC API methods reference
<!--- END of page meta data -->

# JSON-RPC API Methods

!!! danger "Breaking Change in v0.8.3"
    From v0.8.3, incoming HTTP requests are only accepted from hostnames specified using the [`--host-whitelist`](Using-JSON-RPC-API.md#breaking-change-in-v083) option. 

The following lists the Pantheon JSON-RPC API commands:

## Admin Methods

### admin_peers

Returns networking information about connected remote nodes. 

**Parameters**

None

**Returns**

`result` : *array* of *objects* - Object returned for each remote node. 

Properties of the remote node object are:

* `version` - P2P protocol version
* `name` - Client name
* `caps` - P2P message capabilities 
* `network` - Addresses of local node and remote node
* `port` - Port
* `id` - Node public key. Excluding the `0x` prefix, the node public key is the ID in the enode URL `enode://<id ex 0x>@<host:port>`. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"admin_peers","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"admin_peers","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ 
        {
          "version" : "0x5",
          "name" : "Geth/v1.8.20-unstable-92639b67/linux-amd64/go1.11.1",
          "caps" : [
            "eth/62", 
            "eth/63"
            ],
          "network" : {
            "localAddress" : "192.168.1.229:51279",
            "remoteAddress" : "52.3.158.184:30303"
          },
          "port" : "0x0",
          "id" : "0x343149e4feefa15d882d9fe4ac7d88f885bd05ebb735e547f12e12080a9fa07c8014ca6fd7f373123488102fe5e34111f8509cf0b7de3f5b44339c9f25e87cb8"
        } 
      ]
    }
    ```

## Web3 Methods

### web3_clientVersion

Returns the current client version.

**Parameters**

None

**Returns**

`result` : *string* - Current client version.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "pantheon/1.0.0"
    }
    ```

### web3_sha3

Returns a [SHA3](https://en.wikipedia.org/wiki/SHA-3) hash of the specified data. The result value is a [Keccak-256](https://keccak.team/keccak.html) hash, not the standardized SHA3-256.

**Parameters**

`DATA` - Data to convert to a SHA3 hash.

**Returns**

`result` (*DATA*) - SHA3 result of the input data.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"web3_sha3","params":["0x68656c6c6f20776f726c00"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"web3_sha3","params":["0x68656c6c6f20776f726c00"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x5e39a0a66544c0668bde22d61c47a8710000ece931f13b84d3b2feb44ec96d3f"
    }
    ```

## Net Methods

### net_version

Returns the current chain ID.

**Parameters**

None

**Returns**

`result` : *string* - Current chain ID.
- `1` - Ethereum Mainnet
- `2` - Morden Testnet  (deprecated)
- `3` - Ropsten Testnet
- `4` - Rinkeby Testnet
- `42` - Kovan Testnet

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data ''{"jsonrpc":"2.0","method":"net_version","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    $ wscat -c <JSON-RPC-ws-endpoint:port>
    connected (press CTRL+C to quit)
    > '{"jsonrpc":"2.0","method":"net_version","params":[],"id":53}
    ```
    
    ```json tab="JSON result for Mainnet"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "1"
    }
    ```    
    
    ```json tab="JSON result for Ropsten"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "3"
    }
    ```

### net_listening

Indicates whether the client is actively listening for network connections.

**Parameters**

None

**Returns**

`result` (*BOOLEAN*) - `true` if the client is actively listening for network connections; otherwise `false`.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"net_listening","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_listening","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : true
    }
    ```

### net_peerCount

Returns the number of peers currently connected to the client.

**Parameters**

None

**Returns**

`result` : *integer* - Number of connected peers in hexadecimal.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x5"
    }
    ```

## Eth Methods

### eth_syncing

Returns an object with data about the synchronization status, or `false` if not synchronizing.

**Parameters**

None

**Returns**

`result` : *Object|Boolean* - Object with synchronization status data or `false`, when not synchronizing:

* `startingBlock` : *quantity* - Index of the highest block on the blockchain when the network synchronization starts.

    If you start with an empty blockchain, the starting block is the beginning of the blockchain (`startingBlock` = 0).

    If you import a block file using `pantheon import <block-file>`, the synchronization starts at the head of the blockchain, and the starting block is the next block synchronized. For example, if you imported 1000 blocks, the import would include blocks 0 to 999, so in that case `startingBlock` = 1000.

* `currentBlock` : *quantity* - Index of the latest block (also known as the best block) for the current node. This is the same index that [eth_blockNumber](#eth_blocknumber) returns.

* `highestBlock`: *quantity* - Index of the highest known block in the peer network (that is, the highest block so far discovered among peer nodes). This is the same value as `currentBlock` if the current node has no peers.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_syncing","params":[],"id":51}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_syncing","params":[],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : {
        "startingBlock" : "0x5a0",
        "currentBlock" : "0xad9",
        "highestBlock" : "0xad9"
      }
    }
    ```

### eth_chainId

Returns the [chain ID](../Configuring-Pantheon/NetworkID-And-ChainID.md).

!!!note
    This method is only available from v0.8.2 or when you [build from source](../Installation/Build-From-Source.md). 

**Parameters**

None

**Returns**

`result` : *quantity* - Chain ID in hexadecimal.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":51}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "0x7e2"
    }
    ```

### eth_coinbase

Returns the client coinbase address. The coinbase address is the account to which mining rewards are paid. 

To set a coinbase address, start Pantheon with the `--miner-coinbase` option set to a valid Ethereum account address.
You can get the Ethereum account address from a client such as MetaMask or Etherscan. For example:

!!!example
    ```bash
    $ bin/pantheon --miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73" --rpc-enabled
    ```

**Parameters**

None

**Returns**

`result` : *data* - Coinbase address.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_coinbase","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_coinbase","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    }
    ```

### eth_mining

Indicates whether the client is actively mining new blocks. Mining is paused while the client synchronizes with the network regardless of command settings or methods called. 

**Parameters**

None

**Returns**

`result` (*BOOLEAN*) - `true` if the client is actively mining new blocks; otherwise `false`.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_mining","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_mining","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : true
    }
    ```

### eth_gasPrice

Returns the current gas unit price in wei.

**Parameters**

None

**Returns**

`result` : *quantity* - Current gas unit price in wei as a hexadecimal value.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x3e8"
    }
    ```

### eth_accounts

Returns a list of account addresses that the client owns.

!!!note
    This method returns an empty object because Pantheon [does not support account management](Using-JSON-RPC-API.md#account-management-not-supported-by-pantheon).

**Parameters**

None

**Returns**

`Array of data` : List of 20-byte account addresses owned by the client.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_accounts","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_accounts","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : [ ]
    }
    ```

### eth_blockNumber

Returns the index of the current block the client is processing.

**Parameters**

None

**Returns**

`result` : *QUANTITY* - Hexadecimal integer representing the 0-based index of the block that the client is currently processing.


!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":51}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "0x2377"
    }
    ```

### eth_getBalance

Returns the account balance of the specified address.

**Parameters**

`DATA` - 20-byte account address from which to retrieve the balance.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer value of the current balance in wei.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBalance","params":["0xdd37f65db31c107f773e82a4f85c693058fef7a9", "latest"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getBalance","params":["0xdd37f65db31c107f773e82a4f85c693058fef7a9", "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x0"
    }
    ```

### eth_getStorageAt

Returns the value of a storage position at a specified address.

**Parameters**

`DATA` - A 20-byte storage address.

`QUANTITY` - Integer index of the storage position.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](JSON-RPC-API-Objects.md#block-parameter).

**Returns**

`result` : *DATA* - The value at the specified storage position.

!!! example
    Calculating the correct position depends on the storage you want to retrieve.

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method": "eth_getStorageAt","params": ["0x‭3B3F3E‬","0x0","latest"],"id": 53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method": "eth_getStorageAt","params": ["0x‭3B3F3E‬","0x0","latest"],"id": 53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x0000000000000000000000000000000000000000000000000000000000000000"
    }
    ```

### eth_getTransactionCount

Returns the number of transactions sent from a specified address.

**Parameters**

`DATA` - 20-byte account address.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer representing the number of transactions sent from the specified address.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionCount","params":["0xc94770007dda54cF92009BFF0dE90c06F603a09f","latest"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getTransactionCount","params":["0xc94770007dda54cF92009BFF0dE90c06F603a09f","latest"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : "0x1"
    }
    ```

### eth_getBlockTransactionCountByHash

Returns the number of transactions in the block matching the given block hash.

**Parameters**

`DATA` - 32-byte block hash.

**Returns**

`result` : *QUANTITY* - Integer representing the number of transactions in the specified block.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : null
    }
    ```

### eth_getBlockTransactionCountByNumber

Returns the number of transactions in a block matching the specified block number.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer representing the number of transactions in the specified block.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByNumber","params":["0xe8"],"id":51}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByNumber","params":["0xe8"],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "0x8"
    }
    ```

### eth_getUncleCountByBlockHash

Returns the number of uncles in a block from a block matching the given block hash.

**Parameters**

`DATA` - 32-byte block hash.

**Returns**

`result` : *QUANTITY* - Integer representing the number of uncles in the specified block.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleCountByBlockHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getUncleCountByBlockHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : null
    }
    ```

### eth_getUncleCountByBlockNumber

Returns the number of uncles in a block matching the specified block number.

**Parameters**

`QUANTITY|TAG` - Integer representing either the 0-based index of the block within the blockchain, or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer representing the number of uncles in the specified block.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleCountByBlockNumber","params":["0xe8"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getUncleCountByBlockNumber","params":["0xe8"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : "0x1"
    }
    ```

### eth_getCode

Returns the code of the smart contract at the specified address. Compiled smart contract code is stored as a hexadecimal value. 

**Parameters**

`DATA` - 20-byte contract address.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *DATA* - Code stored at the specified address.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getCode","params":["0xa50a51c09a5c451c52bb714527e1974b686d8e77", "latest"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getCode","params":["0xa50a51c09a5c451c52bb714527e1974b686d8e77", "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 53,
        "result": "0x60806040526004361060485763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f2458114604d57806355241077146071575b600080fd5b348015605857600080fd5b50605f6088565b60408051918252519081900360200190f35b348015607c57600080fd5b506086600435608e565b005b60005481565b60008190556040805182815290517f199cd93e851e4c78c437891155e2112093f8f15394aa89dab09e38d6ca0727879181900360200190a1505600a165627a7a723058209d8929142720a69bde2ab3bfa2da6217674b984899b62753979743c0470a2ea70029"
    }
    ```

### eth_sendRawTransaction

Sends a signed transaction. A transaction can send ether, deploy a contract, or interact with a contract.  

You can interact with contracts using [eth_sendRawTransaction or eth_call](../Using-Pantheon/Transactions.md#eth_call-or-eth_sendrawtransaction).

To avoid exposing your private key, create signed transactions offline and send the signed transaction data using this method. For information on creating signed transactions and using `eth_sendRawTransaction`, refer to [Using Pantheon](../Using-Pantheon/Transactions.md).  

!!!important
    Pantheon does not implement [eth_sendTransaction](Using-JSON-RPC-API.md#account-management-not-supported-by-pantheon).

**Parameters**

`DATA` - Hash of the signed raw transaction in hexadecimal format; for example:

`params: ["0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"]`


**Returns**

`result` : `DATA` - 32-byte transaction hash, or zero hash if the transaction is not yet available.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "id":1,
      "jsonrpc": "2.0",
      "result": "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1527331"
    }
    ```

### eth_call

Invokes a contract function locally and does not change the state of the blockchain. 

You can interact with contracts using [eth_sendRawTransaction or eth_call](../Using-Pantheon/Transactions.md#eth_call-or-eth_sendrawtransaction).

**Parameters**

*OBJECT* - [Transaction call object](JSON-RPC-API-Objects.md#transaction-call-object).

*QUANTITY|TAG* - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](JSON-RPC-API-Objects.md#block-parameter).

**Returns**

`result` (*DATA*) - Return value of the executed contract.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13","value":"0x1"}, "latest"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_call","params":[{"to":"0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13","value":"0x1"}, "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 53,
        "result": "0x"
    }
    ```

### eth_estimateGas

Generates and returns an estimate of how much gas is necessary to allow the transaction to complete. (Per Etherscan: gas price * gas used.) The transaction is added to the blockchain. The estimate may be significantly more than the amount of gas actually used by the transaction for reasons including EVM mechanics and node performance.

**Parameters**

!!!note
    Parameters are the same as the eth_call parameters, except that all properties are optional. If you do not specify a `gas` limit, Pantheon uses the gas limit from the pending block as an upper bound. As a result, the returned estimate might not be enough to execute the call or transaction when the amount of gas is higher than the pending block's gas limit.

*OBJECT* - [Transaction call object](JSON-RPC-API-Objects.md#transaction-call-object).

*QUANTITY|TAG* - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` (*QUANTITY*) -  Amount of gas used.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0x687422eea2cb73b5d3e242ba5456b782919afc85","to":"0xdd37f65db31c107f773e82a4f85c693058fef7a9","value":"0x1"}],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0x687422eea2cb73b5d3e242ba5456b782919afc85","to":"0xdd37f65db31c107f773e82a4f85c693058fef7a9","value":"0x1"}],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 54,
      "result" : "0x5208"
    }
    ```

### eth_getBlockByHash

Returns information about the block by hash.

**Parameters**

`DATA` - 32-byte hash of a block.

`Boolean` - If `true`, returns the full [transaction objects](JSON-RPC-API-Objects.md#transaction-object); if `false`, returns the transaction hashes.

**Returns**

`result` : *OBJECT* - [Block object](JSON-RPC-API-Objects.md#block-object) , or `null` when no block is found. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockByHash","params":["0x16b69965a5949262642cfb5e86368ddbbe57ab9f17d999174a65fd0e66580d8f", false],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getBlockByHash","params":["0x16b69965a5949262642cfb5e86368ddbbe57ab9f17d999174a65fd0e66580d8f", false],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : {
        "number" : "0x7",
        "hash" : "0x16b69965a5949262642cfb5e86368ddbbe57ab9f17d999174a65fd0e66580d8f",
        "parentHash" : "0xe9bd4b277983580ef0eabad6011891f8b6aff9381a78bd1c4faca374a48b3e09",
        "nonce" : "0x46acb59e85b5bb6d",
        "sha3Uncles" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
        "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "transactionsRoot" : "0x7aa0913c235f272eb6ed6ab74ba5a057e0a62c1c1d1dbccfd971221e6b6e83a3",
        "stateRoot" : "0xfaf6520d6e3d24107a4309855593341ab87a1744dbb6eea4e709b92e9c9107ca",
        "receiptsRoot" : "0x056b23fbba480696b65fe5a59b8f2148a1299103c4f57df839233af2cf4ca2d2",
        "miner" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
        "difficulty" : "0x5",
        "totalDifficulty" : "0x10023",
        "extraData" : "0x",
        "size" : "0x270",
        "gasLimit" : "0x1000000",
        "gasUsed" : "0x5208",
        "timestamp" : "0x5bbbe99f",
        "uncles" : [ ],
        "transactions" : [ "0x2cc6c94c21685b7e0f8ddabf277a5ccf98db157c62619cde8baea696a74ed18e" ]
      }
    }
    ```

### eth_getBlockByNumber

Returns information about a block by block number.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

`Boolean` - If `true`, returns the full [transaction objects](JSON-RPC-API-Objects.md#transaction-object); if `false`, returns only the hashes of the transactions.

**Returns**

`result` : *OBJECT* - [Block object](JSON-RPC-API-Objects.md#block-object) , or `null` when no block is found. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["0x64", true],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["0x64", true],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : {
        "number" : "0x64",
        "hash" : "0xdfe2e70d6c116a541101cecbb256d7402d62125f6ddc9b607d49edc989825c64",
        "parentHash" : "0xdb10afd3efa45327eb284c83cc925bd9bd7966aea53067c1eebe0724d124ec1e",
        "nonce" : "0x37129c7f29a9364b",
        "sha3Uncles" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
        "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        "transactionsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
        "stateRoot" : "0x90c25f6d7fddeb31a6cc5668a6bba77adbadec705eb7aa5a51265c2d1e3bb7ac",
        "receiptsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
        "miner" : "0xbb7b8287f3f0a933474a79eae42cbca977791171",
        "difficulty" : "0x42be722b6",
        "totalDifficulty" : "0x19b5afdc486",
        "extraData" : "0x476574682f4c5649562f76312e302e302f6c696e75782f676f312e342e32",
        "size" : "0x21e",
        "gasLimit" : "0x1388",
        "gasUsed" : "0x0",
        "timestamp" : "0x55ba43eb",
        "uncles" : [ ],
        "transactions" : [ ]
      }
    }
    ```

### eth_getTransactionByHash

Returns transaction information for the specified transaction hash.

**Parameters**

`DATA` - 32-byte transaction hash.

**Returns**

Object - [Transaction object](JSON-RPC-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByHash","params":["0xa52be92809541220ee0aaaede6047d9a6c5d0cd96a517c854d944ee70a0ebb44"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getTransactionByHash","params":["0xa52be92809541220ee0aaaede6047d9a6c5d0cd96a517c854d944ee70a0ebb44"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : {
        "blockHash" : "0x510efccf44a192e6e34bcb439a1947e24b86244280762cbb006858c237093fda",
        "blockNumber" : "0x422",
        "from" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
        "gas" : "0x5208",
        "gasPrice" : "0x3b9aca00",
        "hash" : "0xa52be92809541220ee0aaaede6047d9a6c5d0cd96a517c854d944ee70a0ebb44",
        "input" : "0x",
        "nonce" : "0x1",
        "to" : "0x627306090abab3a6e1400e9345bc60c78a8bef57",
        "transactionIndex" : "0x0",
        "value" : "0x4e1003b28d9280000",
        "v" : "0xfe7",
        "r" : "0x84caf09aefbd5e539295acc67217563438a4efb224879b6855f56857fa2037d3",
        "s" : "0x5e863be3829812c81439f0ae9d8ecb832b531d651fb234c848d1bf45e62be8b9"
      }
    }
    ```

### eth_getTransactionByBlockHashAndIndex

Returns transaction information for the specified block hash and transaction index position.

**Parameters**

`DATA` - 32-byte hash of a block.

`QUANTITY` - Integer representing the transaction index position.

**Returns**

Object - [Transaction object](JSON-RPC-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByBlockHashAndIndex","params":["0xbf137c3a7a1ebdfac21252765e5d7f40d115c2757e4a4abee929be88c624fdb7", "0x2"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getTransactionByBlockHashAndIndex","params":["0xbf137c3a7a1ebdfac21252765e5d7f40d115c2757e4a4abee929be88c624fdb7", "0x2"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : {
        "blockHash" : "0xbf137c3a7a1ebdfac21252765e5d7f40d115c2757e4a4abee929be88c624fdb7",
        "blockNumber" : "0x1442e",
        "from" : "0x70c9217d814985faef62b124420f8dfbddd96433",
        "gas" : "0x3d090",
        "gasPrice" : "0x57148a6be",
        "hash" : "0xfc766a71c406950d4a4955a340a092626c35083c64c7be907060368a5e6811d6",
        "input" : "0x51a34eb8000000000000000000000000000000000000000000000029b9e659e41b780000",
        "nonce" : "0x2cb2",
        "to" : "0xcfdc98ec7f01dab1b67b36373524ce0208dc3953",
        "transactionIndex" : "0x2",
        "value" : "0x0",
        "v" : "0x2a",
        "r" : "0xa2d2b1021e1428740a7c67af3c05fe3160481889b25b921108ac0ac2c3d5d40a",
        "s" : "0x63186d2aaefe188748bfb4b46fb9493cbc2b53cf36169e8501a5bc0ed941b484"
      }
     }
    ```

### eth_getTransactionByBlockNumberAndIndex

Returns transaction information for the specified block number and transaction index position.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

`QUANTITY` - The transaction index position.

**Returns**

Object - [Transaction object](JSON-RPC-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!!note
    Your node must be synchronized to at least the block containing the transaction for the request to return it.

!!! example
    This request returns the third transaction in the 82990 block on the Ropsten testnet. You can also view this [block](https://ropsten.etherscan.io/txs?block=82990) and [transaction](https://ropsten.etherscan.io/tx/0xfc766a71c406950d4a4955a340a092626c35083c64c7be907060368a5e6811d6) on Etherscan.

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByBlockNumberAndIndex","params":["82990", "0x2"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getTransactionByBlockNumberAndIndex","params":["82990", "0x2"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : {
        "blockHash" : "0xbf137c3a7a1ebdfac21252765e5d7f40d115c2757e4a4abee929be88c624fdb7",
        "blockNumber" : "0x1442e",
        "from" : "0x70c9217d814985faef62b124420f8dfbddd96433",
        "gas" : "0x3d090",
        "gasPrice" : "0x57148a6be",
        "hash" : "0xfc766a71c406950d4a4955a340a092626c35083c64c7be907060368a5e6811d6",
        "input" : "0x51a34eb8000000000000000000000000000000000000000000000029b9e659e41b780000",
        "nonce" : "0x2cb2",
        "to" : "0xcfdc98ec7f01dab1b67b36373524ce0208dc3953",
        "transactionIndex" : "0x2",
        "value" : "0x0",
        "v" : "0x2a",
        "r" : "0xa2d2b1021e1428740a7c67af3c05fe3160481889b25b921108ac0ac2c3d5d40a",
        "s" : "0x63186d2aaefe188748bfb4b46fb9493cbc2b53cf36169e8501a5bc0ed941b484"
      }
    }
    ```

### eth_getTransactionReceipt

Returns the receipt of a transaction by transaction hash. Receipts for pending transactions are not available.

**Parameters**

`DATA` - 32-byte hash of a transaction.

**Returns**

`Object` - [Transaction receipt object](JSON-RPC-API-Objects.md#transaction-receipt-object), or `null` when no receipt is found.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["0x504ce587a65bdbdb6414a0c6c16d86a04dd79bfcc4f2950eec9634b30ce5370f"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["0x504ce587a65bdbdb6414a0c6c16d86a04dd79bfcc4f2950eec9634b30ce5370f"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "blockHash": "0xe7212a92cfb9b06addc80dec2a0dfae9ea94fd344efeb157c41e12994fcad60a",
            "blockNumber": "0x50",
            "contractAddress": null,
            "cumulativeGasUsed": "0x5208",
            "from": "0x627306090abab3a6e1400e9345bc60c78a8bef57",
            "gasUsed": "0x5208",
            "logs": [],
            "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            "status": "0x1",
            "to": "0xf17f52151ebef6c7334fad080c5704d77216b732",
            "transactionHash": "0xc00e97af59c6f88de163306935f7682af1a34c67245e414537d02e422815efc3",
            "transactionIndex": "0x0"
        }
    }
    ```

### eth_newFilter

Creates a topic filter with the specified options to notify (log) when the state changes. To determine whether the state has changed, call [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

`Object` - [Filter options object](JSON-RPC-API-Objects.md#filter-options-object). 

!!!note
    Topics are order-dependent. A transaction with a log containing topics `[A, B]` would be matched with the following topic filters:
    
    * [] - Match any topic
    * [A] - Match A in first position (and any topic thereafter)
    * [null, B] - Match any topic in first position AND B in second position (and any topic thereafter)
    * [A, B] - Match A in first position AND B in second position (and any topic thereafter)
    * [[A, B], [A, B]] - Match (A OR B) in first position AND (A OR B) in second position (and any topic thereafter)

    For example, params could be specified as follows:
    !!!example
        ```json
        [{
          "fromBlock": "earliest",
          "toBlock": "0x4",
          "address": "0xc94770007dda54cF92009BFF0dE90c06F603a09f",
          "topics": ["0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b", null, ["0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b", "0x0000000000000000000000000aff3454fce5edbc8cca8697c15331677e6ebccc"]]
        }]
        ```

**Returns**

`result` : *QUANTITY* - Filter ID.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newFilter","params":[{"topics":[]}],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newFilter","params":[{"topics":[]}],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x3"
    }
    ```

Invalid params error:

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newFilter","params":[{"fromBlock": "earliest","toBlock": "latest","address": "0xDD37f65dB31c107F773E82a4F85C693058fEf7a9","topics": []}],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newFilter","params":[{"fromBlock": "earliest","toBlock": "latest","address": "0xDD37f65dB31c107F773E82a4F85C693058fEf7a9","topics": []}],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "error" : {
        "code" : -32602,
        "message" : "Invalid params"
      }
    }
    ```

### eth_newBlockFilter

Creates a filter in the node that notifies when a new block arrives. To determine whether the state has changed, call [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

None

**Returns**

`QUANTITY` - Hexadecimal integer filter ID. Each time you call this method, it creates a new filter, and the index is incremented by 1.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newBlockFilter","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newBlockFilter","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x3"
    }
    ```

### eth_newPendingTransactionFilter

Creates a filter in the node that notifies when new pending transactions arrive. To check if the state has changed, call [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

None

**Returns**

`QUANTITY` - Hexadecimal integer filter ID. Each time you call this method, it creates a new filter, and the index is incremented by 1.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newPendingTransactionFilter","params":[],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newPendingTransactionFilter","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x4"
    }
    ```

### eth_uninstallFilter

Uninstalls a filter with the specified ID. This method should always be called when notification is no longer needed. Note that filters time out when they are not requested with [eth_getFilterChanges](#eth_getfilterchanges) for a period of time.

This method deletes filters of any type: block filters, pending transaction filters, and state (topic) filters.


**Parameters**

`QUANTITY` - Hexadecimal integer filter ID specifying the filter to be deleted.

**Returns**

`Boolean` - `true` if the filter was successfully uninstalled; otherwise `false`.

!!! example
    The following request deletes the block filter with an ID of 0x4:

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_uninstallFilter","params":["0x4"],"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_uninstallFilter","params":["0x4"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : true
    }
    ```

### eth_getFilterChanges

Polls the specified filter and returns an array of logs that have occurred since the last poll.

**Parameters**

`QUANTITY` - Hexadecimal integer filter ID.

**Returns**

`result` : `Array of Object` - List of logs, or an empty array if nothing has changed since the last poll.

* For filters created with `eth_newBlockFilter`, returns 32-byte *DATA* block hashes; for example `["0x3454645634534..."]`.
* For filters created with `eth_newPendingTransactionFilter`, returns transaction hashes (32-byte *DATA*); for example `["0x6345343454645..."]`.
* For filters created with `eth_newFilter`, returns [log objects](JSON-RPC-API-Objects.md#log-object). 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterChanges","params":["0xa"]:"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getFilterChanges","params":["0xa"]:"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : [ ]
    }
    ```

### eth_getFilterLogs

Returns an array of logs matching the filter with the specified ID.

**Parameters**

`QUANTITY` - Integer representing the filter ID.

**Returns**

Same as [eth_getFilterChanges](#eth_getfilterchanges).

!!! example
    The following example requests logs for filter ID 0x16 (22 decimal):

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterLogs","params":["0x3"]"id":53}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getFilterLogs","params":["0x3"]"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0x14427",
        "blockHash" : "0x677bf4b962464e6dfd548d6a30b6c703dd78c7cc3602825a7013a6e90a001d2a",
        "transactionHash" : "0x7bf9876a9de3c0add38495e21a17b96c81b3f18e0990a4a3aecdf9f47fea0eed",
        "transactionIndex" : "0x0",
        "address" : "0xe8fe77d1576d0972d453b49bfaa84d716173d133",
        "data" : "0x0000000000000000000000001046c9bdec0e634fbd7cf91afebd93cc854432b10000000000000000000000002101416eeaf73acb66d124f79efde9631662a83a0000000000000000000000006f72045702a34c473da863945221965c61528bd3",
        "topics" : [ "0xc36800ebd6079fdafc3a7100d0d1172815751804a6d1b7eb365b85f6c9c80e61", "0x000000000000000000000000b344324aa2a82a6fda8459e40923e1fd65bfac36" ]
      } ]
    }
    ```

### eth_getLogs

Returns an array of all logs matching a specified filter object.

**Parameters**

`Object` - [Filter options object](JSON-RPC-API-Objects.md#filter-options-object)

**Returns**

Same as [eth_getFilterChanges](#eth_getfilterchanges).

!!!note
    You must be synchronized to at least the requested block for the request to return the logs.

!!! example
    The request above returns the logs for the 82893 block on the Ropsten testnet. You can also view this [block](https://ropsten.etherscan.io/block/82983) on Etherscan. 

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getLogs","params":[{"blockhash":"0x677bf4b962464e6dfd548d6a30b6c703dd78c7cc3602825a7013a6e90a001d2a"}], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getLogs","params":[{"blockhash":"0x677bf4b962464e6dfd548d6a30b6c703dd78c7cc3602825a7013a6e90a001d2a"}], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0x14427",
        "blockHash" : "0x677bf4b962464e6dfd548d6a30b6c703dd78c7cc3602825a7013a6e90a001d2a",
        "transactionHash" : "0x7bf9876a9de3c0add38495e21a17b96c81b3f18e0990a4a3aecdf9f47fea0eed",
        "transactionIndex" : "0x0",
        "address" : "0xe8fe77d1576d0972d453b49bfaa84d716173d133",
        "data" : "0x0000000000000000000000001046c9bdec0e634fbd7cf91afebd93cc854432b10000000000000000000000002101416eeaf73acb66d124f79efde9631662a83a0000000000000000000000006f72045702a34c473da863945221965c61528bd3",
        "topics" : [ "0xc36800ebd6079fdafc3a7100d0d1172815751804a6d1b7eb365b85f6c9c80e61", "0x000000000000000000000000b344324aa2a82a6fda8459e40923e1fd65bfac36" ]
      } ]
    }
    ```

### eth_getWork

Returns the hash of the current block, the seed hash, and the target boundary condition to be met.

**Parameters**

None

**Returns**

`result` : `Array of DATA` with the following fields:

* DATA, 32 Bytes - Hash of the current block header (pow-hash).
* DATA, 32 Bytes - The seed hash used for the DAG.
* DATA, 32 Bytes - The target boundary condition to be met; 2^256 / difficulty.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getWork","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getWork","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "id":1,
      "jsonrpc":"2.0",
      "result": [
          "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "0x5EED00000000000000000000000000005EED0000000000000000000000000000",
          "0xd1ff1c01710000000000000000000000d1ff1c01710000000000000000000000"
        ]
    }
    ```


## Clique Methods

### clique_discard

Discards a proposal to add or remove a signer with the specified address. 

**Parameters** 

`data` - 20-byte address of proposed signer. 

**Returns** 

`result: boolean` - `true`

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"clique_discard","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"clique_discard","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : true
    }
    ```

### clique_getSigners

Lists signers for the specified block. 

**Parameters** 

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter). 

**Returns**

`result: array of data` - List of 20-byte addresses of signers. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"clique_getSigners","params":["latest"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"clique_getSigners","params":["latest"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ "0x42eb768f2244c8811c63729a21a3569731535f06", "0x7ffc57839b00206d1ad20c69a1981b489f772031", "0xb279182d99e65703f0076e4812653aab85fca0f0" ]
    }
    ```
    
### clique_getSignersAtHash

Lists signers for the specified block.

**Parameters**

`data` - 32-byte block hash. 

**Returns** 

`result: array of data` - List of 20-byte addresses of signers.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"clique_getSignersAtHash","params":["0x98b2ddb5106b03649d2d337d42154702796438b3c74fd25a5782940e84237a48"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"clique_getSignersAtHash","params":["0x98b2ddb5106b03649d2d337d42154702796438b3c74fd25a5782940e84237a48"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ "0x42eb768f2244c8811c63729a21a3569731535f06", "0x7ffc57839b00206d1ad20c69a1981b489f772031", "0xb279182d99e65703f0076e4812653aab85fca0f0" ]
    }
    ```
    
### clique_propose

Proposes adding or removing a signer with the specified address. 

**Parameters**

`data` - 20-byte address.
 
`boolean` -  `true` to propose adding signer or `false` to propose removing signer. 

**Returns** 

`result: boolean` - `true`
   
!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"clique_propose","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73", true], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"clique_propose","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73", true], "id":1}
    ```
    
    ```json tab="JSON result"
    {
     "jsonrpc" : "2.0",
     "id" : 1,
     "result" : true
    }
    ```

### clique_proposals

Returns current proposals. 

**Parameters**

None

**Returns** 

`result`:_object_ - Map of account addresses to corresponding boolean values indicating the proposal for each account. 

If the boolean value is `true`, the proposal is to add a signer. If `false`, the proposal is to remove a signer. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"clique_proposals","params":[], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"clique_proposals","params":[], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "0x42eb768f2244c8811c63729a21a3569731535f07": false,
            "0x12eb759f2222d7711c63729a45c3585731521d01": true
        }
    }
    ```

## Debug Methods

### debug_metrics

!!!note
    This method is only available only from v0.8.3 or when [building from source](Installation).

Returns metrics providing information on the internal operation of Pantheon. 

The available metrics may change over time. The JVM metrics may vary based on the JVM implementation being used. 

The metric types are:

* Timer
* Counter
* Gauge

**Parameters**

None

**Returns**

`result`:`object`

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"debug_metrics","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_metrics","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "jvm": {
                "memory_bytes_init": {
                    "heap": 268435456,
                    "nonheap": 2555904
                },
                "threads_current": 41,
                "memory_bytes_used": {
                    "heap": 696923976,
                    "nonheap": 63633456
                },
                "memory_pool_bytes_used": {
                    "PS Eden Space": 669119360,
                    "Code Cache": 19689024,
                    "Compressed Class Space": 4871144,
                    "PS Survivor Space": 2716320,
                    "PS Old Gen": 25088296,
                    "Metaspace": 39073288
                },
                ...
            },
            "process": {
                "open_fds": 546,
                "cpu_seconds_total": 67.148992,
                "start_time_seconds": 1543897699.589,
                "max_fds": 10240
            },
            "rpc": {
                "request_time": {
                    "debug_metrics": {
                        "bucket": {
                            "+Inf": 2,
                            "0.01": 1,
                            "0.075": 2,
                            "0.75": 2,
                            "0.005": 1,
                            "0.025": 2,
                            "0.1": 2,
                            "1.0": 2,
                            "0.05": 2,
                            "10.0": 2,
                            "0.25": 2,
                            "0.5": 2,
                            "5.0": 2,
                            "2.5": 2,
                            "7.5": 2
                        },
                        "count": 2,
                        "sum": 0.015925392
                    }
                }
            },
            "blockchain": {
                "difficulty_total": 3533501,
                "announcedBlock_ingest": {
                    "bucket": {
                        "+Inf": 0,
                        "0.01": 0,
                        "0.075": 0,
                        "0.75": 0,
                        "0.005": 0,
                        "0.025": 0,
                        "0.1": 0,
                        "1.0": 0,
                        "0.05": 0,
                        "10.0": 0,
                        "0.25": 0,
                        "0.5": 0,
                        "5.0": 0,
                        "2.5": 0,
                        "7.5": 0
                    },
                    "count": 0,
                    "sum": 0
                },
                "height": 1908793
            },
            "peers": {
                "disconnected_total": {
                    "remote": {
                        "SUBPROTOCOL_TRIGGERED": 5
                    },
                    "local": {
                        "TCP_SUBSYSTEM_ERROR": 1,
                        "SUBPROTOCOL_TRIGGERED": 2,
                        "USELESS_PEER": 3
                    }
                },
                "peer_count_current": 2,
                "connected_total": 10
            }
        }
    }
    ```

### debug_traceTransaction

[Remix](https://remix.ethereum.org/) uses `debug_traceTransaction` to implement debugging. Use the _Debugger_ tab in Remix rather than calling `debug_traceTransaction` directly.  

Reruns the transaction with the same state as when the transaction was executed. 

**Parameters**

`transactionHash` : `data` - Transaction hash.

`Object` - request options (all optional and default to `false`):
* `disableStorage` : `boolean` - `true` disables storage capture. 
* `disableMemory` : `boolean` - `true` disables memory capture. 
* `disableStack` : `boolean` - `true` disables stack capture. 

**Returns**

`result`:`object` - [Trace object](JSON-RPC-API-Objects.md#trace-object). 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"debug_traceTransaction","params":["0x2cc6c94c21685b7e0f8ddabf277a5ccf98db157c62619cde8baea696a74ed18e",{"disableStorage":true}],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_traceTransaction","params":["0x2cc6c94c21685b7e0f8ddabf277a5ccf98db157c62619cde8baea696a74ed18e",{"disableStorage":true}],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : {
        "gas" : 21000,
        "failed" : false,
        "returnValue" : "",
        "structLogs" : [ {
          "pc" : 0,
          "op" : "STOP",
          "gas" : 0,
          "gasCost" : 0,
          "depth" : 1,
          "stack" : [ ],
          "memory" : [ ],
          "storage" : null
        } ]
      }
    }
    ```

## Miner Methods

### miner_start

Starts the CPU mining process. To start mining, a miner coinbase must have been previously specified using the [`--miner-coinbase`](../Reference/Pantheon-CLI-Syntax.md#miner-coinbase) command line option.  

**Parameters**

None

**Returns**

`result` :  `boolean` - `true` if the mining start request was received successfully; otherwise returns an error. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"miner_start","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"miner_start","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": true
    }
    ```

### miner_stop

Stops the CPU mining process on the client.

**Parameters**

None

**Returns**

`result` :  `boolean` - `true` if the mining stop request was received successfully; otherwise returns an error. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"miner_stop","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"miner_stop","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": true
    }
    ```

## ibft Methods

:construction: IBFT is not currently supported. Support for IBFT is in active development. 