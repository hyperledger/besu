# Roadmap
This document represents the current working roadmap for Besu.  It is a living document, which will 
evolve and change over time. In particular the features in later versions are likely to be refined and change.

We use the approach of  `#now`, `#next`, `#later` [used by foursquare](https://medium.com/@noah_weiss/now-next-later-roadmaps-without-the-drudgery-1cfe65656645), with a slightly different time horizon.  
Our `#now` scale is about 3 months, `#next` about 6 months, and `+later` is 12+ months.

## Now (up to v1.3)
Our key areas for now are:
* Making Besu a First Class Client
* Istanbul Support 
* State Pruning 
* Disaster recovery 

### Making Besu a First Class Client

First and foremost, we want Besu to be a first class client for usage on both mainnet and permissioned networks. 
This entails maintaining compatibility with mainnet, providing permissioning features and constantly improving Besu's performance. 
Some recent additions to the client have been [UPnP Support](https://docs.besu.pegasys.tech/en/latest/Configuring-Besu/Networking/Using-UPnP/)
and a [GraphQL interface](https://docs.besu.pegasys.tech/en/latest/Besu-API/GraphQL/). 

### Istanbul Support

Besu will support the upcoming Istanbul network upgrade and implement all required EIPs as per the [Hardfork Meta: Istanbul EIP](https://eips.ethereum.org/EIPS/eip-1679). 

### State Pruning 

State pruning will be implemented. State pruning reduces the disk space required for the Besu database by discarding outdated world state data. 

### Disaster Recovery

Support key-value storage in relational databases to solidify a robust Disaster Recovery process. Note: Orion to support Oracle and Postgres in 1.3. 

## Next (v1.4)
The key areas for next are:
* Tracing APIs
* Enhancing key management capabilities 
* Migration tools  
* Ethereum 1.x 

### Tracing APIs 

Additional tracing APIs to be added. 

### Enhancing Key Management

Enhancing key management capabilities by supporting secure storage of keys. 

### Migration Tools

Adding tools to enable migration across consensus algorithms. 

### Ethereum 1.x

The Besu team will help spearhead the Ethereum 1.x initiative by contributing to EIPs, community discussions, roadmap specificaton and eventually implementing the resulting features from the initiative. More information on the 1.x initiative can be found [here](https://docs.ethhub.io/ethereum-roadmap/ethereum-1.x/). 

## Future (v1.5+) 
In addition to making incremental improvements to the above features, there will be some bigger pieces of work.  
These are deliberately kept vague at this time, and will be elaborated upon when they move up to the now and next levels of work.

* Ethereum 2.0
* Alternate Consensus Mechanisms
* Sidechains
* Privacy group consensus 
* Cross privacy group communication 
* On-chain privacy 
