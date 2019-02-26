description: Pantheon IBFT 2.0 Proof-of-Authority (PoA) consensus protocol implementation
<!--- END of page meta data -->

*[Vanity]: Validators can include anything they like as vanity data. 
*[RLP]: Recursive Length Prefix

# IBFT 2.0

Pantheon implements the IBFT 2.0 Proof-of-Authority (PoA) consensus protocol. IBFT 2.0 can be used for private networks. 

In IBFT 2.0 networks, transactions and blocks are validated by approved accounts, known as validators. 
Validators take turns to create the next block. Existing validators propose and vote to add or remove validators. 

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

The `extraData` property is RLP encoded. RLP encoding is a space efficient object 
serialization scheme used in Ethereum. You can use a library such as [EthereumJS RLP](https://github.com/ethereumjs/rlp)
to encode and decode RLP strings. 

!!! example "Decoding Extra Data Example"
    
    Using the [EthereumJS RLP](https://github.com/ethereumjs/rlp) library: 
    ```bash
    rlp decode "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0"
    ```
    
    The decoded result is: 
    ```json
    [ '0000000000000000000000000000000000000000000000000000000000000000',
      [ 'be068f726a13c8d46c44be6ce9d275600e1735a4',
        '5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193' ],
      '',
      '00000000',
      [] 
    ]
    ```
    
    

Properties that have specific values in IBFT 2.0 genesis files are: 

* `nonce` - `0x0`
* `difficulty` - `0x1`
* `mixHash` - `0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365` for Istanbul block identification.

To start a node on an IBFT 2.0 private network, use the [`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file`) option to specify the custom genesis file. 

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

* [ibft_getPendingVotes](../Reference/JSON-RPC-API-Methods.md#ibft_getPendingVotes)
* [ibft_proposeValidatorVote](../Reference/JSON-RPC-API-Methods.md#ibft_proposeValidatorVote)
* [ibft_discardValidatorVote](../Reference/JSON-RPC-API-Methods.md#ibft_discardValidatorVote)

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


