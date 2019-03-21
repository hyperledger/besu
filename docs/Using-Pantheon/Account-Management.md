description: Using third party wallets for account management
<!--- END of page meta data -->

# Using Wallets for Account Management

Pantheon does not implement private key management. Use third-party tools (for example, [MetaMask](https://metamask.io/) and [web3j](https://web3j.io/)) for creating accounts. 

In Pantheon, you can use the JSON-RPC methods:

 * [eth_getBalance](../Reference/JSON-RPC-API-Methods.md#eth_getbalance) to obtain the account balance
 * [eth_sendRawTransaction](../Reference/JSON-RPC-API-Methods.md#eth_sendrawtransaction) to transfer ether or create and interact with contracts (for more information, refer to [Transactions](Transactions/Transactions.md#transactions)).  