description: Creating and manage privacy groups
<!--- END of page meta data -->

Pantheon-extended privacy provides JSON-RPC API methods for creating and managing privacy groups: 

* [`priv_createPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_createprivacygroup)
* [`priv_findPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_findprivacygroup)
* [`priv_deletePrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_deleteprivacygroup)

!!! tip
    [EEA-compliant privacy groups](../Explanation/Privacy-Groups.md) can be found and deleted using 
    [`priv_findPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_findprivacygroup)
    and [`priv_deletePrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_deleteprivacygroup) but 
    future functionality to update group membership will only be available for privacy groups 
    created using [`priv_createPrivacyGroup`](../../Reference/Pantheon-API-Methods.md#priv_createprivacygroup). 