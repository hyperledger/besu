# Roadmap
This document represents the current working roadmap for Pantheon.  It is a living document, which will evolve and change over time.  In particular the features in later versions are likely to be refined and change.

We use the approach of  `#now`, `#next`, `#later` [used by foursquare](https://medium.com/@noah_weiss/now-next-later-roadmaps-without-the-drudgery-1cfe65656645), with a slightly different time horizon.  Our now scale is about 3 months, next about 6 months, and then later is beyond.

## Now
Our key three areas for now are:
* Permissioning
* First Class Client
* iBFT 2.0

### Permissioning
We are implementing the key elements of an Enterprise Ethereum Permissioned network.  The initial version of this will be based around a JSON RPC API to manage the network. This will form the foundation for a smart contract based solution which will be developed in the `next` release (1.1)

### First Class Client
There is an ongoing piece of work underway enhancing the core performance of Pantheon, and ensuring that it behaves well as a first class client. The key elements of this are implementation of some performance benchmarks, finalising the options for the command line, and implementing an appropriate fast sync mechanism.

### iBFT 2.0
Work is underway designing and implementing a new PBFT consensus mechanism, improving the existing iBFT protocol, addressing some liveness and safety issues in the original protocol.

## Next
The key areas for next are:
* Smart contract based permissioning
* Privacy
* Ethereum 1.x
* iBFT 2.x

### Smart Contract based Permissioning
Building on the Permissioning system  implemented in version 1.0 of Pantheon, we will use a smart contract to share this information across the network, giving a consistent set of permissions, and ensuring that all nodes in the network work consistently.

### Privacy
The Enterprise Ethereum `restricted` privacy will be implemented.

### Ethereum 1.x
The 1.x series of EIPs that are currently under early development will be implemented as they are ready.  In addition, the team will work on helping the specification of the Ethereum roadmap.

## Future
In addition to making incremental improvements to the above features, there will be some bigger pieces of work.  
These are deliberately kept vague at this time, and will be elaborated upon when they move up to the now and next levels of work.
* Ethereum 2.0
* Advanced Privacy
* Alternate Consensus Mechanisms
* Sidechains

