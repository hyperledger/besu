description: Pantheon Clique Proof-of-Authority (PoA) consensus protocol implementation
path: blob/master/config/src/main/resources/
source: rinkeby.json
<!--- END of page meta data -->

*[vanity data]: Signers can include anything they like as vanity data.

# Clique

Pantheon implements the Clique Proof-of-Authority (PoA) consensus protocol. Clique is used by the
Rinkeby testnet and can be used for private networks. 

In Clique networks, transactions and blocks are validated by approved accounts, known as signers.
Signers take turns to create the next block. Existing signers propose and vote to add or remove signers. 

## Genesis File

To use Clique in a private network, Pantheon requires a Clique genesis file. When connecting to Rinkeby,
Pantheon uses the [`rinkeby.json`](https://github.com/PegaSysEng/pantheon/blob/master/config/src/main/resources/rinkeby.json) 
genesis file in the `/pantheon/config/src/main/resources` directory.

A PoA genesis file defines properties specific to Clique:

!!! example "Genesis JSON file (stripped)"
    ```json
    {
      "config": {
        ....
        "clique": {
          "blockperiodseconds": 15,
          "epochlength": 30000
        }
      },
      ...
      "extraData": "0x0000000000000000000000000000000000000000000000000000000000000000dd37f65db31c107f773e82a4f85c693058fef7a90000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      ...
    }
    ```
    
The properties specific to Clique are:

* `blockperiodseconds` - Block time in seconds. 
* `epochlength` - Number of blocks after which to reset all votes.
* `extraData` - Initial signers are specified after the 32 bytes reserved for vanity data. 

To connect to the Rinkeby testnet, start Pantheon with the [`--network=rinkeby`](../Reference/Pantheon-CLI-Syntax.md#network)
command line option. To start a node on a Clique private network, use the 
[`--genesis-file`](../Reference/Pantheon-CLI-Syntax.md#genesis-file) option to specify the custom genesis file. 

## Adding and Removing Signers

To propose adding or removing signers using the JSON-RPC methods, enable the HTTP interface 
using [`--rpc-http-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-enabled) or WebSockets interface using 
[`--rpc-ws-enabled`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-enabled). 

The Clique API methods are not enabled by default. To enable, specify the [`--rpc-http-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) 
or [`--rpc-ws-api`](../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) option and include `CLIQUE`.

The JSON-RPC methods to add or remove signers are:

* [clique_propose](../Reference/JSON-RPC-API-Methods.md#clique_propose)
* [clique_getSigners](../Reference/JSON-RPC-API-Methods.md#clique_getsigners)
* [clique_discard](../Reference/JSON-RPC-API-Methods.md#clique_discard)

To propose adding a signer, call `clique_propose` specifying the address of the proposed signer and `true`.
!!! example "JSON-RPC clique_propose Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_propose","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73", true], "id":1}' <JSON-RPC-endpoint:port>
    ``` 

When the next block is created by the signer, a vote is added to the block for the proposed signer.  

When more than half of the existing signers propose adding the signer and their votes have been
distributed in blocks, the signer is added and can begin signing blocks. 

Use `clique_getSigners` to return a list of the signers and to confirm that your proposed signer has
been added. 
!!! example "JSON-RPC clique_getSigners Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_getSigners","params":["latest"], "id":1}' <JSON-RPC-endpoint:port>
    ```  
 
To discard your proposal after confirming the signer was added, call `clique_discard` specifying the address of the proposed signer.
!!! example "JSON-RPC clique_discard Request Example"
    ```bash
    curl -X POST --data '{"jsonrpc":"2.0","method":"clique_discard","params":["0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"], "id":1}' <JSON-RPC-endpoint:port>
    ```

The process for removing a signer is the same as adding a signer except you specify `false` as the 
second parameter of `clique_propose`. 

### Epoch Transition

At each epoch transition, all pending votes collected from received blocks are discarded. 
Existing proposals remain in effect and signers re-add their vote the next time they create a block. 

Define the number of blocks between epoch transitions in the genesis file. 

