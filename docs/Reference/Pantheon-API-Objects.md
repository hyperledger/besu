description: Pantheon API objects reference
<!--- END of page meta data -->

# Pantheon API Objects

The following objects are parameters for or returned by Pantheon API Methods.

## Block Object

Returned by [eth_getBlockByHash](Pantheon-API-Methods.md#eth_getblockbyhash) and [eth_getBlockByNumber](Pantheon-API-Methods.md#eth_getblockbynumber).

| Key                  | Type                  | Value                                                                                                                            |
|----------------------|:---------------------:|----------------------------------------------------------------------------------------------------------------------------------|
| **number**           | *Quantity*, Integer   | Block number. `null` when block is pending.                                                                                      |
| **hash**             | *Data*, 32&nbsp;bytes | Hash of the block. `null` when block is pending.                                                                                 |
| **parentHash**       | *Data*, 32&nbsp;bytes | Hash of the parent block.                                                                                                        |
| **nonce**            | *Data*, 8&nbsp;bytes  | Hash of the generated proof of work. `null` when block is pending.                                                               |
| **sha3Uncles**       | *Data*, 32&nbsp;bytes | SHA3 of the uncle's data in the block.                                                                                           |
| **logsBloom**        | *Data*, 256 bytes     | Bloom filter for the block logs. `null` when block is pending.                                                                   |
| **transactionsRoot** | *Data*, 32&nbsp;bytes | Root of the transaction trie for the block.                                                                                      |
| **stateRoot**        | Data, 32&nbsp;bytes   | Root of the final state trie for the block.                                                                                      |
| **receiptsRoot**     | Data, 32&nbsp;bytes   | Root of the receipts trie for the block.                                                                                         |
| **miner**            | Data, 20&nbsp;bytes   | Address to which mining rewards were paid.                                                                                       |
| **difficulty**       | Quantity, Integer     | Difficulty for this block.                                                                                                       |
| **totalDifficulty**  | Quantity, Integer     | Total difficulty of the chain until this block.                                                                                  |
| **extraData**        | Data                  | Extra data field of this block.                                                                                                  |
| **size**             | Quantity, Integer     | Size of block in bytes.                                                                                                          |
| **gasLimit**         | Quantity              | Maximum gas allowed in this block.                                                                                               |
| **gasUsed**          | Quantity              | Total gas used by all transactions in this block.                                                                                |
| **timestamp**        | Quantity              | Unix timestamp when block was collated.                                                                                          |
| **transactions**     | Array                 | Array of [transaction objects](#transaction-object), or 32 byte transaction hashes depending on the specified boolean parameter. |
| **uncles**           | Array                 | Array of uncle hashes.                                                                                                           |


## Filter Options Object

Parameter for [eth_newFilter](Pantheon-API-Methods.md#eth_newfilter) and [eth_getLogs](Pantheon-API-Methods.md#eth_getlogs). Used to [filter logs](../Using-Pantheon/Accessing-Logs-Using-JSON-RPC.md). 

| Key           | Type                              | Required/Optional | Value                                                                                                                                       |
|---------------|:---------------------------------:|:-----------------:|---------------------------------------------------------------------------------------------------------------------------------------------|
| **fromBlock** | Quantity &#124; Tag               | Optional          | Integer block number or `latest`, `pending`, `earliest`. See [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter). Default is `latest`. |
| **toBlock**   | Quantity &#124; Tag               | Optional          | Integer block number or `latest`, `pending`, `earliest`. See [Block Parameter](../Pantheon-API/Using-JSON-RPC-API.md#block-parameter). Default is `latest`. |
| **address**   | Data &#124; Array                 | Optional          | Contract address or array of addresses from which [logs](../Using-Pantheon/Events-and-Logs.md) originate.                                                                           |
| **topics**    | Array of Data, 32&nbsp;bytes each | Optional          | Array of topics by which to [filter logs](../Using-Pantheon/Events-and-Logs.md#topic-filters).                             |

[eth_getLogs](Pantheon-API-Methods.md#eth_getlogs) has an additional key. 

| Key   |   Type            | Required/Optional | Value  |  
|------------|:-----------------:|:-----------------:|------|
| **blockhash** |Data, 32&nbsp;bytes | Optional | Hash of block for which to return logs. If `blockhash` is specified, `fromBlock` and `toBlock` cannot be specified. | 

## Log Object 

Returned by [eth_getFilterChanges](Pantheon-API-Methods.md#eth_getfilterchanges) and [transaction receipt objects](#transaction-receipt-object) can contain an array of log objects.  

| Key                  | Type                              | Value                                                                                                                                                                                                               |
|----------------------|-:- :------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **removed**          | Tag                               | `true` when log removed due to chain reorganization. `false` if valid log.                                                                                                                                         |
| **logIndex**         | Quantity, Integer                 | Log index position in the block. `null` when log is pending.                                                                                                                                                        |
| **transactionIndex** | Quantity, Integer                 | Index position of transaction from which log was created. `null` when log is pending.                                                                                                                               |
| **transactionHash**  | Data, 32&nbsp;bytes               | Hash of transaction from which log was created. `null` when log is pending.                                                                                                                                         |
| **blockHash**        | Data, 32&nbsp;bytes               | Hash of block in which log included. `null` when log is pending.                                                                                                                                                    |
| **blockNumber**      | Quantity                          | Number of block in which log included. `null` when log is pending.                                                                                                                                                  |
| **address**          | Data, 20&nbsp;bytes               | Address from which log originated.                                                                                                                                                                                  |
| **data**             | Data                              | Non-indexed arguments of log.                                                                                                                                                                                       |
| **topics**           | Array of Data, 32&nbsp;bytes each | [Event signature hash](../Using-Pantheon/Events-and-Logs.md#event-signature-hash) and 0 to 3 [indexed log arguments](../Using-Pantheon/Events-and-Logs.md#event-parameters).  |

## Private Transaction Object

Returned by [priv_getPrivateTransaction](Pantheon-API-Methods.md#priv_getprivatetransaction).

| Key                  | Type                              | Value                                                                           |
|----------------------|-:-:-------------------------------|---------------------------------------------------------------------------------|
| **from**             | Data, 20&nbsp;bytes               | Address of the sender.                                                          |
| **gas**              | Quantity                          | Gas provided by the sender.                                                     |
| **gasPrice**         | Quantity                          | Gas price provided by the sender in Wei.                                        |
| **hash**             | Data, 32&nbsp;bytes               | Hash of the transaction.                                                        |
| **input**            | Data                              | Data to create or invoke contract.                                                                                 |
| **nonce**            | Quantity                          | Number of transactions made by the sender to the privacy group before this one.                      |
| **to**               | Data, 20&nbsp;bytes               | `null` if a contract creation transaction; otherwise, contract address             |
| **value**            | Quantity                          | `null` because private transactions cannot transfer Ether                                                                          |
| **v**                | Quantity                          | ECDSA Recovery ID                                                               |
| **r**                | Data, 32&nbsp;bytes               | ECDSA signature r                                                               |
| **s**                | Data, 32&nbsp;bytes               | ECDSA signature s                                                               |
| **privateFrom**      | Data, 32&nbsp;bytes               | [Orion](https://docs.orion.pegasys.tech/en/stable/) public key of sender                                                      |
| **privateFor**       | Array of Data, 32&nbsp;bytes each | [Orion](https://docs.orion.pegasys.tech/en/stable/) public keys of recipients                                                 |
| **restriction**      | String                            | Must be [`restricted`](../Privacy/Explanation/Privacy-Overview.md#private-transaction-attributes) 
 
## Range Object

Returned by [debug_storageRangeAt](Pantheon-API-Methods.md#debug_storagerangeat).

| Key             | Type    | Value                                                             |
|-----------------|:-------:|-------------------------------------------------------------------|
| **storage**     | Object  | Key hash and value. Preimage key is always null         |
| **nextKey**      | Hash | Hash of next key if further storage in range. Otherwise, not included                   |


### Structured Log Object

Log information returned as part of the [Trace object](#trace-object). 

| Key                        | Type                         | Value                                                                                                                                                                                                                                                                                                                                               |
|----------------------------|:----------------------------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **pc**                     | Integer                      | Current program counter                                                                                                                                                                                                                                                                                                                             |
| **op**                     | String                       | Current OpCode                                                                                                                                                                                                                                                                                                                                      |
| **gas**                    | Integer                      | Gas remaining                                                                                                                                                                                                                                                                                                                                       |
| **gasCost**                | Integer                      | Cost in wei of each gas unit                                                                                                                                                                                                                                                                                                                        |
| **depth**                  | Integer                      | Execution depth                                                                                                                                                                                                                                                                                                                                     |
| **exceptionalHaltReasons** | Array                        | One or more strings representing an error condition that caused the EVM execution to terminate. These indicate that EVM execution terminated for reasons such as running out of gas or attempting to execute an unknown instruction.  Generally a single exceptional halt reason is returned but it is possible for more than one to occur at once. |
| **stack**                  | Array of 32&nbsp;byte arrays | EVM execution stack before executing current operation                                                                                                                                                                                                                                                                                              |
| **memory**                 | Array of 32&nbsp;byte arrays | Memory space of the contract before executing current operation                                                                                                                                                                                                                                                                                     |
| **storage**                | Object                       | Storage entries changed by the current transaction                                                                                                                                                                                                                                                                                                  |

## Trace Object

Returned by [debug_traceBlock](Pantheon-API-Methods.md#debug_traceblock), [debug_traceBlockByHash](Pantheon-API-Methods.md#debug_traceblockbyhash),
[debug_traceBlockByNumber](Pantheon-API-Methods.md#debug_traceblockbynumber), and [debug_traceTransaction](Pantheon-API-Methods.md#debug_tracetransaction).

| Key             | Type    | Value                                                             |
|-----------------|:-------:|-------------------------------------------------------------------|
| **gas**         | Integer | Gas used by the transaction                                       |
| **failed**      | Boolean | True if transaction failed; otherwise, false                      |
| **returnValue** | String  | Bytes returned from transaction execution (without a `0x` prefix) |
| **structLogs**  | Array   | Array of structured log objects                                   |

## Transaction Object

Returned by [eth_getTransactionByHash](Pantheon-API-Methods.md#eth_gettransactionbyhash), [eth_getTransactionByBlockHashAndIndex](Pantheon-API-Methods.md#eth_gettransactionbyblockhashandindex), and [eth_getTransactionsByBlockNumberAndIndex](Pantheon-API-Methods.md#eth_gettransactionbyblocknumberandindex).

| Key                  | Type                | Value                                                                                  |
|----------------------|:-------------------:|----------------------------------------------------------------------------------------|
| **blockHash**        | Data, 32&nbsp;bytes | Hash of block containing this transaction. `null` when transaction is pending.         |
| **blockNumber**      | Quantity            | Block number of block containing this transaction. `null` when transaction is pending. |
| **from**             | Data, 20&nbsp;bytes | Address of the sender.                                                                 |
| **gas**              | Quantity            | Gas provided by the sender.                                                            |
| **gasPrice**         | Quantity            | Gas price provided by the sender in Wei.                                               |
| **hash**             | Data, 32&nbsp;bytes | Hash of the transaction.                                                               |
| **input**            | Data                | Data sent with the transaction to create or invoke a contract. For [private transactions](../Privacy/Explanation/Privacy-Overview.md) it is a pointer to the transaction location in [Orion](https://docs.orion.pegasys.tech/en/stable/).                                                       |
| **nonce**            | Quantity            | Number of transactions made by the sender before this one.                             |
| **to**               | Data, 20&nbsp;bytes | Address of the receiver. `null` if a contract creation transaction.                    |
| **transactionIndex** | Quantity, Integer   | Index position of transaction in the block. `null` when transaction is pending.        |
| **value**            | Quantity            | Value transferred in Wei.                                                              |
| **v**                | Quantity            | ECDSA Recovery ID                                                                      |
| **r**                | Data, 32&nbsp;bytes | ECDSA signature r                                                                      |
| **s**                | Data, 32&nbsp;bytes | ECDSA signature s                                                                      |

## Transaction Call Object

Parameter for [eth_call](Pantheon-API-Methods.md#eth_call) and [eth_estimateGas](Pantheon-API-Methods.md#eth_estimategas).

!!!note
    All parameters are optional for [eth_estimateGas](Pantheon-API-Methods.md#eth_estimategas)

| Key          | Type                | Required/Optional | Value                                                                                                                                                                  |
|--------------|:-------------------:|:-----------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **from**     | Data, 20&nbsp;bytes | Optional          | Address from which transaction is sent.                                                                                                                                |
| **to**       | Data, 20&nbsp;bytes | Required          | Address to which transaction is directed.                                                                                                                              |
| **gas**      | Quantity, Integer   | Optional          | Gas provided for the transaction execution. `eth_call` consumes zero gas, but this parameter might be needed by some executions. `eth_estimateGas` ignores this value. |
| **gasPrice** | Quantity, Integer   | Optional          | Price used for each paid gas.                                                                                                                                          |
| **value**    | Quantity, Integer   | Optional          | Value sent with this transaction.                                                                                                                                      |
| **data**     | Data                | Optional          | Hash of the method signature and encoded parameters. For details, see [Ethereum Contract ABI](https://github.com/ethereum/wiki/wiki/Ethereum-Contract-ABI).            |

## Transaction Receipt Object 

Returned by [eth_getTransactionReceipt](Pantheon-API-Methods.md#eth_gettransactionreceipt).

| Key                   | Type                 | Value                                                                                |
|-----------------------|:--------------------:|--------------------------------------------------------------------------------------|
| **blockHash**         | Data, 32&nbsp;bytes  | Hash of block containing this transaction.                                           |
| **blockNumber**       | Quantity             | Block number of block containing this transaction.                                   |
| **contractAddress**   | Data, 20&nbsp;bytes  | Contract address created, if contract creation transaction; otherwise, `null`.       |
| **cumulativeGasUsed** | Quantity             | Total amount of gas used by previous transactions in the block and this transaction. |
| **from**              | Data, 20&nbsp;bytes  | Address of the sender.                                                               |
| **gasUsed**           | Quantity             | Amount of gas used by this specific transaction.                                     |
| **logs**              | Array                | Array of [log objects](#log-object) generated by this transaction.                   |
| **logsBloom**         | Data, 256&nbsp;bytes | Bloom filter for light clients to quickly retrieve related logs.                     |
| **status**            | Quantity             | Either `1` (success) or `0` (failure)                                                |
| **to**                | Data, 20&nbsp;bytes  | Address of the receiver, if sending ether; otherwise, null.                          |
| **transactionHash**   | Data, 32&nbsp;bytes  | Hash of the transaction.                                                             |
| **transactionIndex**  | Quantity, Integer    | Index position of transaction in the block.                                          |

!!!note
    For pre-Byzantium transactions, the transaction receipt object includes the following instead of `status`:

| Key   |   Type            |  Value  |  
|-------|:-----------------:|---------|
| **root** | Data, 32&nbsp;bytes| Post-transaction stateroot|

## Private Transaction Receipt Object 

Returned by [eea_getTransactionReceipt](Pantheon-API-Methods.md#eea_gettransactionreceipt).

| Key                   | Type                 | Value                                                                                |
|-----------------------|:--------------------:|--------------------------------------------------------------------------------------|
| **contractAddress**   | Data, 20&nbsp;bytes  | Contract address created, if contract creation transaction; otherwise, `null`.       |
| **from**              | Data, 20&nbsp;bytes  | Address of the sender.                                                               |
| **to**                | Data, 20&nbsp;bytes  | Address of the receiver, if sending ether; otherwise, null.                          |
| **output**            | Data                 | RLP-encoded return value of a contract call, if value is returned; otherwise, `null`.|
| **logs**              | Array                | Array of [log objects](#log-object) generated by this transaction.                   |