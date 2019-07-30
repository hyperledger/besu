description: web3js-eea methods reference
<!--- END of page meta data -->

# web3js-eea 

Use the [web3.js-eea library](https://github.com/PegaSysEng/eeajs) to [create and send 
private transactions](../Privacy/How-To/Creating-Sending-Private-Transactions.md).

## Options Parameter 

The Options parameter has the following properties: 

* `privateKey`: Ethereum private key with which to sign the transaction
* `privateFrom` : Orion public key of the sender
* [`privateFor` : Orion public keys of recipients or `privacyGroupId`: Privacy group to receive the transaction](../Privacy/Explanation/Privacy-Groups.md)  
* `nonce` : Optional. If not provided, calculated using [`eea_getTransctionCount`](../Reference/Pantheon-API-Methods.md).
* `to` : Optional. Contract address to send the transaction to. Do not specify for contract deployment transactions
* `data` : Transaction data

## createPrivacyGroup

Creates privacy group for Pantheon privacy. 

**Parameters**

[Transaction options](#options-parameter)

**Returns** 

`string` : Privacy group ID 

!!! example 
    ```bash
    const createPrivacyGroup = () => {
      const contractOptions = {
        addresses: [orion.node1.publicKey, orion.node2.publicKey],
        privateFrom: orion.node1.publicKey,
        name: "Privacy Group A",
        description: "Members of Group A"
      };
      return web3.eea.createPrivacyGroup(contractOptions).then(result => {
        console.log(`The privacy group created is:`, result);
        return result;
      });
    };
    ```

## deletePrivacyGroup

Deletes privacy group. 

**Parameters**

[Transaction options](#options-parameter)

**Returns** 

`string` : Privacy group ID 

!!! example 
    ```bash
    const deletePrivacyGroup = givenPrivacyGroupId => {
      const contractOptions = {
        privacyGroupId: givenPrivacyGroupId,
        privateFrom: orion.node1.publicKey
      };
      return web3.eea.deletePrivacyGroup(contractOptions).then(result => {
        console.log(`The privacy group deleted is:`, result);
        return result;
      });
    };
    ```

## findPrivacyGroup

Finds privacy groups containing only the specified members.

**Parameters**

[Transaction options](#options-parameter)

**Returns** 

`array of objects` : Privacy groups containing only the specified members. 

!!! example 
    ```bash
    const findPrivacyGroup = () => {
      const contractOptions = {
        addresses: [orion.node1.publicKey, orion.node2.publicKey]
      };
      return web3.eea.findPrivacyGroup(contractOptions).then(result => {
        console.log(`The privacy groups found are:`, result);
        return result;
      });
    };
    ```

## generatePrivacyGroup
    
Generates the privacy group ID for EEA privacy. The privacy group ID is the RLP-encoded `privateFor` and `privateFrom` keys.
    
**Parameters**
    
[Transaction options](#options-parameter)
    
**Returns**
    
`string` : Privacy group ID 

!!! example
    ```bash
    const privacyGroupId = web3.eea.generatePrivacyGroup({
      privateFrom: "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
      privateFor: ["Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="]
    });
    ```

## getMarkerTransaction

Gets the [privacy marker transaction](../Privacy/Explanation/Private-Transaction-Processing.md) transaction receipt.

**Parameters**

`txHash` - `string` : Transaction hash of the private transaction

`retries` - `int` : Maximum number of attempts to get the private marker transaction receipt 

`delay` - `int` : Delay between retries in milliseconds

**Returns**

Privacy marker transaction receipt 

!!! example
    ```bash
    const privateMarkerTransacion = web3.eea.getMarkerTransaction("0x9c41b3d44ed73511c82a9e2b1ef581eb797475c82f318ca2802358d3ba4a8274", 5, 100);
    ```
        
## getTransactionCount 

Returns the number of transactions sent from the specified address for the privacy group.

**Parameters**

[Transaction options](#options-parameter)

**Returns**

`int` : Transaction count for that account (`privateKey`) and privacy group

!!! example
    ```bash
    return web3.eea
       .getTransactionCount({
       privateKey: pantheon.node1.privateKey,
       privateFrom: orion.node1.publicKey,
       privateFor: [orion.node2.publicKey],
    })
    ```
        
## getTransactionReceipt 

Gets the private transaction receipt using [`eea_getTransactionReceipt`](../Reference/Pantheon-API-Methods.md#eea_gettransactionreceipt).

**Parameters**

`txHash` - `string` : Transaction hash of the private transaction

`enclavePublicKey` - `string` : [`privateFrom` key for the transaction](#options-parameter) 

`retries` - `int` : Optional. Maximum number of attempts to get the private marker transaction receipt. Default is `300`. 

`delay` - `int` : Optional. Delay between retries in milliseconds. Default is `1000`.

**Returns**

Private transaction receipt 

!!! example
    ```bash
    const privateTxReceipt = web3.eea.getTransactionReceipt("0x9c41b3d44ed73511c82a9e2b1ef581eb797475c82f318ca2802358d3ba4a8274", "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
    ```
    
## sendRawTransaction 

Signs and sends a RLP-encoded private transaction to Pantheon using [`eea_sendRawTransaction`](Pantheon-API-Methods.md#eea_sendrawtransaction). 

`sendRawTransaction` supports [EEA-compliant privacy](../Privacy/How-To/EEA-Compliant.md) using `privateFor`, or [Pantheon-extended privacy](../Privacy/How-To/Pantheon-Privacy.md) using `privacyGroupId`. 

**Parameters**

[Transaction options](#options-parameter)

**Returns**

`string` : Transaction hash of the [`privacy marker transaction`](../Privacy/Explanation/Private-Transaction-Processing.md)   
        
!!! example "Pantheon-extended Privacy"
    ```bash tab="Contract Deployment with privacyGroupId"
    const createPrivateEmitterContract = privacyGroupId => {
      const contractOptions = {
        data: `0x${binary}`,
        privateFrom: orion.node1.publicKey,
        privacyGroupId,
        privateKey: pantheon.node1.privateKey
      };
      return web3.eea.sendRawTransaction(contractOptions);
    };
    ```
                
    ```bash tab="Contract Invocation with privacyGroupId "
    const functionCall = {
       to: address,
       data: functionAbi.signature,
       privateFrom,
       privacyGroupId,
       privateKey
    };
    return web3.eea.sendRawTransaction(functionCall);
    ```

!!! example "EEA-compliant Privacy"
    ```bash tab="Contract Deployment with privateFor"
    const createPrivateEmitterContract = () => {
      const contractOptions = {
         data: `0x${binary}`,
         privateFrom: orion.node1.publicKey,
         privateFor: [orion.node2.publicKey],
         privateKey: pantheon.node1.privateKey
      };
      return web3.eea.sendRawTransaction(contractOptions);
    };
    ```
            
    ```bash tab="Contract Invocation with privateFor"
    const functionCall = {
      to: address,
      data: functionAbi.signature + functionArgs,
      privateFrom: orion.node1.publicKey,
      privateFor: [orion.node2.publicKey],
      privateKey: pantheon.node1.privateKey
    };
    return web3.eea.sendRawTransaction(functionCall);
    ```