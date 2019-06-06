description: Pantheon Permissioning feature
<!--- END of page meta data -->

# Permissioning 

A permissioned network allows only specified nodes and accounts to participate by enabling node permissioning and/or account permissioning on the network.

!!! note
    In peer-to-peer networks, node-level permissions can be used to enforce rules on nodes you control. 
    With node-level permissions only, it is still possible a bad actor could violate network governance 
    and act as a proxy to other nodes.  
    
![Node Permissioning](../images/node-permissioning-bad-actor.png)

![Account Permissioning](../images/account-permissioning.png)

## Local 

[Local permissioning](Local-Permissioning.md) are specified at the node level. Each node in the network has a [permissions configuration file](#permissions-configuration-file).
Updates to local permissioning must be made to the configuration file for each node. 

## Onchain 

[Onchain permissioning](Onchain-Permissioning.md) is specified in a smart contract on the network. Specifying permissioning onchain
enables all nodes to read and update permissioning in one location. 

!!! note
    Onchain permissioning for accounts will be available in a future Pantheon release. 
