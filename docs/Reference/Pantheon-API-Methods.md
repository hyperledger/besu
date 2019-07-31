description: Pantheon JSON-RPC API methods reference
<!--- END of page meta data -->

# Pantheon API Methods

!!! attention
    All JSON-RPC HTTP examples use the default host and port endpoint `http://127.0.0.1:8545`. 

    If using the [--rpc-http-host](../Reference/Pantheon-CLI-Syntax.md#rpc-http-host) or [--rpc-http-port](../Reference/Pantheon-CLI-Syntax.md#rpc-http-port)
    options, update the endpoint.  

## Admin Methods

!!! note
    The `ADMIN` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `ADMIN` API methods.

### admin_addPeer

Adds a [static node](../Configuring-Pantheon/Networking/Managing-Peers.md#static-nodes).  

!!! caution 
    If connections are timing out, ensure the node ID in the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) is correct. 

**Parameters**

`string` : [Enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) of peer to add

**Returns**

`result` : `boolean` - `true` if peer added or `false` if peer already a [static node](../Configuring-Pantheon/Networking/Managing-Peers.md#static-nodes). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"admin_addPeer","params":["enode://f59c0ab603377b6ec88b89d5bb41b98fc385030ab1e4b03752db6f7dab364559d92c757c13116ae6408d2d33f0138e7812eb8b696b2a22fe3332c4b5127b22a3@127.0.0.1:30304"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"admin_addPeer","params":["enode://f59c0ab603377b6ec88b89d5bb41b98fc385030ab1e4b03752db6f7dab364559d92c757c13116ae6408d2d33f0138e7812eb8b696b2a22fe3332c4b5127b22a3@127.0.0.1:30304"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": true
    }
    ```
    
### admin_changeLogLevel

Change the log level without restarting Pantheon. 

**Parameters**

`level` - [Log level](Pantheon-CLI-Syntax.md#logging)

**Returns**

`result` : `Success` if the log level has changed; otherwise `error`. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"admin_changeLogLevel","params":["DEBUG"], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"admin_changeLogLevel","params":["DEBUG"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
     "jsonrpc": "2.0",
     "id": 1,
     "result": "Success"
    }
    ```

### admin_nodeInfo

Returns networking information about the node. The information includes general information about the node
and specific information from each running Ethereum sub-protocol (for example, `eth`). 

**Parameters**

None

**Returns**

`result` : Node object 

Properties of the node object are:

* `enode` - [Enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) for the node  
* `listenAddr` - Host and port for the node
* `name` - Client name
* `id` - [Node public key](../Configuring-Pantheon/Node-Keys.md#node-public-key)
* `ports` - Peer discovery and listening [ports](../Configuring-Pantheon/Networking/Managing-Peers.md#port-configuration) 
* `protocols` - List of objects containing information for each Ethereum sub-protocol 

!!! note
    If the node is running locally, the host of the `enode` and `listenAddr` are displayed as `[::]` in the result. 
    If [UPnP](../Configuring-Pantheon/Networking/Using-UPnP.md) is enabled, the external address is 
    displayed for the `enode` and `listenAddr`. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"admin_nodeInfo","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"admin_nodeInfo","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "enode": "enode://87ec35d558352cc55cd1bf6a472557797f91287b78fe5e86760219124563450ad1bb807e4cc61e86c574189a851733227155551a14b9d0e1f62c5e11332a18a3@[::]:30303",
            "listenAddr": "[::]:30303",
            "name": "pantheon/v1.0.1-dev-0d2294a5/osx-x86_64/oracle-java-1.8",
            "id": "87ec35d558352cc55cd1bf6a472557797f91287b78fe5e86760219124563450ad1bb807e4cc61e86c574189a851733227155551a14b9d0e1f62c5e11332a18a3",
            "ports": {
                "discovery": 30303,
                "listener": 30303
            },
            "protocols": {
                "eth": {
                    "config": {
                        "chainId": 2018,
                        "homesteadBlock": 0,
                        "daoForkBlock": 0,
                        "daoForkSupport": true,
                        "eip150Block": 0,
                        "eip155Block": 0,
                        "eip158Block": 0,
                        "byzantiumBlock": 0,
                        "constantinopleBlock": 0,
                        "constantinopleFixBlock": 0,
                        "ethash": {
                            "fixeddifficulty": 100
                        }
                    },
                    "difficulty": 78536,
                    "genesis": "0x43ee12d45470e57c86a0dfe008a5b847af9e372d05e8ba8f01434526eb2bea0f",
                    "head": "0xc6677651f16d07ae59cab3a5e1f0b814ed2ec27c00a93297b2aa2e29707844d9",
                    "network": 2018
                }
            }
        }
    }
    ```

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
* `network` - Local and remote addresses established at time of bonding with the peer. The remote address may not 
match the hex value for `port`. The remote address depends on which node initiated the connection. 
* `port` - Port on the remote node on which P2P peer discovery is listening
* `id` - Node public key. Excluding the `0x` prefix, the node public key is the ID in the enode URL `enode://<id ex 0x>@<host>:<port>`. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"admin_peers","params":[],"id":1}' http://127.0.0.1:8545
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

### admin_removePeer

Removes a [static node](../Configuring-Pantheon/Networking/Managing-Peers.md#static-nodes).  

**Parameters**

`string` : [Enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) of peer to remove

**Returns**

`result` : `boolean` - `true` if peer removed or `false` if peer not a [static node](../Configuring-Pantheon/Networking/Managing-Peers.md#static-nodes)). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"admin_removePeer","params":["enode://f59c0ab603377b6ec88b89d5bb41b98fc385030ab1e4b03752db6f7dab364559d92c757c13116ae6408d2d33f0138e7812eb8b696b2a22fe3332c4b5127b22a3@127.0.0.1:30304"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"admin_removePeer","params":["enode://f59c0ab603377b6ec88b89d5bb41b98fc385030ab1e4b03752db6f7dab364559d92c757c13116ae6408d2d33f0138e7812eb8b696b2a22fe3332c4b5127b22a3@127.0.0.1:30304"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": true
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "pantheon/{{ versions.pantheon_stable }}"
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"web3_sha3","params":["0x68656c6c6f20776f726c00"],"id":53}' http://127.0.0.1:8545
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
- `5` - Goerli Testnet
- `42` - Kovan Testnet

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"net_version","params":[],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_version","params":[],"id":53}
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"net_listening","params":[],"id":53}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"net_peerCount","params":[],"id":53}' http://127.0.0.1:8545
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

### net_enode

Returns the [enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url).

**Parameters**

None

**Returns**

`result` : *string* - [Enode URL](../Configuring-Pantheon/Node-Keys.md#enode-url) for the node

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"net_enode","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_enode","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : "enode://6a63160d0ccef5e4986d270937c6c8d60a9a4d3b25471cda960900d037c61988ea14da67f69dbfb3497c465d0de1f001bb95598f74b68a39a5156a608c42fa1b@127.0.0.1:30303"
    }
    ```
    
### net_services

Returns enabled services (for example, `jsonrpc`) and the host and port for each service.

**Parameters**

None

**Returns**

`result` : *objects* - Enabled services 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"net_services","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"net_services","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "jsonrpc": {
                "host": "127.0.0.1",
                "port": "8545"
            },
            "p2p" : {
                "host" : "127.0.0.1",
                "port" : "30303"
            },
            "metrics" : {
                "host": "127.0.0.1",
                "port": "9545"
            }
        }
    }
    ```
	
## Eth Methods

!!! note
    Methods with an equivalent [GraphQL](../Pantheon-API/GraphQL.md) query include a GraphQL request and result in the method example.
    The parameter and result descriptions apply to the JSON-RPC requests. The GraphQL specification is defined in the [schema](https://github.com/PegaSysEng/pantheon/blob/master/ethereum/graphql/src/main/resources/schema.graphqls).  

### eth_syncing

Returns an object with data about the synchronization status, or `false` if not synchronizing.

**Parameters**

None

**Returns**

`result` : *Object|Boolean* - Object with synchronization status data or `false`, when not synchronizing:

* `startingBlock` : *quantity* - Index of the highest block on the blockchain when the network synchronization starts.

* `currentBlock` : *quantity* - Index of the latest block (also known as the best block) for the current node. This is the same index that [eth_blockNumber](#eth_blocknumber) returns.

* `highestBlock`: *quantity* - Index of the highest known block in the peer network (that is, the highest block so far discovered among peer nodes). This is the same value as `currentBlock` if the current node has no peers.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_syncing","params":[],"id":51}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{syncing{startingBlock currentBlock highestBlock}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      syncing {
        startingBlock
        currentBlock
        highestBlock
      }
    }
    ```
    
    ```json tab="GraphQL result"
    {
      "data" : {
        "syncing" : {
          "startingBlock" : 1592,
          "currentBlock" : 31988,
          "highestBlock" : 4389714
        }
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":51}' http://127.0.0.1:8545
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

### eth_protocolVersion

Returns current Ethereum protocol version. 

**Parameters**

None

**Returns**

`result` : *quantity* - Ethereum protocol version 

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_protocolVersion","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_protocolVersion","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": "0x3f"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{protocolVersion}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      protocolVersion
    }
    ```
        
    ```json tab="GraphQL result"
    {
      "data" : {
        "protocolVersion" : 63
      }
    }
    ```

### eth_coinbase

Returns the client coinbase address. The coinbase address is the account to which mining rewards are paid. 

To set a coinbase address, start Pantheon with the `--miner-coinbase` option set to a valid Ethereum account address.
You can get the Ethereum account address from a client such as MetaMask or Etherscan. For example:

!!!example
    ```bash
    pantheon --miner-coinbase="0xfe3b557e8fb62b89f4916b721be55ceb828dbd73" --rpc-http-enabled
    ```

**Parameters**

None

**Returns**

`result` : *data* - Coinbase address.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_coinbase","params":[],"id":53}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_mining","params":[],"id":53}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_hashrate","params":[],"id":1}' http://127.0.0.1:8545
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
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_gasPrice","params":[],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x3e8"
    }
    ```

    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{gasPrice}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      gasPrice
    }
    ```
    
    ```json tab="GraphQL result"
    {
      "data" : {
        "gasPrice" : "0x3e8"
      }
    }
    ```   

### eth_accounts

Returns a list of account addresses that the client owns.

!!!note
    This method returns an empty object because Pantheon [doesn't support key management](../Pantheon-API/Using-JSON-RPC-API.md#account-management)
    inside the client.
    
    Use [EthSigner](http://docs.ethsigner.pegasys.tech/en/latest/) with Pantheon to provide access to your key store and sign transactions.

**Parameters**

None

**Returns**

`Array of data` : List of 20-byte account addresses owned by the client.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_accounts","params":[],"id":53}' http://127.0.0.1:8545
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
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":51}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "0x2377"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block{number}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block {
        number
      }
    }
    ```
    
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "number" : 16221
        }
      }
    }
    ```

### eth_getBalance

Returns the account balance of the specified address.

**Parameters**

`DATA` - 20-byte account address from which to retrieve the balance.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer value of the current balance in wei.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBalance","params":["0xdd37f65db31c107f773e82a4f85c693058fef7a9", "latest"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getBalance","params":["0xdd37f65db31c107f773e82a4f85c693058fef7a9", "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x0"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{ account ( address: \"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\") { balance } }"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      account(address: "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73") {
        balance
      }
    }
    ```
    
    ```bash tab="GraphQL result"
    {
      "data": {
        "account": {
          "balance": "0xac70d23585eadfc2e"
        }
      }
    }    
    ```
    
### eth_getStorageAt

Returns the value of a storage position at a specified address.

**Parameters**

`DATA` - A 20-byte storage address.

`QUANTITY` - Integer index of the storage position.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *DATA* - The value at the specified storage position.

!!! example
    Calculating the correct position depends on the storage you want to retrieve.

    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method": "eth_getStorageAt","params": ["0x‭3B3F3E‬","0x0","latest"],"id": 53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method": "eth_getStorageAt","params": ["0x‭3B3F3E‬","0x0","latest"],"id": 53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x0000000000000000000000000000000000000000000000000000000000000000"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{account(address: \"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\") {storage(slot: \"0x04\")}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      account(address: "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73") {
        storage(slot: "0x04")
      }
    }
    ```
        
    ```bash tab="GraphQL result"
    {
      "data" : {
        "account" : {
          "storage" : "0x0000000000000000000000000000000000000000000000000000000000000000"
        }
      }
    }   
    ```

### eth_getTransactionCount

Returns the number of transactions sent from a specified address. Use the `pending` tag to get the account nonce.

**Parameters**

`data` - 20-byte account address.

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *quantity* - Integer representing the number of transactions sent from the specified address.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionCount","params":["0xc94770007dda54cF92009BFF0dE90c06F603a09f","latest"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getTransactionCount","params":["0xc94770007dda54cF92009BFF0dE90c06F603a09f","latest"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : "0x1"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{ account (address:\"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\"){transactionCount}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      account(address: "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73") {
        transactionCount
      }
    }
    ```
            
    ```bash tab="GraphQL result"
    {
      "data" : {
        "account" : {
          "transactionCount" : 5
        }
      }
    }
    ```
    
### eth_getBlockTransactionCountByHash

Returns the number of transactions in the block matching the given block hash.

**Parameters**

`data` - 32-byte block hash.

**Returns**

`result` : `quantity` - Integer representing the number of transactions in the specified block.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : null
    }
    ```

    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(hash:\"0xe455c14f757b0b9b67774baad1be1c180a4c1657df52259dbb685bf375408097\"){transactionCount}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(hash: "0xe455c14f757b0b9b67774baad1be1c180a4c1657df52259dbb685bf375408097") {
        transactionCount
      }
    }
    ```
                
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "transactionCount" : 1
        }
      }
    }
    ```

### eth_getBlockTransactionCountByNumber

Returns the number of transactions in a block matching the specified block number.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer representing the number of transactions in the specified block.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByNumber","params":["0xe8"],"id":51}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getBlockTransactionCountByNumber","params":["0xe8"],"id":51}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 51,
      "result" : "0x8"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(number:232){transactionCount}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(number: 232) {
        transactionCount
      }
    }
    ```
                    
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "transactionCount" : 1
        }
      }
    }
    ```


### eth_getUncleByBlockHashAndIndex

Returns uncle specified by block hash and index. 

**Parameters**

`data` - 32-byte block hash.

`quantity` - Index of the uncle.

**Returns**

`result` : [Block object](Pantheon-API-Objects.md#block-object)

!!! note
    Uncles do not contain individual transactions. 

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleByBlockHashAndIndex","params":["0xc48fb64230a82f65a08e7280bd8745e7fea87bc7c206309dee32209fe9a985f7", "0x0"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getUncleByBlockHashAndIndex","params":["0xc48fb64230a82f65a08e7280bd8745e7fea87bc7c206309dee32209fe9a985f7", "0x0"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc":"2.0",
      "id":1,
      "result":{
        "difficulty":"0x76b123df93230",
        "extraData":"0x50505945206e616e6f706f6f6c2e6f7267",
        "gasLimit":"0x7a121d",
        "gasUsed":"0x7a0175",
        "hash":"0xc20189c0b1a4a23116ab3b177e929137f6e826f17fc4c2e880e7258c620e9817",
        "logsBloom":"0x890086c024487ca422be846a201a10e41bc2882902312116c1119609482031e9c000e2a708004a10281024028020c505727a12570c4810121c59024490b040894406a1c23c37a0094810921da3923600c71c03044b40924280038d07ab91964a008084264a01641380798840805a284cce201a8026045451002500113a00de441001320805ca2840037000111640d090442c11116d2112948084240242340400236ce81502063401dcc214b9105194d050884721c1208800b20501a4201400276004142f118e60808284506979a86e050820101c170c185e2310005205a82a2100382422104182090184800c02489e033440218142140045801c024cc1818485",
        "miner":"0x52bc44d5378309ee2abf1539bf71de1b7d7be3b5",
        "mixHash":"0xf557cc827e058862aa3ea1bd6088fb8766f70c0eac4117c56cf85b7911f82a14",
        "nonce":"0xd320b48904347cdd",
        "number":"0x768964",
        "parentHash":"0x98d752708b3677df8f439c4529f999b94663d5494dbfc08909656db3c90f6255",
        "receiptsRoot":"0x0f838f0ceb73368e7fc8d713a7761e5be31e3b4beafe1a6875a7f275f82da45b",
        "sha3Uncles":"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
        "size":"0x21a",
        "stateRoot":"0xa0c7d4fca79810c89c517eff8dadb9c6d6f4bcc27c2edfb301301e1cf7dec642",
        "timestamp":"0x5cdcbba6",
        "totalDifficulty":"0x229ad33cabd4c40d23d",
        "transactionsRoot":"0x866e38e91d01ef0387b8e07ccf35cd910224271ccf2b7477b8c8439e8b70f365",
        "uncles":[]
      }
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(hash:\"0xc48fb64230a82f65a08e7280bd8745e7fea87bc7c206309dee32209fe9a985f7\"){ ommerAt(index: 0) {difficulty extraData gasLimit gasUsed hash logsBloom mixHash nonce number receiptsRoot stateRoot timestamp totalDifficulty transactionsRoot}}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(hash: "0xc48fb64230a82f65a08e7280bd8745e7fea87bc7c206309dee32209fe9a985f7") {
        ommerAt(index: 0) {
          difficulty
          extraData
          gasLimit
          gasUsed
          hash
          logsBloom
          mixHash
          nonce
          number
          receiptsRoot
          stateRoot
          timestamp
          totalDifficulty
          transactionsRoot
        }
      }
    }
    ```
                        
    ```bash tab="GraphQL result"
    {
      "data": {
        "block": {
          "difficulty": "0x1",
          "extraData": "0xf882a00000000000000000000000000000000000000000000000000000000000000000d5949811ebc35d7b06b3fa8dc5809a1f9c52751e1deb808400000000f843b8418e98ef756acdae1e510b1df4b507b7af04eb3802db7fa0f3e73e7d0721b3645e76f4eb3d0dbf0de75620c4405bd5a663247cdd9616482c883053856d857f884a01",
          "gasLimit": 4700000,
          "gasUsed": 0,
          "hash": "0x0efe67972b982eb6be5df84e5238eb07475f86afa8a7de708f6a13ac0ff60d6c",
          "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
          "mixHash": "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
          "nonce": "0x0000000000000000",
          "number": 200,
          "receiptsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          "stateRoot": "0xd650578a04b39f50cc979155f4510ec28c2c0a7c1e5fdbf84609bc7b1c430f48",
          "timestamp": "0x5cd109fb",
          "totalDifficulty": "0xc9",
          "transactionsRoot": "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"
        }
      }
    }
    ```

### eth_getUncleByBlockNumberAndIndex

Returns uncle specified by block number and index. 

**Parameters**

`quantity|tag` - Index of the block, or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

`quantity` - Index of the uncle.

**Returns**

`result` : [Block object](Pantheon-API-Objects.md#block-object)

!!! note
    Uncles do not contain individual transactions. 

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleByBlockNumberAndIndex","params":["0x7689D2", "0x0"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getUncleByBlockNumberAndIndex","params":["0x7689D2", "0x0"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc":"2.0",
      "id":1,
      "result":{
        "difficulty":"0x77daec467bf93",
        "extraData":"0x50505945206e616e6f706f6f6c2e6f7267",
        "gasLimit":"0x7a121d",
        "gasUsed":"0x7a0f7b",
        "hash":"0x42d83ae9c0743f4b1f9c61ff7ea8b164c1bab3627decd49233760680be006ecf",
        "logsBloom":"0x888200800000340120220008640200500408006100038400100581c000080240080a0014e8002010080004088040004022402a000c18010001400100002a041141a0610a0052900600041018c0002a0003090020404c00206010010513d00020005380124e08050480710000000108401012b0901c1424006000083a10a8c1040100a0440081050210124400040044304070004001100000012600806008061d0320800000b40042160600002480000000800000c0002100200940801c000820800048024904710000400640490026000a44300309000286088010c2300060003011380006400200812009144042204810209020410a84000410520c08802941",
        "miner":"0x52bc44d5378309ee2abf1539bf71de1b7d7be3b5",
        "mixHash":"0xf977fcdb52868be410b75ef2becc35cc312f13ab0a6ce400ecd9d445f66fa3f2",
        "nonce":"0x628b28403bf1e3d3",
        "number":"0x7689d0",
        "parentHash":"0xb32cfdfbf4adb05d30f02fcc6fe039cc6666402142954051c1a1cb9cc91aa11e",
        "receiptsRoot":"0x9c7c8361d1a24ea2841432234c81974a9920d3eba2b2b1c496b5f925a95cb4ac",
        "sha3Uncles":"0x7d972aa1b182b7e93f1db043f03fbdbfac6874fe7e67e162141bcc0aefa6336b",
        "size":"0x21a",
        "stateRoot":"0x74e97b77813146344d75acb5a52a006cc6dfaca678a10fb8a484a8443e919272",
        "timestamp":"0x5cdcc0a7",
        "totalDifficulty":"0x229b0583b4bd2698ca0",
        "transactionsRoot":"0x1d21626afddf05e5866de66ca3fcd98f1caf5357eba0cc6ec675606e116a891b",
        "uncles":[]
      }
    }
    ```
    
     ```bash tab="curl GraphQL"
     curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(number:2587){ ommerAt(index: 0) {difficulty extraData gasLimit gasUsed hash logsBloom mixHash nonce number receiptsRoot stateRoot timestamp totalDifficulty transactionsRoot}}}"}' http://localhost:8547/graphql
     ```
     
     ```bash tab="GraphQL"
     {
       block(number: 2587) {
         ommerAt(index: 0) {
           difficulty
           extraData
           gasLimit
           gasUsed
           hash
           logsBloom
           mixHash
           nonce
           number
           receiptsRoot
           stateRoot
           timestamp
           totalDifficulty
           transactionsRoot
         }
       }
     }
     ```
                            
     ```bash tab="GraphQL result"
     {
       "data" : {
         "block" : {
           "ommerAt" : null
         }
       }
     }
     ```

### eth_getUncleCountByBlockHash

Returns the number of uncles in a block from a block matching the given block hash.

**Parameters**

`DATA` - 32-byte block hash.

**Returns**

`result` : *QUANTITY* - Integer representing the number of uncles in the specified block.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleCountByBlockHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getUncleCountByBlockHash","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : 0x0
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(hash:\"0x65c08d792e4192b9ece6b6f2390da7da464208b22d88490be8add9373917b426\"){ommerCount}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(hash: "0x65c08d792e4192b9ece6b6f2390da7da464208b22d88490be8add9373917b426") {
        ommerCount
      }
    }
    ```
                        
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "ommerCount" : 2
        }
      }
    }
    ```
    
### eth_getUncleCountByBlockNumber

Returns the number of uncles in a block matching the specified block number.

**Parameters**

`QUANTITY|TAG` - Integer representing either the 0-based index of the block within the blockchain, or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *QUANTITY* - Integer representing the number of uncles in the specified block.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getUncleCountByBlockNumber","params":["0xe8"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getUncleCountByBlockNumber","params":["0xe8"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 1,
      "result" : "0x1"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block(number:\"0x59fd\"){ommerCount}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(number: "0x59fd") {
        ommerCount
      }
    }
    ```
    
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "ommerCount" : 0
        }
      }
    }
    ```
    
### eth_getCode

Returns the code of the smart contract at the specified address. Compiled smart contract code is stored as a hexadecimal value. 

**Parameters**

`DATA` - 20-byte contract address.

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` : *DATA* - Code stored at the specified address.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getCode","params":["0xa50a51c09a5c451c52bb714527e1974b686d8e77", "latest"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_getCode","params":["0xa50a51c09a5c451c52bb714527e1974b686d8e77", "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 53,
        "result": "0x60806040526004361060485763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f2458114604d57806355241077146071575b600080fd5b348015605857600080fd5b50605f6088565b60408051918252519081900360200190f35b348015607c57600080fd5b506086600435608e565b005b60005481565b60008190556040805182815290517f199cd93e851e4c78c437891155e2112093f8f15394aa89dab09e38d6ca0727879181900360200190a1505600a165627a7a723058209d8929142720a69bde2ab3bfa2da6217674b984899b62753979743c0470a2ea70029"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{account(address: \"0xa50a51c09a5c451c52bb714527e1974b686d8e77\"){ code }}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      account(address: "0xa50a51c09a5c451c52bb714527e1974b686d8e77") {
        code
      }
    }
    ```
                        
    ```bash tab="GraphQL result"
    {
      "data" : {
        "account" : {
          "code" : "0x60806040526004361060485763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f2458114604d57806355241077146071575b600080fd5b348015605857600080fd5b50605f6088565b60408051918252519081900360200190f35b348015607c57600080fd5b506086600435608e565b005b60005481565b60008190556040805182815290517f199cd93e851e4c78c437891155e2112093f8f15394aa89dab09e38d6ca0727879181900360200190a1505600a165627a7a723058209d8929142720a69bde2ab3bfa2da6217674b984899b62753979743c0470a2ea70029"
        }
      }
    }
    ```
    
### eth_sendRawTransaction

Sends a [signed transaction](../Using-Pantheon/Transactions/Transactions.md). A transaction can send ether, deploy a contract, or interact with a contract.  

You can interact with contracts using [eth_sendRawTransaction or eth_call](../Using-Pantheon/Transactions/Transactions.md#eth_call-or-eth_sendrawtransaction).

To avoid exposing your private key, create signed transactions offline and send the signed transaction data using `eth_sendRawTransaction`. 

!!!important
    Pantheon does not implement [eth_sendTransaction](../Pantheon-API/Using-JSON-RPC-API.md#account-management). 
    
    [EthSigner](https://docs.ethsigner.pegasys.tech/en/latest/) provides transaction signing and implements [`eth_sendTransaction`](https://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner#eth_sendTransaction). 

**Parameters**

`data` -  Signed transaction serialized to hexadecimal format. For example:

`params: ["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"]`

!!! note
    [Creating and Sending Transactions](../Using-Pantheon/Transactions/Transactions.md) includes examples of creating signed transactions using the [web3.js](https://github.com/ethereum/web3.js/) library.

**Returns**

`result` : `data` - 32-byte transaction hash

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "id":1,
      "jsonrpc": "2.0",
      "result": "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1527331"
    }
    ```
    
     ```bash tab="curl GraphQL"
     curl -X POST -H "Content-Type: application/json" --data '{ "query": "mutation {sendRawTransaction(data: \"0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833\")}"}' http://localhost:8547/graphql
     ```
     
     ```bash tab="GraphQL"
     mutation {
       sendRawTransaction(data: "0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833")
     }
     ```
        
     ```json tab="GraphQL result"
     {
       "data" : {
         "sendRawTransaction" : "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1527331"
       }
     }
     ```

### eth_call

Invokes a contract function locally and does not change the state of the blockchain. 

You can interact with contracts using [eth_sendRawTransaction or eth_call](../Using-Pantheon/Transactions/Transactions.md#eth_call-or-eth_sendrawtransaction).

**Parameters**

*OBJECT* - [Transaction call object](Pantheon-API-Objects.md#transaction-call-object).

*QUANTITY|TAG* - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

**Returns**

`result` - `data` - Return value of the executed contract.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13","value":"0x1"}, "latest"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_call","params":[{"to":"0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13","value":"0x1"}, "latest"],"id":53}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 53,
        "result": "0x"
    }
    ```

    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block {number call (data : {from : \"0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b\", to: \"0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13\", data :\"0x12a7b914\"}){data status}}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block {
        number
        call(data: {from: "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b", to: "0x69498dd54bd25aa0c886cf1f8b8ae0856d55ff13", data: "0x12a7b914"}) {
          data
          status
        }
      }
    }
    ```
        
    ```json tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "number" : 17449,
          "call" : {
            "data" : "0x",
            "status" : 1
          }
        }
      }
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

*OBJECT* - [Transaction call object](Pantheon-API-Objects.md#transaction-call-object).

**Returns**

`result` : `quantity` -  Amount of gas used.

The following example returns an estimate of 21000 wei (0x5208) for the transaction.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73","to":"0x44Aa93095D6749A706051658B970b941c72c1D53","value":"0x1"}],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
    {"jsonrpc":"2.0","method":"eth_estimateGas","params":[{"from":"0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73","to":"0x44Aa93095D6749A706051658B970b941c72c1D53","value":"0x1"}],"id":53}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc" : "2.0",
      "id" : 53,
      "result" : "0x5208"
    }
    ```
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block{estimateGas (data: {from :\"0x6295ee1b4f6dd65047762f924ecd367c17eabf8f\", to :\"0x8888f1f195afa192cfee860698584c030f4c9db1\"})}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block {
        estimateGas(data: {from: "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f", to: "0x8888f1f195afa192cfee860698584c030f4c9db1"})
      }
    }
    ```
                
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "estimateGas" : 21000
        }
      }
    }   
    ```

The following example request estimates the cost of deploying a simple storage smart contract to the network. The data field
contains the hash of the compiled contract to be deployed. (You can obtain the compiled contract hash from your IDE;
for example, **Remix > Compile tab > details > WEB3DEPLOY**.) The result is 113355 wei.

**Returns**

!!! example
    ```bash tab="curl HTTP request"
     curl -X POST \
        http://127.0.0.1:8545 \
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

`Boolean` - If `true`, returns the full [transaction objects](Pantheon-API-Objects.md#transaction-object); if `false`, returns the transaction hashes.

**Returns**

`result` : *OBJECT* - [Block object](Pantheon-API-Objects.md#block-object) , or `null` when no block is found. 

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockByHash","params":["0x16b69965a5949262642cfb5e86368ddbbe57ab9f17d999174a65fd0e66580d8f", false],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block (hash : \"0xb0efed1fc9326fee967cb2d845d4ebe57c5350a0670c8e86f8052dea6f219f92\") {number transactions{hash} timestamp difficulty totalDifficulty gasUsed gasLimit hash nonce ommerCount logsBloom mixHash ommerHash extraData stateRoot receiptsRoot transactionCount transactionsRoot}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(hash: "0xb0efed1fc9326fee967cb2d845d4ebe57c5350a0670c8e86f8052dea6f219f92") {
        number
        transactions {
          hash
        }
        timestamp
        difficulty
        totalDifficulty
        gasUsed
        gasLimit
        hash
        nonce
        ommerCount
        logsBloom
        mixHash
        ommerHash
        extraData
        stateRoot
        receiptsRoot
        transactionCount
        transactionsRoot
      }
    }
    ```
    
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "number" : 17607,
          "transactions" : [ ],
          "timestamp" : "0x5cdbdfb5",
          "difficulty" : "0x1",
          "totalDifficulty" : "0x44c8",
          "gasUsed" : 0,
          "gasLimit" : 4700000,
          "hash" : "0xb0efed1fc9326fee967cb2d845d4ebe57c5350a0670c8e86f8052dea6f219f92",
          "nonce" : "0x0000000000000000",
          "ommerCount" : 0,
          "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
          "mixHash" : "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
          "ommerHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
          "extraData" : "0xf882a00000000000000000000000000000000000000000000000000000000000000000d5949811ebc35d7b06b3fa8dc5809a1f9c52751e1deb808400000000f843b841fae6d25da0b91e3e88669d0a765c98479d86d53e9ea1f3fb6b36d7ff22fa622a3da0c49c20e5562c774e90acae8ad487936f6b6019cd8a782db684693cba1e9800",
          "stateRoot" : "0xa7086c266aed46cd3bc45579178f8acb36d9d147de575a3ecbf8c7e6f1c737fc",
          "receiptsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          "transactionCount" : 0,
          "transactionsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"
        }
      }
    }
    ```

### eth_getBlockByNumber

Returns information about a block by block number.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

`Boolean` - If `true`, returns the full [transaction objects](Pantheon-API-Objects.md#transaction-object); if `false`, returns only the hashes of the transactions.

**Returns**

`result` : *OBJECT* - [Block object](Pantheon-API-Objects.md#block-object) , or `null` when no block is found. 

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["0x64", true],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{ "query": "{block (number : 100) {transactions{hash} timestamp difficulty totalDifficulty gasUsed gasLimit hash nonce ommerCount logsBloom mixHash ommerHash extraData stateRoot receiptsRoot transactionCount transactionsRoot ommers{hash} ommerAt(index : 1){hash} miner{address} account(address: \"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\"){balance} parent{hash} }}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(number: 100) {
        transactions {
          hash
        }
        timestamp
        difficulty
        totalDifficulty
        gasUsed
        gasLimit
        hash
        nonce
        ommerCount
        logsBloom
        mixHash
        ommerHash
        extraData
        stateRoot
        receiptsRoot
        transactionCount
        transactionsRoot
        ommers {
          hash
        }
        ommerAt(index: 1) {
          hash
        }
        miner {
          address
        }
        account(address: "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73") {
          balance
        }
        parent {
          hash
        }
      }
    }
    ```
        
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "transactions" : [ ],
          "timestamp" : "0x5cd10933",
          "difficulty" : "0x1",
          "totalDifficulty" : "0x65",
          "gasUsed" : 0,
          "gasLimit" : 4700000,
          "hash" : "0x63b3ea2bc37fec8f82680eb823652da6af8acebb4f6c4d0ff659c55be473c8b0",
          "nonce" : "0x0000000000000000",
          "ommerCount" : 0,
          "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
          "mixHash" : "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
          "ommerHash" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
          "extraData" : "0xf882a00000000000000000000000000000000000000000000000000000000000000000d5949811ebc35d7b06b3fa8dc5809a1f9c52751e1deb808400000000f843b8414d877d8d0ced37ea138fab55a978f3740367a24a31731322ecdc3368f11e0d4966c9ce17ae59a76fb94eb436e8a386868f6bd6b0a5678e58daf49f5dd940558b00",
          "stateRoot" : "0xd650578a04b39f50cc979155f4510ec28c2c0a7c1e5fdbf84609bc7b1c430f48",
          "receiptsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          "transactionCount" : 0,
          "transactionsRoot" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          "ommers" : [ ],
          "ommerAt" : null,
          "miner" : {
            "address" : "0x9811ebc35d7b06b3fa8dc5809a1f9c52751e1deb"
          },
          "account" : {
            "balance" : "0xad0f47f269cbf31ac"
          },
          "parent" : {
            "hash" : "0x7bca25e1fa5e395fd6029eb496a70b6b5495843976bf9e49b993c723ded29d9e"
          }
        }
      }
    }
    ```
    

### eth_getTransactionByHash

Returns transaction information for the specified transaction hash.

**Parameters**

`DATA` - 32-byte transaction hash.

**Returns**

Object - [Transaction object](Pantheon-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByHash","params":["0xa52be92809541220ee0aaaede6047d9a6c5d0cd96a517c854d944ee70a0ebb44"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{transaction(hash : \"0x03d80b9ca0a71435399a268609d6d7896f7155d2147cc22b780672bcb59b170d\") { block{hash} gas gasPrice hash nonce value from {address} to {address} status}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      transaction(hash: "0x03d80b9ca0a71435399a268609d6d7896f7155d2147cc22b780672bcb59b170d") {
        block {
          hash
        }
        gas
        gasPrice
        hash
        nonce
        value
        from {
          address
        }
        to {
          address
        }
        status
      }
    }
    ```
            
    ```bash tab="GraphQL result"
    {
      "data" : {
        "transaction" : {
          "block" : {
            "hash" : "0xb1ef35744bade6980c3a933024b2557a8c724a19e5fdd2116bac712aa5e57198"
          },
          "gas" : 21000,
          "gasPrice" : "0x2540be400",
          "hash" : "0x03d80b9ca0a71435399a268609d6d7896f7155d2147cc22b780672bcb59b170d",
          "nonce" : 6,
          "value" : "0x8ac7230489e80000",
          "from" : {
            "address" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
          },
          "to" : {
            "address" : "0x9d8f8572f345e1ae53db1dfa4a7fce49b467bd7f"
          },
          "status" : 1
        }
      }
    }
    ```

### eth_getTransactionByBlockHashAndIndex

Returns transaction information for the specified block hash and transaction index position.

**Parameters**

`DATA` - 32-byte hash of a block.

`QUANTITY` - Integer representing the transaction index position.

**Returns**

Object - [Transaction object](Pantheon-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByBlockHashAndIndex","params":["0xbf137c3a7a1ebdfac21252765e5d7f40d115c2757e4a4abee929be88c624fdb7", "0x2"], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{ block(hash: \"0x9270651f9c6fa36232c379d0ecf69b519383aa275815a65f1e03114346668f69\") { transactionAt(index: 0) {block{hash}  hash } } }"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(hash: "0x9270651f9c6fa36232c379d0ecf69b519383aa275815a65f1e03114346668f69") {
        transactionAt(index: 0) {
          block {
            hash
          }
          hash
        }
      }
    }
    ```
                
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "transactionAt" : {
            "block" : {
              "hash" : "0x9270651f9c6fa36232c379d0ecf69b519383aa275815a65f1e03114346668f69"
            },
            "hash" : "0x5f5366af89e8777d5ae62a1af94a0876bdccbc22417bed0aff361eefa3e37f86"
          }
        }
      }
    }
    ```

### eth_getTransactionByBlockNumberAndIndex

Returns transaction information for the specified block number and transaction index position.

**Parameters**

`QUANTITY|TAG` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

`QUANTITY` - The transaction index position.

**Returns**

Object - [Transaction object](Pantheon-API-Objects.md#transaction-object), or `null` when no transaction is found.

!!! example
    This request returns the third transaction in the 82990 block on the Ropsten testnet. You can also view this [block](https://ropsten.etherscan.io/txs?block=82990) and [transaction](https://ropsten.etherscan.io/tx/0xfc766a71c406950d4a4955a340a092626c35083c64c7be907060368a5e6811d6) on Etherscan.

    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionByBlockNumberAndIndex","params":["82990", "0x2"], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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

    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{block(number:20303) {transactionAt(index: 0) {block{hash} hash}}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(number: 20303) {
        transactionAt(index: 0) {
          block {
            hash
          }
          hash
        }
      }
    }
    ```
                    
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "transactionAt" : {
          "block" : {
            "hash" : "0x9270651f9c6fa36232c379d0ecf69b519383aa275815a65f1e03114346668f69"
          },
          "hash" : "0x5f5366af89e8777d5ae62a1af94a0876bdccbc22417bed0aff361eefa3e37f86"
          }
        }
      }
    }
    ```

### eth_getTransactionReceipt

Returns the receipt of a transaction by transaction hash. Receipts for pending transactions are not available.

If [revert reason](../Using-Pantheon/Transactions/Revert-Reason.md) is enabled, includes available revert 
reasons in the response. 

**Parameters**

`DATA` - 32-byte hash of a transaction.

**Returns**

`Object` - [Transaction receipt object](Pantheon-API-Objects.md#transaction-receipt-object), or `null` when no receipt is found.

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["0x504ce587a65bdbdb6414a0c6c16d86a04dd79bfcc4f2950eec9634b30ce5370f"],"id":53}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{transaction(hash: \"0x5f5366af89e8777d5ae62a1af94a0876bdccbc22417bed0aff361eefa3e37f86\") {block{hash logsBloom} hash createdContract{address} cumulativeGasUsed gas gasUsed logs{topics} from{address} to{address} index}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      transaction(hash: "0x5f5366af89e8777d5ae62a1af94a0876bdccbc22417bed0aff361eefa3e37f86") {
        block {
          hash
          logsBloom
        }
        hash
        createdContract {
          address
        }
        cumulativeGasUsed
        gas
        gasUsed
        logs {
          topics
        }
        from {
          address
        }
        to {
          address
        }
        index
      }
    }
    ```
                        
    ```bash tab="GraphQL result"
    {
      "data" : {
        "transaction" : {
          "block" : {
            "hash" : "0x9270651f9c6fa36232c379d0ecf69b519383aa275815a65f1e03114346668f69",
            "logsBloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
          },
          "hash" : "0x5f5366af89e8777d5ae62a1af94a0876bdccbc22417bed0aff361eefa3e37f86",
          "createdContract" : null,
          "cumulativeGasUsed" : 21000,
          "gas" : 21000,
          "gasUsed" : 21000,
          "logs" : [ ],
          "from" : {
            "address" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
          },
          "to" : {
            "address" : "0x9d8f8572f345e1ae53db1dfa4a7fce49b467bd7f"
          },
          "index" : 0
        }
      }
    }
    ```

### eth_newFilter

Creates a [log filter](../Using-Pantheon/Events-and-Logs.md). To poll for logs associated with the created filter, use [eth_getFilterChanges](#eth_getfilterchanges).

**Parameters**

`Object` - [Filter options object](Pantheon-API-Objects.md#filter-options-object). 

!!!note
    `fromBlock` and `toBlock` in the filter options object default to `latest`. To obtain logs using `eth_getFilterLogs`, set `fromBlock` and `toBlock` appropriately.
    
**Returns**

`data` - Filter ID hash

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newFilter","params":[{"fromBlock":"earliest", "toBlock":"latest", "topics":[]}],"id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newBlockFilter","params":[],"id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_newPendingTransactionFilter","params":[],"id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_uninstallFilter","params":["0x70355a0b574b437eaa19fe95adfedc0a"],"id":1}' http://127.0.0.1:8545
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
* For filters created with `eth_newFilter`, returns [log objects](Pantheon-API-Objects.md#log-object). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterChanges","params":["0xf8bf5598d9e04fbe84523d42640b9b0e"],"id":1}' http://127.0.0.1:8545
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

`array` - [Log objects](Pantheon-API-Objects.md#log-object)

!!! example

    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getFilterLogs","params":["0x5ace5de3985749b6a1b2b0d3f3e1fb69"],"id":1}' http://127.0.0.1:8545
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

`Object` - [Filter options object](Pantheon-API-Objects.md#filter-options-object)

**Returns**

`array` - [Log objects](Pantheon-API-Objects.md#log-object)

!!! example
    ```bash tab="curl HTTP"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getLogs","params":[{"fromBlock":"earliest", "toBlock":"latest", "address": "0x2e1f232a9439c3d459fceca0beef13acc8259dd8", "topics":[]}], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS"
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
    
    ```bash tab="curl GraphQL"
    curl -X POST -H "Content-Type: application/json" --data '{"query": "{block(number:23037) {logs(filter:{topics:[[\"0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8\", \"0x0000000000000000000000000000000000000000000000000000000000000002\"]]}) {index topics data account{address} transaction{hash} }}}"}' http://localhost:8547/graphql
    ```
    
    ```bash tab="GraphQL"
    {
      block(number: 23037) {
        logs(filter: {topics: [["0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8", "0x0000000000000000000000000000000000000000000000000000000000000002"]]}) {
          index
          topics
          data
          account {
            address
          }
          transaction {
            hash
          }
        }
      }
    }
    ```
                            
    ```bash tab="GraphQL result"
    {
      "data" : {
        "block" : {
          "logs" : [ {
            "index" : 0,
            "topics" : [ "0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8", "0x0000000000000000000000000000000000000000000000000000000000000002" ],
            "data" : "0x0000000000000000000000000000000000000000000000000000000000000003",
            "account" : {
              "address" : "0x686afd6e502a81d2e77f2e038a23c0def4949a20"
            },
            "transaction" : {
              "hash" : "0x29b541c12ad308960d4e7e0da00e0281a18428b3417f0f9b4ad8f68c4f5adc3c"
            }
          } ]
        }
      }
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"eth_getWork","params":[],"id":1}' http://127.0.0.1:8545
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
    The `CLIQUE` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `CLIQUE` API methods.

### clique_discard

Discards a proposal to [add or remove a signer with the specified address](../Consensus-Protocols/Clique.md#adding-and-removing-signers). 

**Parameters** 

`data` - 20-byte address of proposed signer. 

**Returns** 

`result: boolean` - `true`

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_discard","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"], "id":1}' http://127.0.0.1:8545
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

Lists [signers for the specified block](../Consensus-Protocols/Clique.md#adding-and-removing-signers). 

**Parameters** 

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter). 

**Returns**

`result: array of data` - List of 20-byte addresses of signers. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_getSigners","params":["latest"], "id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_getSignersAtHash","params":["0x98b2ddb5106b03649d2d337d42154702796438b3c74fd25a5782940e84237a48"], "id":1}' http://127.0.0.1:8545
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

Proposes [adding or removing a signer with the specified address](../Consensus-Protocols/Clique.md#adding-and-removing-signers). 

**Parameters**

`data` - 20-byte address.
 
`boolean` -  `true` to propose adding signer or `false` to propose removing signer. 

**Returns** 

`result: boolean` - `true`
   
!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_propose","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73", true], "id":1}' http://127.0.0.1:8545
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

Returns [current proposals](../Consensus-Protocols/Clique.md#adding-and-removing-signers). 

**Parameters**

None

**Returns** 

`result`:_object_ - Map of account addresses to corresponding boolean values indicating the proposal for each account. 

If the boolean value is `true`, the proposal is to add a signer. If `false`, the proposal is to remove a signer. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_proposals","params":[], "id":1}' http://127.0.0.1:8545
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
    The `DEBUG` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `DEBUG` API methods.

### debug_storageRangeAt

[Remix](https://remix.ethereum.org/) uses `debug_storageRangeAt` to implement debugging. Use the _Debugger_ tab in Remix rather than calling `debug_storageRangeAt` directly.  

Returns the contract storage for the specified range. 

**Parameters**

`blockHash` : `data` - Block hash

`txIndex` : `integer` - Transaction index from which to start

`address` : `data` - Contract address 

`startKey` : `hash` - Start key

`limit` : `integer` - Number of storage entries to return 

**Returns**

`result`:`object` - [Range object](Pantheon-API-Objects.md#range-object)  

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_storageRangeAt","params":["0x2b76b3a2fc44c0e21ea183d06c846353279a7acf12abcc6fb9d5e8fb14ae2f8c",0,"0x0e0d2c8f7794e82164f11798276a188147fbd415","0x0000000000000000000000000000000000000000000000000000000000000000",1], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_storageRangeAt","params":["0x2b76b3a2fc44c0e21ea183d06c846353279a7acf12abcc6fb9d5e8fb14ae2f8c",0,"0x0e0d2c8f7794e82164f11798276a188147fbd415","0x0000000000000000000000000000000000000000000000000000000000000000",1], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "storage": {
                "0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563": {
                    "key": null,
                    "value": "0x0000000000000000000000000000000000000000000000000000000000000001"
                }
            },
            "nextKey": "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6"
        }
    }
    ```

### debug_metrics

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
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_metrics","params":[],"id":1}' http://127.0.0.1:8545
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

`result`:`object` - [Trace object](Pantheon-API-Objects.md#trace-object). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_traceTransaction","params":["0x2cc6c94c21685b7e0f8ddabf277a5ccf98db157c62619cde8baea696a74ed18e",{"disableStorage":true}],"id":1}' http://127.0.0.1:8545
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

### debug_traceBlock

Returns full trace of all invoked opcodes of all transactions included in the block.

**Parameters**

`Block` : `data` - RLP of the block

`Object` - request options (all optional and default to `false`):

* `disableStorage` : `boolean` - `true` disables storage capture. 
* `disableMemory` : `boolean` - `true` disables memory capture. 
* `disableStack` : `boolean` - `true` disables stack capture. 

**Returns**

`result`:`object` - [Trace object](Pantheon-API-Objects.md#trace-object). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_traceBlock","params":["0xf90277f90208a05a41d0e66b4120775176c09fcf39e7c0520517a13d2b57b18d33d342df038bfca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794e6a7a1d47ff21b6321162aea7c6cb457d5476bcaa00e0df2706b0a4fb8bd08c9246d472abbe850af446405d9eba1db41db18b4a169a04513310fcb9f6f616972a3b948dc5d547f280849a87ebb5af0191f98b87be598a0fe2bf2a941abf41d72637e5b91750332a30283efd40c424dc522b77e6f0ed8c4b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000860153886c1bbd82b44382520b8252088455c426598b657468706f6f6c2e6f7267a0b48c515a9dde8d346c3337ea520aa995a4738bb595495506125449c1149d6cf488ba4f8ecd18aab215f869f86780862d79883d2000825208945df9b87991262f6ba471f09758cde1c0fc1de734827a69801ca088ff6cf0fefd94db46111149ae4bfc179e9b94721fffd821d38d16464b3f71d0a045e0aff800961cfce805daef7016b9b675c137a6a41a548f7b60a3484c06a33ac0"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_traceBlock","params":["0xf90277f90208a05a41d0e66b4120775176c09fcf39e7c0520517a13d2b57b18d33d342df038bfca01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794e6a7a1d47ff21b6321162aea7c6cb457d5476bcaa00e0df2706b0a4fb8bd08c9246d472abbe850af446405d9eba1db41db18b4a169a04513310fcb9f6f616972a3b948dc5d547f280849a87ebb5af0191f98b87be598a0fe2bf2a941abf41d72637e5b91750332a30283efd40c424dc522b77e6f0ed8c4b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000860153886c1bbd82b44382520b8252088455c426598b657468706f6f6c2e6f7267a0b48c515a9dde8d346c3337ea520aa995a4738bb595495506125449c1149d6cf488ba4f8ecd18aab215f869f86780862d79883d2000825208945df9b87991262f6ba471f09758cde1c0fc1de734827a69801ca088ff6cf0fefd94db46111149ae4bfc179e9b94721fffd821d38d16464b3f71d0a045e0aff800961cfce805daef7016b9b675c137a6a41a548f7b60a3484c06a33ac0"],"id":1}
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

### debug_traceBlockByHash

Returns full trace of all invoked opcodes of all transactions included in the block.

**Parameters**

`block hash` : `data` - Block hash

`Object` - request options (all optional and default to `false`):

* `disableStorage` : `boolean` - `true` disables storage capture. 
* `disableMemory` : `boolean` - `true` disables memory capture. 
* `disableStack` : `boolean` - `true` disables stack capture. 

**Returns**

`result`:`array of objects` - [Trace objects](Pantheon-API-Objects.md#trace-object). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_traceBlockByHash","params":["0xaceb3b2c9b25b0589230873921eb894b28722011b8df63977145517d754875a5"], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_traceBlockByHash","params":["0xaceb3b2c9b25b0589230873921eb894b28722011b8df63977145517d754875a5"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "gas": 21000,
                "failed": false,
                "returnValue": "",
                "structLogs": [
                    {
                        "pc": 0,
                        "op": "STOP",
                        "gas": 0,
                        "gasCost": 0,
                        "depth": 1,
                        "stack": [],
                        "memory": [],
                        "storage": {},
                        "reason": null
                    }
                ]
            }
        ]
    }
    ```
    
### debug_traceBlockByNumber

Returns full trace of all invoked opcodes of all transactions included in the block.

**Parameters**

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter).

`Object` - request options (all optional and default to `false`):

* `disableStorage` : `boolean` - `true` disables storage capture. 
* `disableMemory` : `boolean` - `true` disables memory capture. 
* `disableStack` : `boolean` - `true` disables stack capture. 

**Returns**

`result`:`array of objects` - [Trace objects](Pantheon-API-Objects.md#trace-object). 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"debug_traceBlockByNumber","params":["0x7224",{"disableStorage":true}], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"debug_traceBlockByNumber","params":["0x7224",{"disableStorage":true}], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "gas": 21000,
                "failed": false,
                "returnValue": "",
                "structLogs": [
                    {
                        "pc": 0,
                        "op": "STOP",
                        "gas": 0,
                        "gasCost": 0,
                        "depth": 1,
                        "stack": [],
                        "memory": [],
                        "storage": null,
                        "reason": null
                    }
                ]
            }
        ]
    }
    ```

## Miner Methods

!!! note
    The `MINER` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `MINER` API methods.

### miner_start

Starts the CPU mining process. To start mining, a miner coinbase must have been previously specified using the [`--miner-coinbase`](../Reference/Pantheon-CLI-Syntax.md#miner-coinbase) command line option.  

**Parameters**

None

**Returns**

`result` :  `boolean` - `true` if the mining start request was received successfully; otherwise returns an error. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"miner_start","params":[],"id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"miner_stop","params":[],"id":1}' http://127.0.0.1:8545
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
    The `IBFT` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `IBFT` API methods.

### ibft_discardValidatorVote

Discards a proposal to [add or remove a validator](../Consensus-Protocols/IBFT.md#adding-and-removing-validators) with the specified address. 

**Parameters** 

`data` - 20-byte address of proposed validator 

**Returns** 

`result: boolean` - `true`

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_discardValidatorVote","params":["0xef1bfb6a12794615c9b0b5a21e6741f01e570185"], "id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getPendingVotes","params":[], "id":1}' http://127.0.0.1:8545
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
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockHash","params":["0xbae7d3feafd743343b9a4c578cab5e5d65eb735f6855fb845c00cab356331256"], "id":1}' http://127.0.0.1:8545
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

`quantity|tag` - Integer representing a block number or one of the string tags `latest`, `earliest`, or `pending`, as described in [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter). 

**Returns**

`result: array of data` - List of validator addresses 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockNumber","params":["latest"], "id":1}' http://127.0.0.1:8545
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

Proposes [adding or removing a validator](../Consensus-Protocols/IBFT.md#adding-and-removing-validators) with the specified address. 

**Parameters**

`data` - Account address
 
`boolean` -  `true` to propose adding validator or `false` to propose removing validator. 

**Returns** 

`result: boolean` - `true`
   
!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_proposeValidatorVote","params":["42d4287eac8078828cf5f3486cfe601a275a49a5",true], "id":1}' http://127.0.0.1:8545
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

!!! note
    The `PERM` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `PERM` API methods.

### perm_addAccountsToWhitelist

Adds accounts (participants) to the [accounts whitelist](../Permissions/Local-Permissioning.md#account-whitelisting). 

**Parameters** 

`list of strings` - List of account addresses 

!!! note 
    The parameters list contains a list which is why the account addresses are enclosed by double square brackets. 

**Returns** 

`result` - `Success` or `error`. Errors include attempting to add accounts already on the whitelist or 
including invalid account addresses. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_addAccountsToWhitelist","params":[["0xb9b81ee349c3807e46bc71aa2632203c5b462032", "0xb9b81ee349c3807e46bc71aa2632203c5b462034"]], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_addAccountsToWhitelist","params":[["0xb9b81ee349c3807e46bc71aa2632203c5b462032", "0xb9b81ee349c3807e46bc71aa2632203c5b462034"]], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "Success"
    }
    ```
    
### perm_getAccountsWhitelist

Lists accounts (participants) in the [accounts whitelist](../Permissions/Local-Permissioning.md#account-whitelisting). 

**Parameters** 

None

**Returns** 

`result: list` - Accounts (participants) in the accounts whitelist. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_getAccountsWhitelist","params":[], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_getAccountsWhitelist","params":[], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "0x0000000000000000000000000000000000000009",
            "0xb9b81ee349c3807e46bc71aa2632203c5b462033"
        ]
    }
    ``` 
    
### perm_removeAccountsFromWhitelist

Removes accounts (participants) from the [accounts whitelist](../Permissions/Local-Permissioning.md#account-whitelisting). 

**Parameters** 

`list of strings` - List of account addresses 

!!! note 
    The parameters list contains a list which is why the account addresses are enclosed by double square brackets.

**Returns** 

`result` - `Success` or `error`. Errors include attempting to remove accounts not on the whitelist or 
including invalid account addresses.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_removeAccountsFromWhitelist","params":[["0xb9b81ee349c3807e46bc71aa2632203c5b462032", "0xb9b81ee349c3807e46bc71aa2632203c5b462034"]], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_removeAccountsFromWhitelist","params":[["0xb9b81ee349c3807e46bc71aa2632203c5b462032", "0xb9b81ee349c3807e46bc71aa2632203c5b462034"]], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "Success"
    }
    ```
### perm_addNodesToWhitelist

Adds nodes to the [nodes whitelist](../Permissions/Local-Permissioning.md#node-whitelisting). 

**Parameters** 

`list of strings` - List of [enode URLs](../Configuring-Pantheon/Node-Keys.md#enode-url) 

!!! note 
    The parameters list contains a list which is why the enode URLs are enclosed by double square brackets.

**Returns** 

`result` - `Success` or `error`. Errors include attempting to add nodes already on the whitelist or 
including invalid enode URLs.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_addNodesToWhitelist","params":[["enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304"]], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_addNodesToWhitelist","params":[["enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304"]], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "Success"
    }
    ```
    
### perm_getNodesWhitelist

Lists nodes in the [nodes whitelist](../Permissions/Local-Permissioning.md#node-whitelisting). 

**Parameters** 

None

**Returns** 

`result: list` - [Enode URLs](../Configuring-Pantheon/Node-Keys.md#enode-url) of nodes in the nodes whitelist. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_getNodesWhitelist","params":[], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_getNodesWhitelist","params":[], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            "enode://7b61d5ee4b44335873e6912cb5dd3e3877c860ba21417c9b9ef1f7e500a82213737d4b269046d0669fb2299a234ca03443f25fe5f706b693b3669e5c92478ade@127.0.0.1:30305",
            "enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304"
        ]
    }
    ``` 
    
### perm_removeNodesFromWhitelist

Removes nodes from the [nodes whitelist](../Permissions/Local-Permissioning.md#node-whitelisting). 

**Parameters** 

`list of strings` - List of [enode URLs](../Configuring-Pantheon/Node-Keys.md#enode-url)

!!! note 
    The parameters list contains a list which is why the enode URLs are enclosed by double square brackets.

**Returns** 

`result` - `Success` or `error`. Errors include attempting to remove nodes not on the whitelist or 
including invalid enode URLs.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_removeNodesFromWhitelist","params":[["enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304"]], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_removeNodesFromWhitelist","params":[["enode://7e4ef30e9ec683f26ad76ffca5b5148fa7a6575f4cfad4eb0f52f9c3d8335f4a9b6f9e66fcc73ef95ed7a2a52784d4f372e7750ac8ae0b544309a5b391a23dd7@127.0.0.1:30303","enode://2feb33b3c6c4a8f77d84a5ce44954e83e5f163e7a65f7f7a7fec499ceb0ddd76a46ef635408c513d64c076470eac86b7f2c8ae4fcd112cb28ce82c0d64ec2c94@127.0.0.1:30304"]], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "Success"
    }
    ```
    
### perm_reloadPermissionsFromFile

Reloads the accounts and nodes whitelists from the [permissions configuration file](../Permissions/Local-Permissioning.md#permissions-configuration-file). 

**Parameters** 

None

**Returns** 

`result` - `Success` or `error` if the permissions configuration file is not valid.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"perm_reloadPermissionsFromFile","params":[], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"perm_reloadPermissionsFromFile","params":[], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "Success"
    }
    ```

## Txpool Methods 

!!! note
    The `TXPOOL` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `TXPOOL` API methods.

### txpool_pantheonStatistics

Lists statistics about the node transaction pool. 

**Parameters** 

None

**Returns** 

`result` - Transaction pool statistics: 

* `maxSize` - Maximum number of transactions kept in the transaction pool. Use the [`--tx-pool-max-size`](Pantheon-CLI-Syntax.md#tx-pool-max-size)
 option to configure the maximum size. 
* `localCount` - Number of transactions submitted directly to this node 
* `remoteCount` - Number of transactions received from remote nodes. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"txpool_pantheonStatistics","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"txpool_pantheonStatistics","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "maxSize": 4096,
            "localCount": 1,
            "remoteCount": 0
        }
    }
    ``` 

### txpool_pantheonTransactions

Lists transactions in the node transaction pool. 

**Parameters** 

None

**Returns** 

`result` - List of transactions 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"txpool_pantheonTransactions","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"txpool_pantheonTransactions","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "hash": "0x8a66830098be4006a3f63a03b6e9b67aa721e04bd6b46d420b8f1937689fb4f1",
                "isReceivedFromLocalSource": true,
                "addedToPoolAt": "2019-03-21T01:35:50.911Z"
            },
            {
                "hash": "0x41ee803c3987ceb5bcea0fad7a76a8106a2a6dd654409007d9931032ea54579b",
                "isReceivedFromLocalSource": true,
                "addedToPoolAt": "2019-03-21T01:36:00.374Z"
            }
        ]
    }
    ``` 
            
## EEA Methods

!!! note
    The `EEA` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `EEA` API methods.

### eea_sendRawTransaction

Creates a [private transaction](../Privacy/Explanation/Privacy-Overview.md) from a signed transaction, generates the transaction hash 
and submits it to the transaction pool, and returns the transaction hash of the Privacy Marker Transaction.

The signed transaction passed as an input parameter includes the `privateFrom`, `privateFor`, and `restriction` fields.

To avoid exposing your private key, create signed transactions offline and send the signed transaction 
data using `eea_sendRawTransaction`.

!!! important
    For production systems requiring private transactions, we recommend using a network 
    with a consensus mechanism supporting transaction finality. For example, [IBFT 2.0](../Consensus-Protocols/IBFT.md). 
    
    Pantheon does not implement [`eea_sendTransaction`](../Using-Pantheon/Account-Management.md). 
        
    [EthSigner](https://docs.ethsigner.pegasys.tech/en/latest/) provides transaction signing and implements [`eea_sendTransaction`](https://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner/Using-EthSigner/#eea_sendtransaction).

**Parameters**

`data` -  Signed RLP-encoded private transaction. For example:

`params: ["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"]`

**Returns**

`result` : `data` - 32-byte transaction hash

!!! tip
    If creating a contract, use [eea_getTransactionReceipt](#eea_gettransactionreceipt) to retrieve the contract 
    address after the transaction is finalized.

!!! example 
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eea_sendRawTransaction","params": ["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"], "id":1}' http://127.0.0.1:8545
    ```
        
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eea_sendRawTransaction","params": ["0xf869018203e882520894f17f52151ebef6c7334fad080c5704d77216b732881bc16d674ec80000801ba02da1c48b670996dcb1f447ef9ef00b33033c48a4fe938f420bec3e56bfd24071a062e0aa78a81bf0290afbc3a9d8e9a068e6d74caa66c5e0fa8a46deaae96b0833"], "id":1}
    ```
        
    ```json tab="JSON result"
    {
      "id":1,
      "jsonrpc": "2.0",
      "result": "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d1527331"
    }
    ```

### eea_getTransactionReceipt

Returns information about the private transaction after the transaction was mined. Receipts for pending transactions 
are not available.

**Parameters**

`data` - 32-byte hash of a transaction.

**Returns**

`Object` - [Private Transaction receipt object](Pantheon-API-Objects.md#private-transaction-receipt-object), or `null` if no receipt found.

!!! example 
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"eea_getTransactionReceipt","params":["0xf3ab9693ad92e277bf785e1772f29fb1864904bbbe87b0470455ddb082caab9d"],"id":1}' http://127.0.0.1:8545
    ```
            
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"eea_getTransactionReceipt","params":["0xf3ab9693ad92e277bf785e1772f29fb1864904bbbe87b0470455ddb082caab9d"],"id":1}
    ```
            
    ```json tab="JSON result"
    {
       "jsonrpc": "2.0",
       "id": 1,
       "result": {
           "contractAddress": "0xf4464be696b6531b87edbfb8c21dd178c34eb89e",
           "from": "0x372a70ace72b02cc7f1757183f98c620254f9c8d",
           "to": null,
           "output": "0x6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029",
           "logs": []
       }
    }
    ```

## Priv Methods 

!!! note
    The `PRIV` API methods are not enabled by default for JSON-RPC. Use the [`--rpc-http-api`](Pantheon-CLI-Syntax.md#rpc-http-api) 
    or [`--rpc-ws-api`](Pantheon-CLI-Syntax.md#rpc-ws-api) options to enable the `PRIV` API methods.

### priv_getPrivacyPrecompileAddress

Returns the address of the [privacy precompiled contract](../Privacy/Explanation/Private-Transaction-Processing.md). 
The address is specified by the [`--privacy-precompiled-address`](Pantheon-CLI-Syntax.md#privacy-precompiled-address) command line option. 

**Parameters**

None

**Returns**

`result` : `data` - Address of the privacy precompile 

!!! example 
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"priv_getPrivacyPrecompileAddress","params":[], "id":1}' http://127.0.0.1:8545
    ```
        
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"priv_getPrivacyPrecompileAddress","params":[], "id":1}
    ```
        
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": "0x000000000000000000000000000000000000007e"
    }
    ```

### priv_getPrivateTransaction

Returns the private transaction if you are a participant; otherwise, null. 

**Parameters** 

`data` - Transaction hash returned by [`eea_sendRawTransaction`](#eea_sendrawtransaction) or 
[`eea_sendTransction`](https://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner/Using-EthSigner/#eea_sendtransaction). .

**Returns**  

Object - [Private transaction object](Pantheon-API-Objects.md#private-transaction-object), or `null` if not a participant in the private transaction. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"priv_getPrivateTransaction","params":["0x623c4ce5275a87b91f4f1c521012d39ca19311c787bde405490f4c0426a71498"], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"priv_getPrivateTransaction","params":["0x623c4ce5275a87b91f4f1c521012d39ca19311c787bde405490f4c0426a71498"], "id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "gas": "0x2dc6c0",
            "gasPrice": "0x0",
            "hash": "0x623c4ce5275a87b91f4f1c521012d39ca19311c787bde405490f4c0426a71498",
            "input": "0x608060405234801561001057600080fd5b50336000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550610221806100606000396000f300608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633fa4f2451461005c5780636057361d1461008757806367e404ce146100b4575b600080fd5b34801561006857600080fd5b5061007161010b565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b506100b260048036038101908080359060200190929190505050610115565b005b3480156100c057600080fd5b506100c96101cb565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000600254905090565b7fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f53382604051808373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020018281526020019250505060405180910390a18060028190555033600160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000600160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff169050905600a165627a7a723058208efaf938851fb2d235f8bf9a9685f149129a30fe0f4b20a6c1885dc02f639eba0029",
            "nonce": "0x0",
            "to": null,
            "value": "0x0",
            "v": "0xfe8",
            "r": "0x654a6a9663ca70bb13e27cca14b3777cc92da184e19a151cdeef2ccbbd5c6405",
            "s": "0x5dd4667b020c8a5af7ae28d4c3126f8dcb1187f49dcf0de9d7a39b1651892eef",
            "privateFrom": "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=",
            "privateFor": [
                "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="
            ],
            "restriction": "restricted"
        }
    }
    ```


### priv_createPrivacyGroup  

Creates a privacy group containing the specified members. Members are specified by their Orion public key. 

**Parameters**  

`array of data` - Array of members specified by Orion public keys 

`string` - Privacy group name 

`string` - Privacy group description

**Returns** 

Privacy group ID 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"priv_createPrivacyGroup","params":[["negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=","g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="], "Group A", "Description Group A"],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"priv_createPrivacyGroup","params":[["negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=","g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="], "Group A", "Description Group A"],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": "ewuTVoc5nlvWMwTFdRRK/wvV0dcyQo/Pauvx5bNEbTk="
    }
    ``` 

### priv_deletePrivacyGroup

Deletes the specified privacy group. 

**Parameters** 

`data` - Privacy group ID 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"priv_deletePrivacyGroup","params":["ewuTVoc5nlvWMwTFdRRK/wvV0dcyQo/Pauvx5bNEbTk="],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"priv_deletePrivacyGroup","params":["ewuTVoc5nlvWMwTFdRRK/wvV0dcyQo/Pauvx5bNEbTk="],"id":1}
    ```
    
    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 53,
      "result": "ewuTVoc5nlvWMwTFdRRK/wvV0dcyQo/Pauvx5bNEbTk="
    }
    ```

### priv_findPrivacyGroup

Returns a list of privacy groups containing only the listed members. For example, if the listed members 
are A and B, a privacy group containing A, B, and C is not returned. 

**Parameters** 

`array of data` - Members specified by Orion public keys 

**Returns** 

Privacy groups containing only the specified members. 

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc": "2.0","method": "priv_findPrivacyGroup","params": [["negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=", "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="]],"id": 1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc": "2.0","method": "priv_findPrivacyGroup","params": [["negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=", "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="]],"id": 1}
    ```
 
    ```json tab="JSON result"
    {
     "jsonrpc": "2.0",
     "id": 1,
     "result": [
       {
         "privacyGroupId": "GpK3ErNO0xF27T0sevgkJ3+4qk9Z+E3HtXYxcKIBKX8=",
         "name": "Group B",
         "description": "Description of Group B",
         "type": "PANTHEON",
         "members": [
           "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=",
           "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="
         ]
       }
    ]
    }
    ```
    
### priv_getTransactionCount

Returns the private transaction count for specified account and privacy group. 

!!! important 
    If sending more than 1 transaction to be mined in the same block (that is, you're not waiting for 
    the transaction receipt), you must calculate the private transaction nonce outside Pantheon. 

**Parameters** 

`data` - Account address

`data` - Privacy group ID 

**Returns** 

`quantity` - Integer representing the number of private transactions sent from the address to the specified privacy group.

!!! example 
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"priv_getTransactionCount","params":["0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "kAbelwaVW7okoEn1+okO+AbA4Hhz/7DaCOWVQz9nx5M="], "id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"priv_getTransactionCount","params":["0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", "kAbelwaVW7okoEn1+okO+AbA4Hhz/7DaCOWVQz9nx5M="], "id":1}
    ```

    ```json tab="JSON result"
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": "0x1"
    }
    ```  

## Miscellaneous Methods 

### rpc_modules

Lists [enabled APIs](../Pantheon-API/Using-JSON-RPC-API.md#api-methods-enabled-by-default) and the version of each.  

**Parameters** 

None

**Returns** 

Enabled APIs.

!!! example
    ```bash tab="curl HTTP request"
    curl -X POST --data '{"jsonrpc":"2.0","method":"rpc_modules","params":[],"id":1}' http://127.0.0.1:8545
    ```
    
    ```bash tab="wscat WS request"
    {"jsonrpc":"2.0","method":"rpc_modules","params":[],"id":1}
    ```
    
    ```json tab="JSON result"
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "web3": "1.0",
            "eth": "1.0",
            "net": "1.0"
        }
    }
