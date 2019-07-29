description: Pantheon-extended privacy
<!--- END of page meta data -->

# Using Pantheon-extended Privacy 

Pantheon provides an extended implementation of privacy by allowing a [privacy
group to be created for a set of participants](../Explanation/Privacy-Groups.md). The privacy group ID 
must be specified when sending private transactions. 

Using the [`--rpc-http-api`](../../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) or [`--rpc-ws-api`](../../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api)
command line options enable: 

* [`EEA` API methods](../../Reference/Pantheon-API-Methods.md#eea-methods) 
* [`PRIV` API methods](../../Reference/Pantheon-API-Methods.md#priv-methods)

Use [`priv_createPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_createprivacygroup) to 
create the privacy group containing the recipients of the private transaction. 

Specify `privacyGroupId` when creating the signed transaction passed as an input parameter to [`eea_sendRawTransaction`](../../Reference/Pantheon-API-Methods.md#eea_sendrawtransaction)
to create an EEA-compliant private transaction. 

!!! note
    Support for specifying `privacyGroupId` when using `eea_sendTransaction` with EthSigner will be available in
    a future EthSigner release. 
    
## Privacy Group Type 

Privacy groups created using  [`priv_createPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_createprivacygroup)
are identified as type `PANTHEON` when returned by [`priv_findPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_findprivacygroup).

!!! example 
    ```json
    {
      "jsonrpc": "2.0",
      "id": 1,
      "result": [
         {
           "privacyGroupId": "GpK3ErNO0xF27T0sevgkJ3+4qk9Z+E3HtXYxcKIBKX8=",
           "name": "Group B",
           "description": "Description of Group B",
           "type": "PANTHEON",
           "members": [
             "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk=",
             "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw="
           ]
         }
      ]
    }
    ```