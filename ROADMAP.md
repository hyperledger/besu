# Roadmap
This document represents the current working roadmap for Pantheon.  It is a living document, which will 
evolve and change over time.  In particular the features in later versions are likely to be refined and change.

We use the approach of  `#now`, `#next`, `#later` [used by foursquare](https://medium.com/@noah_weiss/now-next-later-roadmaps-without-the-drudgery-1cfe65656645), with a slightly different time horizon.  
Our now scale is about 3 months, next about 6 months, and then later is beyond.

## Now (up to v1.1)
Our key areas for now are:
* Privacy
* Smart Contract Based Permissioning
* First Class Client

### Privacy
The Enterprise Ethereum `restricted` privacy will be implemented.

### Smart Contract Based Permissioning
Building on the Permissioning mechanism implemented in v1.0, Pantheon will use a smart contract to share 
permissioning information across the network. This will create a single, decentralized source of truth for node and account 
 permissions.

### First Class Client
There is ongoing work to enhance Pantheon's performance, to ensure it behaves well as a first class client. 
The current initiatives include the implementation of performance benchmarks, of a fast sync mechanism, and of 
support for all major testnets.

## Next
The key areas for next are:
* IBFT 2.x
* Ethereum 1.x
* State Pruning

### IBFT 2.x 

Continued network and consensus improvements to IBFT 2.0. 

### Ethereum 1.x
The 1.x series of EIPs that are currently under early development will be implemented as they are ready.  In addition, the team will work on helping the specification of the Ethereum roadmap.

### State Pruning 

State pruning will be implemented. State pruning reduces the disk space required for the Pantheon database by discarding outdated world state data.

## Future
In addition to making incremental improvements to the above features, there will be some bigger pieces of work.  
These are deliberately kept vague at this time, and will be elaborated upon when they move up to the now and next levels of work.
* Ethereum 2.0
* Advanced Privacy
* Alternate Consensus Mechanisms
* Sidechains

