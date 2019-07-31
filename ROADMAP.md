# Roadmap
This document represents the current working roadmap for Pantheon.  It is a living document, which will 
evolve and change over time. In particular the features in later versions are likely to be refined and change.

We use the approach of  `#now`, `#next`, `#later` [used by foursquare](https://medium.com/@noah_weiss/now-next-later-roadmaps-without-the-drudgery-1cfe65656645), with a slightly different time horizon.  
Our `#now` scale is about 3 months, `#next` about 6 months, and `+later` is 12+ months.

## Now (up to v1.2)
Our key areas for now are:
* Making Pantheon a First Class Client
* Istanbul Support 
* Smart Contract Based Permissioning
* Advanced Privacy

### Making Pantheon a First Class Client

First and foremost, we want Pantheon to be a first class client for usage on both mainnet and permissioned networks. This entails maintaining compatibility with mainnet, providing permissioning features and constantly improving Pantheon's performance. Some recent additions to the client have been [UPnP Support](https://docs.pantheon.pegasys.tech/en/latest/Configuring-Pantheon/Networking/Using-UPnP/) and a [GraphQL interface](https://docs.pantheon.pegasys.tech/en/latest/Pantheon-API/GraphQL/). 

### Istanbul Support

Pantheon will support the upcomming Istanbul network upgrade and will implement all required EIPs as per the [Hardfork Meta: Istanbul EIP](https://eips.ethereum.org/EIPS/eip-1679). 

### Smart Contract Based Permissioning

Building on the smart contract based permissioning mechanism implemented in v1.1, additional tooling will be provided through a dapp to simplify and enhance the interaction with the smart contracts. Permissioning for Ethereum accounts will also be introduced.

### Advanced Privacy
The current privacy functionality will be enhanced as described in the [privacy roadmap](PRIVACYROADMAP.MD).

## Next (v1.3/1.4)
The key areas for next are:
* State Pruning
* Secure key-store and key management
* Disaster Recovery
* Permissioning using RBAC
* IBFT 2.x 
* Ethereum 1.x 

### State Pruning 

State pruning will be implemented. State pruning reduces the disk space required for the Pantheon database by discarding outdated world state data. 

### Secure Key-Store and Key Management

Pantheon will enable external key management for encryption and decryption operations, ensuring that key management is not solely managed by an external party. 

### Disaster Recovery

Support key-value storage in relational databases to solidify a robust Disaster Recovery process. Note: Orion to support Oracle and Postgres in 1.3. 

### Permissioning using RBAC

Enabling support for Role Based Access Control mechanisms to define the permissioning rules. Enabling permissions to be set based on groups and roles of nodes/accounts.

### IBFT 2.x 

Continuous improvements on our IBFT2.0 consensus mechanism to enable a stable Enterprise Ethereum client, ensuring a high degree of safety and liveness.

### Ethereum 1.x

The Pantheon team will help spearhead the Ethereum 1.x initiative by contributing to EIPs, community discussions, roadmap specificaton and eventually implementing the resulting features from the initiative. More information on the 1.x initiative can be found [here](https://docs.ethhub.io/ethereum-roadmap/ethereum-1.x/). 

## Future (v1.5+) 
In addition to making incremental improvements to the above features, there will be some bigger pieces of work.  
These are deliberately kept vague at this time, and will be elaborated upon when they move up to the now and next levels of work.

* Ethereum 2.0
* Alternate Consensus Mechanisms
* Sidechains
