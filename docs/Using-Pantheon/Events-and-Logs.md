# Events and Logs

When a transaction is mined, smart contracts emit events and write logs to the blockchain.  

Logs are associated with the contract address and included in the blockchain but logs are not accessible 
from within contracts. Log storage is cheaper than contract storage (that is, it costs less gas) so if the required data can 
be stored in and accessed from logs, the cost is reduced. For example, you can use logs to display all 
transfers made using a specific contract but not the current state of the contract. 

A Dapp front end can either request logs using the [JSON-RPC API filter methods](Accessing-Logs-Using-JSON-RPC.md) 
or subscribe to logs using the [RPC Pub/Sub API](../Pantheon-API/RPC-PubSub.md#logs). 

## Topics 

Log entries contain up to four topics. The first topic is the [event signature hash](#event-signature-hash) and up to three topics 
are the indexed [event parameters](#event-parameters). 

!!! example 
    Log entry for an event with one indexed parameter: 

    ```json
    {
      "logIndex": "0x0",
      "removed": false,
      "blockNumber": "0x84",
      "blockHash": "0x5fc573d76ec48ec80cbc43f299ebc306a8168112e3a4485c23e84e9a40f5d336",
      "transactionHash": "0xcb52f02342c2498df82c49ac26b2e91e182155c8b2a2add5b6dc4c249511f85a",
      "transactionIndex": "0x0",
      "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
      "data": "0x",
      "topics": [
        "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3",
        "0x0000000000000000000000000000000000000000000000000000000000000001"
      ]
    }
    ```

## Event Parameters

Up to three event parameters can have the `indexed` attribute. Indexed parameters are stored as `topics` 
in the logs. Indexed parameters can be searched and filtered.

Topics are 32 bytes. If an indexed argument is an array (including `string` and `byte` datatypes), 
the keccak-256 hash of the paramater is stored as a topic. 

Non-indexed parameters are included in the logs `data` but cannot be easily searched or filtered. 

!!! example 

    A Solidity contract that stores one indexed and one non-indexed parameter and has an event that emits the value of each parameter: 

    ```solidity
    pragma solidity ^0.5.1;
    contract Storage {
	  uint256 public valueIndexed;
	  uint256 public valueNotIndexed;

	  event Event1(uint256 indexed valueIndexed, uint256 valueNotIndexed);

	  function setValue(uint256 _valueIndexed, uint256 _valueNotIndexed) public {
    	 valueIndexed = _valueIndexed;
    	 valueNotIndexed = _valueNotIndexed;
    	 emit Event1(_valueIndexed, _valueNotIndexed); 
	  }
    }
    ```

!!! example   
    Log entry created by invoking the contract above with `valueIndexed` set to 5 and `valueNotIndexed` set to 7:  

    ```json
     {
       "logIndex": "0x0",
       "removed": false,
       "blockNumber": "0x4d6",
       "blockHash": "0x7d0ac7c12ac9f622d346d444c7e0fa4dda8d4ed90de80d6a28814613a4884a67",
       "transactionHash": "0xe994022ada94371ace00c4e1e20663a01437846ced02f18b3f3afec827002781",
       "transactionIndex": "0x0",
       "address": "0x43d1f9096674b5722d359b6402381816d5b22f28",
       "data": "0x0000000000000000000000000000000000000000000000000000000000000007",
       "topics": [
        "0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8",
        "0x0000000000000000000000000000000000000000000000000000000000000005"
       ]
     }
    ```

## Event Signature Hash

The first topic in a log entry is always the the event signature hash. The event signature hash is a keccak-256
hash of the event name and input argument types. Argument names are ignored. For example, the event `Hello(uint256 worldId)` 
has the signature hash `keccak('Hello(uint256)')`. The signature identifies to which event log topics belong. 

!!! example
    
    A Solidity contract with two different events: 

    ``` solidity	
	     pragma solidity ^0.5.1;
         contract Storage {
  	     uint256 public valueA;
         uint256 public valueB;
  
  	     event Event1(uint256 indexed valueA);
  	     event Event2(uint256 indexed valueB);
  
  	     function setValue(uint256 _valueA) public {
      	    valueA = _valueA;
      	    emit Event1(_valueA); 
  	     }
  	
  	     function setValueAgain(uint256 _valueB) public {
      	    valueB = _valueB;
      	    emit Event2(_valueB); 
  	     }
       } 
    ```

The event signature hash for event 1 is `keccak('Event1(uint256)')` and the event signature hash for event 
2 is `keccak('Event2(uint256)')`. The hashes are: 

* `04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3` for event 1  
* `06df6fb2d6d0b17a870decb858cc46bf7b69142ab7b9318f7603ed3fd4ad240e` for event 2

!!! tip
    You can use a library keccak (sha3) hash function such as provided in [Web3.js](https://github.com/ethereum/wiki/wiki/JavaScript-API#web3sha3)
    or an an online tool such as https://emn178.github.io/online-tools/keccak_256.html to generate event signature hashes. 

!!! example
    Log entries from invoking the Solidity contract above:  

    ```json
    [
        {
            "logIndex": "0x0",
            "removed": false,
            "blockNumber": "0x84",
            "blockHash": "0x5fc573d76ec48ec80cbc43f299ebc306a8168112e3a4485c23e84e9a40f5d336",
            "transactionHash": "0xcb52f02342c2498df82c49ac26b2e91e182155c8b2a2add5b6dc4c249511f85a",
            "transactionIndex": "0x0",
            "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
            "data": "0x",
            "topics": [
                "0x04474795f5b996ff80cb47c148d4c5ccdbe09ef27551820caa9c2f8ed149cce3",
                "0x0000000000000000000000000000000000000000000000000000000000000001"
            ]
        },
        {
            "logIndex": "0x0",
            "removed": false,
            "blockNumber": "0x87",
            "blockHash": "0x6643a1e58ad857f727552e4572b837a85b3ca64c4799d085170c707e4dad5255",
            "transactionHash": "0xa95295fcea7df3b9e47ab95d2dadeb868145719ed9cc0e6c757c8a174e1fcb11",
            "transactionIndex": "0x0",
            "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
            "data": "0x",
            "topics": [
                "0x06df6fb2d6d0b17a870decb858cc46bf7b69142ab7b9318f7603ed3fd4ad240e",
                "0x0000000000000000000000000000000000000000000000000000000000000002"
            ]
        }
    ]
    ```

## Topic Filters

[Filter options objects](../Reference/Pantheon-API-Objects.md#filter-options-object) have a `topics` key to filter logs by topics. 

Topics are order-dependent. A transaction with a log containing topics `[A, B]` is matched with the following topic filters:

* `[]` - Match any topic
* `[A]` - Match A in first position 
* `[[null], [B]]` - Match any topic in first position AND B in second position
* `[[A],[B]]` - Match A in first position AND B in second position
* `[[A, C], [B, D]]` - Match (A OR C) in first position AND (B OR D) in second position 



!!! example
    The following filter option object returns log entries for the [Event Parameters example contract](#event-parameters) where `valueIndexed` is set to 
    5 or 9: 

    ```json
    {
      "fromBlock":"earliest", 
      "toBlock":"latest", 
      "address":"0x43d1f9096674b5722d359b6402381816d5b22f28",
      "topics":[
       ["0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8"], 
       ["0x0000000000000000000000000000000000000000000000000000000000000005", "0x0000000000000000000000000000000000000000000000000000000000000009"]
      ]
    }
    ```


