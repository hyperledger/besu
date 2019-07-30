description: Methods for accessing and managing private transactions and privacy groups
<!--- END of page meta data -->

# Accessing Private and Privacy Marker Transactions 

A private transaction creates a [Privacy Marker Transaction](../Explanation/Private-Transaction-Processing.md) in addition to the private transaction itself. 
Use [`eth_getTransactionReceipt`](../../Reference/Pantheon-API-Methods.md#eth_gettransactionreceipt) to 
get the transaction receipt for the Privacy Maker Transaction and [`eea_getTransactionReceipt`](../../Reference/Pantheon-API-Methods.md#eea_gettransactionreceipt) 
to get the transaction receipt for the private transaction. 

With the transaction hash returned when submitting the private transaction, use: 

* [`eth_getTransactionByHash`](../../Reference/Pantheon-API-Methods.md#eth_gettransactionbyhash) to 
get the Privacy Marker Transaction . 
* [`priv_getPrivateTransaction`](../../Reference/Pantheon-API-Methods.md#priv_getprivatetransaction) 
to get the private transaction. 
