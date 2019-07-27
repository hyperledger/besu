description: Including revert reason in transactions 
<!--- END of page meta data -->

# Revert Reason 

In smart contracts, the [`revert`](https://solidity.readthedocs.io/en/v0.5.10/control-structures.html#revert) operation 
triggers an exception to flag an error and revert the current call. An optional string message containing 
information about the error is passed back to the client from the EVM.

!!! example
    ```sol
    pragma solidity >=0.5.0 <0.7.0;
    
    contract VendingMachine {
        function buy(uint amount) public payable {
            if (amount > msg.value / 2 ether)
                revert("Not enough Ether provided");
            // Alternative way to do it:
            require(
                amount <= msg.value / 2 ether,
                "Not enough Ether provided."
            );
            // Perform the purchase.
        }
    }
    ```

## Enabling Revert Reason 

Use the [`--revert-reason-enabled`](../../Reference/Pantheon-CLI-Syntax.md#revert-reason-enabled) command line option
to include the revert reason in the transaction receipt in Pantheon. 

!!! caution 
    Enabling revert reason may use a significant amount of memory. We do not recommend enabling revert
    reason when connected to public Ethereum networks. 

## Where is the Revert Reason Included 

When revert reason is enabled, the revert reason is included as an ABI-encoded string in the transaction receipt returned by 
[`eth_getTransactionReceipt`](../../Reference/Pantheon-API-Methods.md#eth_gettransactionreceipt). 

!!! important 
    The revert reason is not included in the transactions receipts root hash. Not being included in the 
    transactions receipts root hash means the revert reason is only available to nodes that execute the 
    transaction when importing the block. That is, the revert reason is not available if using fast sync. 

!!! example 
    ```
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
         "transactionIndex": "0x0",
         "revertReason":"0x08c379a00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001a4e6f7420656e6f7567682045746865722070726f76696465642e000000000000"
       }
    }
    ```
    
## Revert Reason Format
    
As described in the [Solidity documentation](https://solidity.readthedocs.io/en/v0.5.10/control-structures.html#revert),
the revert reason is included as an ABI-encoded string consisting of: 

```
0x08c379a0                                                         // Function selector for Error(string)
0x0000000000000000000000000000000000000000000000000000000000000020 // Data offset
0x000000000000000000000000000000000000000000000000000000000000001a // String length
0x4e6f7420656e6f7567682045746865722070726f76696465642e000000000000 // String data
```

!!! example
    ```bash tab="Revert reason string for Not enough Ether provided."
    "0x08c379a00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001a4e6f7420656e6f7567682045746865722070726f76696465642e000000000000"
    ```

## Dapp Support 

Client libraries (eg, web3j) do not support extracting the revert reason from the transaction receipt. 
To extract the revert reason your Dapp must interact directly with Pantheon using a custom JSON -> Object 
converter. 

