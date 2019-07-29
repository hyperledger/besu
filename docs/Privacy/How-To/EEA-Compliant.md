description: JSON-RPC methods to use for EEA-compliant privacy 
<!--- END of page meta data -->

# Using EEA-compliant Privacy 

When using [EEA-compliant privacy](../Explanation/Privacy-Groups.md), the group of nodes specified by `privateFrom`and `privateFor` form a privacy group and are given a unique 
privacy group ID by Orion.

Enable the [`EEA` API methods](../../Reference/Pantheon-API-Methods.md#eea-methods) using the [`--rpc-http-api`](../../Reference/Pantheon-CLI-Syntax.md#rpc-http-api) 
or [`--rpc-ws-api`](../../Reference/Pantheon-CLI-Syntax.md#rpc-ws-api) command line options.

Specify `privateFor` when creating the signed transaction passed as an input parameter to [`eea_sendRawTransaction`](../../Reference/Pantheon-API-Methods.md#eea_sendrawtransaction)
to create an EEA-compliant private transaction. 

## Privacy Group Type 

Privacy groups created when specifying `privateFrom` and `privateFor` are identified as type `LEGACY` 
when returned by [`priv_findPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_findprivacygroup). 

!!! example
    ```json 
    {
        "jsonrpc": "2.0",
        "id": 1,
        "result": [
          {
             "privacyGroupId": "68/Cq0mVjB8FbXDLE1tbDRAvD/srluIok137uFOaClM=",
             "name": "legacy",
             "description": "Privacy groups to support the creation of groups by privateFor and privateFrom",
             "type": "LEGACY",
             "members": [
                "g59BmTeJIn7HIcnq8VQWgyh/pDbvbt2eyP0Ii60aDDw=",
                "negmDcN2P4ODpqn/6WkJ02zT/0w0bjhGpkZ8UP6vARk="
             ]
          }
        ]
    }
    ```

