description: Some use cases of creating transactions on a Pantheon network
<!--- END of page meta data -->

# Creating and Sending Transactions

You can send signed transactions using the [`eth_sendRawTransaction`](../../Reference/JSON-RPC-API-Methods.md#eth_sendrawtransaction) JSON-RPC API method.

These examples describe how to create a signed raw transaction that can be passed to [`eth_sendRawTransaction`](../../Reference/JSON-RPC-API-Methods.md#eth_sendrawtransaction).

!!!tip
    To avoid exposing your private keys, create signed transactions offline.

The examples use the following libraries to create signed transactions:

* [web3.js](https://github.com/ethereum/web3.js/)
* [ethereumjs](https://github.com/ethereumjs/ethereumjs-tx)
                     
!!!info
    Other libraries (such as [web3j](https://github.com/web3j/web3j) or [ethereumj](https://github.com/ethereum/ethereumj)) 
    and tools (such as [MyEtherWallet](https://kb.myetherwallet.com/offline/making-offline-transaction-on-myetherwallet.html) 
    or [MyCrypto](https://mycrypto.com/)) can also be used to create signed transactions. 

Example Javascript scripts are provided to create signed raw transaction strings to:
 
* [Send ether](#sending-ether)
* [Deploy a contract](#deploying-a-contract)

!!!attention
    [Node.js](https://nodejs.org/en/download/) must be installed to run these Javascript scripts. 

You can use the example Javascript scripts to create and send raw transactions in the private network created by the 
[Private Network Quickstart](../../Tutorials/Private-Network-Quickstart.md).

You must update the `JSON-RPC endpoint` in the examples to the endpoint for the private network displayed after running 
the `run.sh` script.

To create and display the transaction string, run the Javascript script.

```bash
$ node create_signed_raw_transaction.js
```

To send a signed transaction, run:
```bash
$ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["raw_transaction_string"],"id":1}' <JSON-RPC-endpoint:port>
```

Where:

* `raw_transaction_string` is the signed raw transaction string displayed by the JS script. 
* `<JSON-RPC-endpoint:port>` is the JSON-RPC endpoint.

!!!example
    ```bash
    $ curl -X POST --data '{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["0xf86a808203e882520894f17f52151ebef6c7334fad080c5704d77216b732896c6b935b8bbd400000801ca08ce4a6c12f7f273321c5dc03910744f8fb11573fcce8140aa44486d385d22fb3a051f6bcc918bf3f12e06bfccfd1451bea5c517dffee0777ebd50caf177b17f383"],"id":1}' http://localhost:8545
    ```

All accounts and private keys in the examples are from the `dev.json` genesis file in the `/pantheon/ethereum/core/src/main/resources` directory.

## Sending Ether
 
!!!example

    The following is an example of JavaScript that displays a signed transaction string to send ether.
    
    ```javascript linenums="1"
    const web3 = require('web3')
    const ethTx = require('ethereumjs-tx')
    
    // web3 initialization - must point to the HTTP JSON-RPC endpoint
    const web3 = new Web3(new Web3.providers.HttpProvider('http://127.0.0.1:8545'))
    
    // Sender address and private key
    // Second acccount in dev.json genesis file
    // Exclude 0x at the beginning of the private key
    const addressFrom = '0x627306090abaB3A6e1400e9345bC60c78a8BEf57'
    const privKey = Buffer.from('c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3', 'hex')
    
    // Receiver address and value to transfer
    // Third account in dev.json genesis file
    const addressTo = '0xf17f52151EbEF6C7334FAD080c5704D77216b732'
    const valueInEther = 2000
    
    // Get the address transaction count in order to specify the correct nonce
    txnCount = web3.eth.getTransactionCount(addressFrom, "pending");
    
    // Create the transaction object
    var txObject = {
        nonce: web3.toHex(txnCount),
        gasPrice: web3.toHex(1000),
        gasLimit: web3.toHex(21000),
        to: addressTo,
        value: web3.toHex(web3.toWei(valueInEther, 'ether'))
    };
    
    // Sign the transaction with the private key
    const tx = new ethTx(txObject);
    tx.sign(privKey)
    
    //Convert to raw transaction string
    const serializedTx = tx.serialize();
    const rawTxHex = '0x' + serializedTx.toString('hex');
    
    console.log("Raw transaction string=" + rawTxHex)
    ```

## Deploying a Contract

!!!example
    The following is an example of JavaScript that displays a signed raw transaction string to deploy a contract.
    
    ```javascript linenums="1"
    const web3 = require('web3')
    const ethTx = require('ethereumjs-tx')
    
    // web3 initialization - must point to the HTTP JSON-RPC endpoint
    const web3 = new Web3(new Web3.providers.HttpProvider('http://127.0.0.1:8545'))
    
    // Deployer address and private key
    // First account in the dev.json genesis file
    const addressFrom = '0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73'
    const privKey = Buffer.from('8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63', 'hex')
    
    // Compiled contract hash - can obtain from Remix by clicking the Details button in the Compile tab. 
    // Compiled contract hash is value of data parameter in the WEB3DEPLOY section
    const contractData = '0x608060405234801561001057600080fd5b5060dc8061001f6000396000f3006080604052600436106049576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633fa4f24514604e57806355241077146076575b600080fd5b348015605957600080fd5b50606060a0565b6040518082815260200191505060405180910390f35b348015608157600080fd5b50609e6004803603810190808035906020019092919050505060a6565b005b60005481565b80600081905550505600a165627a7a723058202bdbba2e694dba8fff33d9d0976df580f57bff0a40e25a46c398f8063b4c00360029'
    
    // Get the address transaction count in order to specify the correct nonce
    txnCount = web3.eth.getTransactionCount(addressFrom, "pending");
    
    var txObject = {
       nonce: web3.toHex(txnCount),
       gasPrice: web3.toHex(1000),
       gasLimit: web3.toHex(126165),
       data: contractData
    };
    
    const tx = new ethTx(txObject);
    tx.sign(privKey)
    
    const serializedTx = tx.serialize();
    const rawTxHex = '0x' + serializedTx.toString('hex');
    
    console.log("Raw transaction string=" + rawTxHex); 
    ```

## eth_call or eth_sendRawTransaction

You can interact with contracts using [eth_call](../../Reference/JSON-RPC-API-Methods.md#eth_call) or [eth_sendRawTransaction](../../Reference/JSON-RPC-API-Methods.md#eth_sendrawtransaction). 

| eth_call                                                | eth_sendRawTransaction                                                                                                         |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Read-only                                               | Write                                                                                                                          |
| Invokes contract function locally                       | Broadcasts to network                                                                                                          |
| Does not change state of blockchain                     | Updates blockchain (for example, transfers ether between accounts)                                                             |
| Does not consume gas                                    | Requires gas                                                                                                                   |
| Synchronous                                             | Asynchronous                                                                                                                   |
| Return value of contract function available immediately | Returns transaction hash only.  Possible transaction may not be included in a block (for example, if the gas price is too low) |
