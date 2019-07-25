description: Pantheon IBFT 2.0 Proof-of-Authority (PoA) consensus protocol implementation
<!--- END of page meta data -->

*[Vanity]: Validators can include anything they like as vanity data. 
*[RLP]: Recursive Length Prefix
*[Byzantine fault tolerant]: Ability to function correctly and reach consensus despite nodes failing or propagating incorrect information to peers.

# IBFT 2.0

Pantheon implements the IBFT 2.0 Proof-of-Authority (PoA) consensus protocol. IBFT 2.0 can be used for private networks. 

In IBFT 2.0 networks, transactions and blocks are validated by approved accounts, known as validators. 
Validators take turns to create the next block. Existing validators propose and vote to add or remove validators. 

## Minimum Number of Validators 

IBFT 2.0 requires 4 validators to be Byzantine fault tolerant. 

## Genesis File

To use IBFT 2.0 requires an IBFT 2.0 genesis file. The genesis file defines properties specific to IBFT 2.0:

!!! example "Example IBFT 2.0 Genesis File"
    ```json
      {
        "config": {
          "chainId": 1981,
          "constantinoplefixblock": 0,
          "ibft2": {
            "blockperiodseconds": 2,
            "epochlength": 30000,
            "requesttimeoutseconds": 10
          }
        },
        "nonce": "0x0",
        "timestamp": "0x58ee40ba",
        "extraData": "0xf83ea00000000000000000000000000000000000000000000000000000000000000000d594c2ab482b506de561668e07f04547232a72897daf808400000000c0",
        "gasLimit": "0x47b760",
        "difficulty": "0x1",
        "mixHash": "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
        "coinbase": "0x0000000000000000000000000000000000000000",
        "alloc": {}
      }
    ```
    
Properties specific to IBFT 2.0 are:

* `blockperiodseconds` - Minimum block time in seconds. 
* `epochlength` - Number of blocks after which to reset all votes.
* `requesttimeoutseconds` - Timeout for each consensus round before a round change. 
* `extraData` - `RLP([32 Bytes Vanity, List<Validators>, No Vote, Round=Int(0), 0 Seals])`

Properties that have specific values in IBFT 2.0 genesis files are: 

* `nonce` - `0x0`
* `difficulty` - `0x1`
* `mixHash` - `0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365` for Istanbul block identification.

To start a node on an IBFT 2.0 private network, use the [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) option to specify the custom genesis file. 

### Extra Data 

The `extraData` property is RLP encoded. RLP encoding is a space efficient object serialization scheme 
used in Ethereum. Use the Pantheon subcommand [`rlp encode`](../Reference/Pantheon-CLI-Syntax.md#rlp) 
to generate the `extraData` RLP string to include in the genesis file. 

!!! example                                        
    ```bash
    pantheon rlp encode --from=toEncode.json
    ```    

Where the `toEncode.json` file contains a list of the initial validators in ascending order. 

!!! example "One Initial Validator"
    ```json
    [
     "9811ebc35d7b06b3fa8dc5809a1f9c52751e1deb"
    ]
    ``` 

Copy the RLP encoded data to the `extraData` in the genesis file. 

### Block Time 

When a new chain head is received, the block time (`blockperiodseconds`) and round timeout (`requesttimeoutseconds`) 
timers are started. When `blockperiodseconds` is reached, a new block is proposed. 

If `requesttimeoutseconds` is reached before the proposed block is added, a round change occurs, and the block time and 
timeout timers are reset. The timeout period for the new round is two times `requesttimeoutseconds`. The 
timeout period continues to double each time a round fails to add a block. 

Generally, the proposed block is added before reaching `requesttimeoutseconds`. A new round is then started, 
and the block time and round timeout timers are reset. When `blockperiodseconds` is reached, the next new block is proposed. 

The time from proposing a block to the block being added is small (around 1s) even in networks
with geographically dispersed validators. Setting `blockperiodseconds` to your desired block time and `requesttimeoutseconds`
to two times `blockperiodseconds` generally results in blocks being added every `blockperiodseconds`. 

!!! example 
    An internal PegaSys IBFT 2.0 testnet has 4 geographically dispersed validators in Sweden, 
    Sydney, and North Virginia (2 validators). With a `blockperiodseconds`of 5 and a `requesttimeoutseconds` of 10,
    the testnet consistently creates block with a 5 second blocktime. 

### Optional Configuration Options 

Optional configuration options that can be specified in the genesis file are:  

* `messageQueueLimit` - Default is 1000. In very large networks with insufficient resources increasing the message queue limit 
   may help to deal with message activity surges.  
   
* `duplicateMesageLimit` - Default is 100. If seeing messages being retransmitted by the same node, increasing the duplicate message limit 
   may reduce the number of retransmissions. A value of 2 to 3 times the number of validators is generally sufficient.  
   
*  `futureMessagesLimit` - Default is 1000. The future messages buffer holds IBFT 2.0 messages for a future chain height.
    For large networks, increasing the future messages limit may be useful. 

*  `futureMessagesMaxDistance` - Default is 10. Specifies the maximum height from the current chain height 
    for which messages are buffered in the future messages buffer. 

## Adding and Removing Validators

To propose adding or removing validators using the JSON-RPC methods, enable the HTTP interface 
using [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) or WebSockets interface using 
[`--rpc-ws-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-enabled). 

The IBFT API methods are not enabled by default. To enable, specify the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) 
or [`--rpc-ws-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) option and include `IBFT`.

The JSON-RPC methods to add or remove validators are:

* [ibft_getPendingVotes](../Reference/Pantheon-API-Methods.md#ibft_getPendingVotes)
* [ibft_proposeValidatorVote](../Reference/Pantheon-API-Methods.md#ibft_proposeValidatorVote)
* [ibft_discardValidatorVote](../Reference/Pantheon-API-Methods.md#ibft_discardValidatorVote)

To propose adding a validator, call `ibft_proposeValidatorVote` specifying the address of the node to be added and `true`.
!!! example "JSON-RPC ibft_proposeValidatorVote Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_proposeValidatorVote","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73", true], "id":1}' <JSON-RPC-endpoint:port>
    ``` 

When the next block is proposed by the validator, one proposal received from `ibft_proposeValidatorVote` is inserted in the block.  
If all proposals have been included in blocks, subsequent blocks proposed by the validator will not contain a vote.

When more than half of the existing validators have published a matching proposal, the proposed validator is added to the validator pool and can begin validating blocks. 

Use `ibft_getValidatorsByBlockNumber` to return a list of the validators and confirm your proposed validator has been added. 

!!! example "JSON-RPC ibft_getValidatorsByBlockNumber Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_getValidatorsByBlockNumber","params":["latest"], "id":1}' <JSON-RPC-endpoint:port>
    ```  
 
To discard your proposal after confirming the validator was added, call `ibft_discardValidatorVote` specifying the address of the proposed validator.

!!! example "JSON-RPC ibft_discardValidatorVote Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"ibft_discardValidatorVote","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"], "id":1}' <JSON-RPC-endpoint:port>
    ```

The process for removing a validator is the same as adding a validator except you specify `false` as the second parameter of `ibft_proposeValidatorVote`. 

### Epoch Transition

At each epoch transition, all pending votes collected from received blocks are discarded. Existing proposals remain 
in effect and validators re-add their vote the next time they create a block. 

An epoch transition occurs every `epochLength` blocks where `epochlength` is defined in the IBFT genesis file.


