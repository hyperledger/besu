description: Creating and sending private transactions
<!--- END of page meta data -->

# Creating and Sending Private Transactions 

Create and send private transactions using: 

* [web3.js-eea client library](eeajs.md) or [web3j client library](https://github.com/web3j/web3j)
* [`eea_sendTransaction` with EthSigner](https://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner/Using-EthSigner/) 
* [`eea_sendRawTransaction`](../../Reference/Pantheon-API-Methods.md#eea_sendrawtransaction) 

!!! note
    Private transactions either deploy contracts or call contract functions. 
    Ether transfer transactions cannot be private. 
    
## Methods for Private Transactions

A private transaction creates a [Privacy Marker Transaction](../Explanation/Private-Transaction-Processing.md) in addition to the private transaction itself. 
Use [`eth_getTransactionReceipt`](../../Reference/Pantheon-API-Methods.md#eth_gettransactionreceipt) to 
get the transaction receipt for the Privacy Maker Transaction and [`eea_getTransactionReceipt`](../../Reference/Pantheon-API-Methods.md#eea_gettransactionreceipt) 
to get the transaction receipt for the private transaction. 

Use [`eth_getTransactionByHash`](../../Reference/Pantheon-API-Methods.md#eth_gettransactionbyhash) to 
get the Privacy Marker Transaction with the transaction hash returned when submitting the private transaction. 
Use [`priv_getPrivateTransaction`](../../Reference/Pantheon-API-Methods.md#priv_getprivatetransaction) 
to get the private transaction with the `input` value from the Privacy Marker Transaction. 

Separate private states are maintained for each [privacy group](../Explanation/Privacy-Overview.md#privacy-groups) so 
the account nonce for an account is specific to the privacy group. That is, the nonce for account A for
privacy group ABC is different to the account nonce for account A for privacy group AB. Use 
[`priv_getTransactionCount`](../../Reference/Pantheon-API-Methods.md#priv_getTransactionCount) to get 
the account nonce for an account for the specified privacy group. 

!!! note
    If sending a large number of private transactions, you may need to calculate the nonce for the account 
    and privacy group outside the client. 