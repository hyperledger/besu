description: Using Pantheon with Truffle
<!--- END of page meta data -->

# Using Pantheon with Truffle 

Developing for Pantheon using Truffle is the same as using Truffle to develop for the public Ethereum networks. 
Truffle supports Pantheon with the only difference being Pantheon does not implement private key management. 
To use Pantheon with Truffle, you must configure a Truffle wallet.

## Install Truffle Wallet 

To install the Truffle wallet:

```bash
npm install --save truffle-hdwallet-provider@web3-one
```

!!!note
    With Truffle 5, you must use a Web3 1.0 enabled wallet or the Truffle tasks hang.

#### Modify the Truffle Configuration File 

Modify the `truffle-config.js` file in the project directory to add the wallet provider. Replace: 

* `<JSON-RPC-http-endpoint>` with the JSON-RPC endpoint (IP address and port) of a Pantheon node
*  `<account-private-key>` with the private key of an Ethereum account containing Ether 

```javascript
const PrivateKeyProvider = require("truffle-hdwallet-provider");
const privateKey = "<account-private-key>";
const privateKeyProvider = new PrivateKeyProvider(privateKey, "<JSON-RPC-http-endpoint>");

module.exports = {
  // See <http://truffleframework.com/docs/advanced/configuration>
  // for more about customizing your Truffle configuration!
  networks: {
    pantheonWallet: {
      provider: privateKeyProvider,
      network_id: "*"
    },
  }
};
```

### Start Pantheon Node 

Start a Pantheon node with JSON-RPC enabled on the endpoint specified in the Truffle configuration 
file.  

### Deploy Contract 

To deploy a contract onto the Pantheon network: 

```bash
truffle migrate --network pantheonWallet
```

