description: Pantheon Permissioning feature
<!--- END of page meta data -->

# Permissioning 

A permissioned network is a network where only specified nodes and accounts (participants) can participate. 
Nodes and accounts outside those specified are prevented from participating. Permissioned networks can have node permissioning enabled, 
account permissioning enabled, or both. 

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

Onchain permissioning are specified in a smart contract on the network. Specifying permissioning onchain
enables all nodes to read and update permissioning in one location. 

!!! note
    Onchain permissioning for nodes is under development and will be available in v1.1. Onchain permissioning
    for accounts will be available in a future Pantheon release. 