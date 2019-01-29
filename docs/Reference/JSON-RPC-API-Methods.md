description: Pantheon JSON-RPC API methods reference
<!--- END of page meta data -->

# JSON-RPC API Methods

!!! danger "Breaking Change in v0.8.3"
    From v0.8.3, incoming HTTP requests are only accepted from hostnames specified using the [`--host-whitelist`](Using-JSON-RPC-API.md) option. 

The following lists the Pantheon JSON-RPC API commands:

## Admin Methods

!!! note
    The `ADMIN` API methods are not enabled by default. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `ADMIN` API methods.

### admin_peers

Returns networking information about connected remote nodes. 

**Parameters**

None

**Returns**

`result` : *array* of *objects* - Object returned for each remote node. 

Properties of the remote node object are:

* `version` - P2P protocol version
* `name` - Client name
* `caps` - List of Ethereum sub-protocol capabilities 
* `network` - Addresses of local node and remote node
* `port` - Port on the remote node on which P2P peer discovery is listening
* `id` - Node public key. Excluding the `0x` prefix, the node public key is the ID in the enode URL `enode://<id ex 0x>@<host>:<port>`. 

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
          "version": "0x5",
          "name": "Parity-Ethereum/v2.3.0-nightly-1c2e121-20181116/x86_64-linux-gnu/rustc1.30.0",
          "caps": [
             "eth/62",
             "eth/63",
             "par/1",
             "par/2",
             "par/3",
             "pip/1"
          ],
           "network": {
              "localAddress": "192.168.1.229:50115",
              "remoteAddress": "168.61.153.255:40303"
           },
           "port": "0x9d6f",
           "id": "0xea26ccaf0867771ba1fec32b3589c0169910cb4917017dba940efbef1d2515ce864f93a9abc846696ebad40c81de7c74d7b2b46794a71de8f95a0d019f494ff3"
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
    $ bin/pantheon --miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73" --rpc-http-enabled
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

### eth_hashrate

Returns the number of hashes per second with which the node is mining. 

**Parameters**

None

**Returns**

`result` : `quantity` - Number of hashes per second

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_hashrate","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_hashrate","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x12b"
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
    This method returns an empty object because Pantheon [does not support account management](Using-JSON-RPC-API.md#account-management).

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

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

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

Sends a [signed transaction](../Using-Pantheon/Transactions.md). A transaction can send ether, deploy a contract, or interact with a contract.  

You can interact with contracts using [eth_sendRawTransaction or eth_call](../Using-Pantheon/Transactions.md#eth_call-or-eth_sendrawtransaction).

To avoid exposing your private key, create signed transactions offline and send the signed transaction data using `eth_sendRawTransaction`. 

!!!important
    Pantheon does not implement [eth_sendTransaction](Using-JSON-RPC-API.md#account-management).

**Parameters**

`data` -  Signed transaction serialized to hexadecimal format. For example:

`params: ["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"]`

!!! note
    [Creating and Sending Transactions](../Using-Pantheon/Transactions.md) includes examples of creating signed transactions using the [web3.js](https://github.com/ethereum/web3.js/) library.

**Returns**

`result` : `data` - 32-byte transaction hash

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"],"id":1}
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

*QUANTITY|TAG* - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter).

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

Returns an estimate of how much gas is needed for a transaction to complete. The estimation process does not use
gas and the transaction is not added to the blockchain. The resulting estimate can be greater than the amount of
gas that the transaction actually uses, for various reasons including EVM mechanics and node performance.

The `eth_estimateGas` call does not send a transaction. You must make a subsequent call to
[eth_sendRawTransaction](#eth_sendrawtransaction) to execute the transaction.

**Parameters**

The transaction call object parameters are the same as those for [eth_call](#eth_call), except that in `eth_estimateGas`,
all fields are optional. Setting a gas limit is irrelevant to the estimation process (unlike transactions, in which gas
limits apply).

*OBJECT* - [Transaction call object](JSON-RPC-API-Objects.md#transaction-call-object).

**Returns**

`result` : `quantity` -  Amount of gas used.

The following example returns an estimate of 21000 wei (0x5208) for the transaction.

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73","to":"0x44Aa93095D6749A706051658B970b941c72c1D53","value":"0x1"}],"id":53}' <JSON-RPC-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73","to":"0x44Aa93095D6749A706051658B970b941c72c1D53","value":"0x1"}],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x5208"
    }
    ```

The following example request estimates the cost of deploying a simple storage smart contract to the network. The data field
contains the hash of the compiled contract to be deployed. (You can obtain the compiled contract hash from your IDE;
for example, **Remix > Compile tab > details > WEB3DEPLOY**.) The result is 113355 wei.

**Returns**

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST \
        http://localhost:8545 \
        -H 'Content-Type: application/json' \
        -d '{
          "jsonrpc": "2.0",
          "method": "eth_estimateGas",
          "params": [{
            "from": "0x8bad598904ec5d93d07e204a366d084a80c7694e",
            "data": "0x608060405234801561001057600080fd5b5060e38061001f6000396000f3fe6080604052600436106043576000357c0100000000000000000000000000000000000000000000000000000000900480633fa4f24514604857806355241077146070575b600080fd5b348015605357600080fd5b50605a60a7565b6040518082815260200191505060405180910390f35b348015607b57600080fd5b5060a560048036036020811015609057600080fd5b810190808035906020019092919050505060ad565b005b60005481565b806000819055505056fea165627a7a7230582020d7ad478b98b85ca751c924ef66bcebbbd8072b93031073ef35270a4c42f0080029"
          }],
          "id": 1
        }'
    ```

!!! example
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x1bacb"
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

Creates a [log filter](../Using-Pantheon/Events-and-Logs.md). To poll for logs associated with the created filter, use [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

`Object` - [Filter options object](JSON-RPC-API-Objects.md#filter-options-object). 

!!!note
    `fromBlock` and `toBlock` in the filter options object default to `latest`. To obtain logs using `eth_getFilterLogs`, set `fromBlock` and `toBlock` appropriately.
    
**Returns**

`data` - Filter ID hash

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newFilter","params":[{"fromBlock":"earliest", "toBlock":"latest", "topics":[]}],"id":1}' <JSON-RPC-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newFilter","params":[{"fromBlock":"earliest", "toBlock":"latest", "topics":[]}],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x1ddf0c00989044e9b41cc0ae40272df3"
    }
    ```
    
### eth_newBlockFilter

Creates a filter to retrieve new block hashes. To poll for new blocks, use [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

None

**Returns**

`data` - Filter ID hash

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newBlockFilter","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newBlockFilter","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x9d78b6780f844228b96ecc65a320a825"
    }
    ```

### eth_newPendingTransactionFilter

Creates a filter to retrieve new pending transactions hashes. To poll for new pending transactions, use [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

None

**Returns**

`data` - Filter ID hash

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newPendingTransactionFilter","params":[],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_newPendingTransactionFilter","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x443d6a77c4964707a8554c92f7e4debd"
    }
    ```

### eth_uninstallFilter

Uninstalls a filter with the specified ID. When a filter is no longer required, call this method.

Filters time out when not requested by [eth_getFilterChanges](#eth_getfilterchanges) for 10 minutes.

**Parameters**

`data` - Filter ID hash

**Returns**

`Boolean` - `true` if the filter was successfully uninstalled; otherwise `false`.

!!! example
    The following request deletes the block filter with an ID of 0x4:

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_uninstallFilter","params":["0x70355a0b574b437eaa19fe95adfedc0a"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_uninstallFilter","params":["0x70355a0b574b437eaa19fe95adfedc0a"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : true
    }
    ```

### eth_getFilterChanges

Polls the specified filter and returns an array of changes that have occurred since the last poll.

**Parameters**

`data` - Filter ID hash

**Returns**

`result` : `Array of Object` - If nothing changed since the last poll, an empty list. Otherwise:

* For filters created with `eth_newBlockFilter`, returns block hashes.
* For filters created with `eth_newPendingTransactionFilter`, returns transaction hashes.
* For filters created with `eth_newFilter`, returns [log objects](JSON-RPC-API-Objects.md#log-object). 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterChanges","params":["0xf8bf5598d9e04fbe84523d42640b9b0e"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getFilterChanges","params":["0xf8bf5598d9e04fbe84523d42640b9b0e"],"id":1}
    ```
    
    ```json tab="JSON result"
    
    Example result from a filter created with `eth_newBlockFilter`:
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "0xda2bfe44bf85394f0d6aa702b5af89ae50ae22c0928c18b8903d9269abe17e0b",
            "0x88cd3a37306db1306f01f7a0e5b25a9df52719ad2f87b0f88ee0e6753ed4a812",
            "0x4d4c731fe129ff32b425e6060d433d3fde278b565bbd1fd624d5a804a34f8786"
        ]
    }
    
    Example result from a filter created with `eth_newPendingTransactionFilter`:
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "0x1e977049b6db09362da09491bee3949d9362080ce3f4fc19721196d508580d46",
            "0xa3abc4b9a4e497fd58dc59cdff52e9bb5609136bcd499e760798aa92802769be"
        ]
    }
    
    Example result from a filter created with `eth_newFilter`:
    
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "logIndex": "0x0",
                "removed": false,
                "blockNumber": "0x233",
                "blockHash": "0xfc139f5e2edee9e9c888d8df9a2d2226133a9bd87c88ccbd9c930d3d4c9f9ef5",
                "transactionHash": "0x66e7a140c8fa27fe98fde923defea7562c3ca2d6bb89798aabec65782c08f63d",
                "transactionIndex": "0x0",
                "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
                "data": "0x0000000000000000000000000000000000000000000000000000000000000004",
                "topics": [
                    "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3"
                ]
            },
            {
                "logIndex": "0x0",
                "removed": false,
                "blockNumber": "0x238",
                "blockHash": "0x98b0ec0f9fea0018a644959accbe69cd046a8582e89402e1ab0ada91cad644ed",
                "transactionHash": "0xdb17aa1c2ce609132f599155d384c0bc5334c988a6c368056d7e167e23eee058",
                "transactionIndex": "0x0",
                "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
                "data": "0x0000000000000000000000000000000000000000000000000000000000000007",
                "topics": [
                    "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3"
                ]
            }
        ]
    }

    ```
    
    

### eth_getFilterLogs

Returns an array of [logs](../Using-Pantheon/Events-and-Logs.md) for the specified filter.

!!!note
     `eth_getFilterLogs` is only used for filters created with `eth_newFilter`. 
      
      You can use `eth_getLogs` to specify a filter object and get logs without creating a filter.

**Parameters**

`data` - Filter ID hash

**Returns**

`array` - [Log objects](JSON-RPC-API-Objects.md#log-object)

!!! example

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterLogs","params":["0x5ace5de3985749b6a1b2b0d3f3e1fb69"],"id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getFilterLogs","params":["0x5ace5de3985749b6a1b2b0d3f3e1fb69"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0xb3",
        "blockHash" : "0xe7cd776bfee2fad031d9cc1c463ef947654a031750b56fed3d5732bee9c61998",
        "transactionHash" : "0xff36c03c0fba8ac4204e4b975a6632c862a3f08aa01b004f570cc59679ed4689",
        "transactionIndex" : "0x0",
        "address" : "0x2e1f232a9439c3d459fceca0beef13acc8259dd8",
        "data" : "0x0000000000000000000000000000000000000000000000000000000000000003",
        "topics" : [ "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3" ]
      }, {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0xb6",
        "blockHash" : "0x3f4cf35e7ed2667b0ef458cf9e0acd00269a4bc394bb78ee07733d7d7dc87afc",
        "transactionHash" : "0x117a31d0dbcd3e2b9180c40aca476586a648bc400aa2f6039afdd0feab474399",
        "transactionIndex" : "0x0",
        "address" : "0x2e1f232a9439c3d459fceca0beef13acc8259dd8",
        "data" : "0x0000000000000000000000000000000000000000000000000000000000000005",
        "topics" : [ "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3" ]
      } ]
    }
    ```

### eth_getLogs

Returns an array of [logs](../Using-Pantheon/Events-and-Logs.md) matching a specified filter object.

**Parameters**

`Object` - [Filter options object](JSON-RPC-API-Objects.md#filter-options-object)

**Returns**

`array` - [Log objects](JSON-RPC-API-Objects.md#log-object)

!!! example
    The following request returns all logs for the contract at address `0x2e1f232a9439c3d459fceca0beef13acc8259dd8`. 

    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getLogs","params":[{"fromBlock":"earliest", "toBlock":"latest", "address": "0x2e1f232a9439c3d459fceca0beef13acc8259dd8", "topics":[]}], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eth_getLogs","params":[{"fromBlock":"earliest", "toBlock":"latest", "address": "0x2e1f232a9439c3d459fceca0beef13acc8259dd8", "topics":[]}], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : [ {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0xb3",
        "blockHash" : "0xe7cd776bfee2fad031d9cc1c463ef947654a031750b56fed3d5732bee9c61998",
        "transactionHash" : "0xff36c03c0fba8ac4204e4b975a6632c862a3f08aa01b004f570cc59679ed4689",
        "transactionIndex" : "0x0",
        "address" : "0x2e1f232a9439c3d459fceca0beef13acc8259dd8",
        "data" : "0x0000000000000000000000000000000000000000000000000000000000000003",
        "topics" : [ "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3" ]
      }, {
        "logIndex" : "0x0",
        "removed" : false,
        "blockNumber" : "0xb6",
        "blockHash" : "0x3f4cf35e7ed2667b0ef458cf9e0acd00269a4bc394bb78ee07733d7d7dc87afc",
        "transactionHash" : "0x117a31d0dbcd3e2b9180c40aca476586a648bc400aa2f6039afdd0feab474399",
        "transactionIndex" : "0x0",
        "address" : "0x2e1f232a9439c3d459fceca0beef13acc8259dd8",
        "data" : "0x0000000000000000000000000000000000000000000000000000000000000005",
        "topics" : [ "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3" ]
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

!!! note
    The `CLIQUE` API methods are not enabled by default. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `CLIQUE` API methods.

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

!!! note
    The `DEBUG` API methods are not enabled by default. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `DEBUG` API methods.

### debug_metrics

!!!note
    This method is only available only from v0.8.3.

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

!!! note
    The `MINER` API methods are not enabled by default. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `MINER` API methods.

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

## IBFT 2.0 Methods 

!!! note 
    IBFT 2.0 is under development and will be available in v1.0. 

!!! note
    The `IBFT` API methods are not enabled by default. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `IBFT` API methods.

### ibft_discardValidatorVote

Discards a proposal to [add or remove a validator](../Consensus-Protocols/IBFT.md#adding-and-removing-validators) with the specified address. 

**Parameters** 

`data` - 20-byte address of proposed validator 

**Returns** 

`result: boolean` - `true`

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_discardValidatorVote","params":["0xef1bfb6a12794615c9b0b5a21e6741f01e570185"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"ibft_discardValidatorVote","params":["0xef1bfb6a12794615c9b0b5a21e6741f01e570185"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : true
    }
    ```

### ibft_getPendingVotes

Returns [current votes](../Consensus-Protocols/IBFT.md#adding-and-removing-validators). 

**Parameters**

None

**Returns** 

`result`: `object` - Map of account addresses to corresponding boolean values indicating the vote for each account. 

If the boolean value is `true`, the vote is to add a validator. If `false`, the proposal is to remove a validator. 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getPendingVotes","params":[], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"ibft_getPendingVotes","params":[], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "0xef1bfb6a12794615c9b0b5a21e6741f01e570185": true,
            "0x42d4287eac8078828cf5f3486cfe601a275a49a5": true
        }
    }
    ```
    
### ibft_getValidatorsByBlockHash

Lists the validators defined in the specified block.

**Parameters**

`data` - 32-byte block hash 

**Returns** 

`result: array of data` - List of validator addresses

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockHash","params":["0xbae7d3feafd743343b9a4c578cab5e5d65eb735f6855fb845c00cab356331256"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockHash","params":["0xbae7d3feafd743343b9a4c578cab5e5d65eb735f6855fb845c00cab356331256"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "0x42d4287eac8078828cf5f3486cfe601a275a49a5",
            "0xb1b2bc9582d2901afdc579f528a35ca41403fa85",
            "0xef1bfb6a12794615c9b0b5a21e6741f01e570185"
        ]
    }
    ```

### ibft_getValidatorsByBlockNumber

Lists the validators defined in the specified block. 

**Parameters** 

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](Using-JSON-RPC-API.md#block-parameter). 

**Returns**

`result: array of data` - List of validator addresses 

!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockNumber","params":["latest"], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockNumber","params":["latest"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "0x42d4287eac8078828cf5f3486cfe601a275a49a5",
            "0xb1b2bc9582d2901afdc579f528a35ca41403fa85",
            "0xef1bfb6a12794615c9b0b5a21e6741f01e570185"
        ]
    }
    ```
    
### ibft_proposeValidatorVote

Proposes [adding or removing a validator](../Consensus-Protocols/IBFT.md#adding-and-removing-validators)) with the specified address. 

**Parameters**

`data` - Account address
 
`boolean` -  `true` to propose adding validator or `false` to propose removing validator. 

**Returns** 

`result: boolean` - `true`
   
!!! example
    ```bash tab="curl HTTP request"
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_proposeValidatorVote","params":["42d4287eac8078828cf5f3486cfe601a275a49a5",true], "id":1}' <JSON-RPC-http-endpoint:port>
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"ibft_proposeValidatorVote","params":["42d4287eac8078828cf5f3486cfe601a275a49a5",true], "id":1}
    ```
    
    ```json tab="JSON result"
    {
     "jsonrpc" : "2.0",
     "id" : 1,
     "result" : true
    }
    ```

## Permissioning Methods

Permissioning is under development and will be available in v1.0. 