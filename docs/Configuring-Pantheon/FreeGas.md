description: Configuring Free Gas Networks 
<!--- END of page meta data -->

# Free Gas Networks 

Transactions have an associated cost. Gas is the cost unit and the gas price is the price per gas unit. 
The transaction cost is the gas used * gas price. 

In public networks, the transaction cost is paid in Ether by the account submitting the transaction.
The transaction cost is paid to the miner (or validator in PoA networks) that includes the transaction in a block.  

In many private networks, the validators are run by the network participants and do not require gas as an 
incentive to participate.  Generally, networks that do not require gas as an incentive, configure the gas price to be 0 (that is, make gas free). 
Some private networks may allocate Ether and use a non-zero gas price to limit resource use.  

!!! tip
    We are using the term _free gas network_ to refer to a network where the gas price is set to zero. 
    A network with gas price of zero is also known as a _zero gas network_ or _no gas network_. 

In a free gas network, transactions still use gas but the gas price is 0 meaning the transaction cost is 0:

Transaction cost = gas used * 0 (gas price)    

## Configuring Pantheon for Free Gas 

When gas is free, limiting block and contract sizes is less important. In free gas networks, we set 
block and contract size limits to the maximum values.   

### 1. Set Block Size 

Set the block size limit (measured in gas) to the maximum accepted by Truffle (`0x1fffffffffffff`) in the genesis file: 

```json
"gasLimit": "0x1fffffffffffff"
```

### 2. Set Contract Size 

Set the contract size limit to the maximum supported size (in bytes) in the `config` section of the genesis file:

```json
"contractSizeLimit": 2147483647
```

### 3. Start Pantheon with Minimum Gas Price of 0 

When starting validators (mining nodes in a PoW network), set the [minimum gas price](../Reference/Pantheon-CLI-Syntax.md#min-gas-price) to 0: 

```bash tab="Command Line"
--min-gas-price=0
```

```bash tab="Configuration File"
min-gas-price=0
```

## Configuring Truffle for Free Gas 

If using Truffle to develop on your free gas network, you also need to configure Truffle for free gas.

Similar to setting block and contract size limits to their maximum values for Pantheon, we set the 
gas limit for transactions in Truffle to the maximum possible. 

!!! important
    Pantheon does not implement private key management. To use Pantheon with Truffle, you must configure 
    a [Truffle wallet](../Using-Pantheon/Truffle.md).


### Update truffle-config.js

Update the `truffle-config.js` file: 

1. Set the gas price to 0: 

    ```js
    gasPrice:0
    ```

1. Set the gas limit for a transaction (that is, contract creation) to be the block gas limit - 1

    ```js
    gas: "0x1ffffffffffffe"
    ``` 
