description: Updating onchain whitelists
<!--- END of page meta data -->

## Update Nodes Whitelist  

To add a node to the nodes whitelist: 

1. In the _Whitelisted Nodes_ tab of the Permissioning Management Dapp, click the _Add Whitelisted Nodes_
   button. The add node window is displayed.

2. Enter the [enode URL](../../Configuring-Pantheon/Node-Keys.md#enode-url) of the node to be added and click 
the _Add Whitelisted Node_ button. 

To remove a node from the nodes whitelist: 

1. In the _Whitelisted Nodes_ tab of the Permissioning Management Dapp, hover over the row of the node to remove. 
A trash can is displayed. 

1. Click on the trash can.  

!!! tip
    If you add a running node, the node does not attempt to reconnect to the bootnode and synchronize until 
    peer discovery restarts.  To add a whitelisted node as a peer without waiting for peer discovery to restart, use [`admin_addPeer`](../../Reference/Pantheon-API-Methods.md#admin_addpeer). 

    If the node is added to the whitelist before starting the node, using `admin_addPeer` is not required because
    peer discovery is run on node startup. 
    
## Update Accounts Whitelists 

To add an account to the accounts whitelist: 

1. In the _Whitelisted Accounts_ tab of the Permissioning Management Dapp, click the _Add Whitelisted Account_
 button. The add account window is displayed. 

1. Enter the account address in the _Account Address_ field and click the _Add Whitelisted Account_ button. 

To remove an account from the accounts whitelist: 

1. In the _Whitelisted Accounts_ tab of the Permissioning Management Dapp, hover over the 
row of the account to be removed. A trash can is displayed. 

1. Click on the trash can.     

## Update Admins 

Admins are added or removed in the same way as accounts except in the _Admin Accounts_ tab.  
