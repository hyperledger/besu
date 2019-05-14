# Accessing Logs Using Pantheon API

Access logs using Pantheon API methods:
 
* [`eth_getFilterChanges`](../Reference/Pantheon-API-Methods.md#eth_getfilterchanges)
* [`eth_getFilterLogs`](../Reference/Pantheon-API-Methods.md#eth_getfilterlogs)
* [`eth_getLogs`](../Reference/Pantheon-API-Methods.md#eth_getlogs)

Use [`eth_newFilter`](../Reference/Pantheon-API-Methods.md#eth_newfilter) to create the filter before
using [`eth_getFilterChanges`](../Reference/Pantheon-API-Methods.md#eth_getfilterchanges) and [`eth_getFilterLogs`](../Reference/Pantheon-API-Methods.md#eth_getfilterlogs)). 

!!! note
    The following examples are created using the sample contract included in [Events and Logs](Events-and-Logs.md). 

## Creating a Filter

Create a filter using [`eth_newFilter`](../Reference/Pantheon-API-Methods.md#eth_newfilter). 

!!! example
    
    If the [example contract](Events-and-Logs.md#example) was deployed to 0x42699a7612a82f1d9c36148af9c77354759b210b, the 
    following request for `eth_newFilter` creates a filter to log when `valueIndexed` is set to 5: 
    
    ```json
    {
     "jsonrpc":"2.0",
     "method":"eth_newFilter",
     "params":[
        {
          "fromBlock":"earliest", 
          "toBlock":"latest", 
          "address":"0x42699a7612a82f1d9c36148af9c77354759b210b", 
          "topics":[
              ["0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8"], 
              ["0x0000000000000000000000000000000000000000000000000000000000000005"]
          ]
         }
      ],
      "id":1
    }
    ```
        
[`eth_newFilter`](../Reference/Pantheon-API-Methods.md#eth_newfilter) returns a filter ID hash (for example, `0x1ddf0c00989044e9b41cc0ae40272df3`). 

### Polling Filter for Changes

To poll the filter for changes that have occurred since the last poll, use [`eth_getFilterChanges`](../Reference/Pantheon-API-Methods.md#eth_getfilterchanges)
with the filter ID hash returned by [`eth_newFilter`](../Reference/Pantheon-API-Methods.md#eth_newfilter). 

!!! example 
    
    If the contract had been executed twice since the last poll, with `valueIndexed` set to 1 and 5, 
    [`eth_getFilterChanges`](../Reference/Pantheon-API-Methods.md#eth_getfilterchanges) returns
    only the log where the [topic](Events-and-Logs.md#event-parameters) for `valueIndexed` is 5: 
    
    ```json
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "logIndex": "0x0",
                "removed": false,
                "blockNumber": "0x21c",
                "blockHash": "0xc7e6c9d5b9f522b2c9d2991546be0a8737e587beb6628c056f3c327a44b45132",
                "transactionHash": "0xfd1a40f9fbf89c97b4545ec9db774c85e51dd8a3545f969418a22f9cb79417c5",
                "transactionIndex": "0x0",
                "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
                "data": "0x0000000000000000000000000000000000000000000000000000000000000005",
                "topics": [
                    "0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8",
                    "0x0000000000000000000000000000000000000000000000000000000000000005"
                ]
            }
        ]
    }
    ```

### Getting All Logs for a Filter

To get all logs for a filter, use [`eth_getFilterLogs`](../Reference/Pantheon-API-Methods.md#eth_getfilterlogs). 

!!! example
    
    If the contract had been executed twice with `valueIndexed` set to 5 since the filter was created using `eth_newFilter`,
    `eth_getFilterLogs` returns: 
    
    ```json
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
            {
                "logIndex": "0x0",
                "removed": false,
                "blockNumber": "0x1a7",
                "blockHash": "0x4edda22a242ddc7bc51e2b6b11e63cd67be1af7389470cdea9c869768ff75d42",
                "transactionHash": "0x9535bf8830a72ca7d0020df0b547adc4d0ecc4321b7d5b5d6beb1eccee5c0afa",
                "transactionIndex": "0x0",
                "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
                "data": "0x0000000000000000000000000000000000000000000000000000000000000005",
                "topics": [
                    "0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8",
                    "0x0000000000000000000000000000000000000000000000000000000000000005"
                ]
            },
            {
                "logIndex": "0x0",
                "removed": false,
                "blockNumber": "0x21c",
                "blockHash": "0xc7e6c9d5b9f522b2c9d2991546be0a8737e587beb6628c056f3c327a44b45132",
                "transactionHash": "0xfd1a40f9fbf89c97b4545ec9db774c85e51dd8a3545f969418a22f9cb79417c5",
                "transactionIndex": "0x0",
                "address": "0x42699a7612a82f1d9c36148af9c77354759b210b",
                "data": "0x0000000000000000000000000000000000000000000000000000000000000005",
                "topics": [
                    "0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8",
                    "0x0000000000000000000000000000000000000000000000000000000000000005"
                ]
            }
        ]
    }
    ```
    
!!! tip 
    You can use [`eth_getLogs`](#getting-logs-using-a-filter-options-object) with a filter options object 
    to get all logs matching the filter options instead of using [`eth_newFilter`](../Reference/Pantheon-API-Methods.md#eth_newfilter)
    followed by [`eth_getFilterLogs`](../Reference/Pantheon-API-Methods.md#eth_getfilterlogs). 
    
## Uninstalling a Filter

When you are finished using a filter, use [`eth_uninstallFilter`](../Reference/Pantheon-API-Methods.md#eth_uninstallfilter) to remove the filter.     
    
## Getting Logs Using a Filter Options Object 

To get all logs for a filter options object, use [`eth_getLogs`](../Reference/Pantheon-API-Methods.md#eth_getlogs).   

!!! example 

    The following request for `eth_getLogs` returns all the logs where the example contract has been 
    deployed to 0x42699a7612a82f1d9c36148af9c77354759b210b and executed with `valueIndexed` set to 5.
    
    ```json
    {
      "jsonrpc":"2.0",
      "method":"eth_getLogs",
      "params":[
        {
          "fromBlock":"earliest", 
          "toBlock":"latest", 
          "address":"0x42699a7612a82f1d9c36148af9c77354759b210b", 
          "topics":[
            ["0xd3610b1c54575b7f4f0dc03d210b8ac55624ae007679b7a928a4f25a709331a8"], 
            ["0x0000000000000000000000000000000000000000000000000000000000000005"]
          ]
    	  }
      ], 
      "id":1
    }
    ``` 
    
    This returns the same result as calling [eth_newFilter](#creating-a-fitler) followed by [eth_getFilterLogs](#getting-all-logs-for-a-filter). 

