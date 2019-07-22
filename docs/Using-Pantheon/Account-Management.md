description: Using third party wallets for account management
<!--- END of page meta data -->

# Using Wallets for Key Management

Pantheon doesn't support key management inside the client. Use:

* [EthSigner](http://docs.ethsigner.pegasys.tech/en/latest/) with Pantheon to provide access to your key store and sign transactions.  

* Third-party tools (for example, [MetaMask](https://metamask.io/) and [web3j](https://web3j.io/)) for creating accounts. 

In Pantheon, you can use the JSON-RPC methods:

 * [`eth_getBalance`](../Reference/Pantheon-API-Methods.md#eth_getbalance) to obtain the account balance
 * [`eth_sendRawTransaction`](../Reference/Pantheon-API-Methods.md#eth_sendrawtransaction) to transfer ether or create and interact with contracts (for more information, refer to [Transactions](Transactions/Transactions.md#transactions)).  
 * [`eea_sendRawTransaction`](../Reference/Pantheon-API-Methods.md#eea_sendrawtransaction) to send [private transactions](../Privacy/How-To/Creating-Sending-Private-Transactions.md).
 
!!! tip 
    [EthSigner](http://docs.ethsigner.pegasys.tech/en/latest/) implements [`eth_sendTransaction`](http://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner/Using-EthSigner/#eth_sendtransaction)
    and [`eea_sendTransaction`](http://docs.ethsigner.pegasys.tech/en/latest/Using-EthSigner/Using-EthSigner/#eea_sendtransaction).

