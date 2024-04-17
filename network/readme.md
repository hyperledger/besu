## How To's

### How to buils besu
- Go to the root dir of the project
  - ./gradlew installDist [--offline]

### How to start the network
- Go to the root dir of the project
  - cd network
  - sh start.sh
### How to stop the network
- On the same terminal
  - CTRL+C

(this will stop all 4 nodes)

### How to use the start script
- **sh start.sh**
  - starts the 4 nodes with the current config and data
- **sh start.sh --no-data**
  - starts the 4 nodes with no data
- **sh start.sh --no-data --only**
  - removes all 4 nodes data, do not start nodes
  
  

## Notes
- First time the network runs new databases will be created
- To add/change the _emptyBlockPeriodSeconds_ setting go to the _config_ section of the **genesis file** and add/change it, and restart the network

## Resources
- https://github.com/hyperledger/besu/pull/6944
- https://github.com/hyperledger/besu/issues/3810
- https://github.com/siladu/besu/tree/wip-qbft-empty-block-period
- https://github.com/Consensys/quorum/pull/1433
- https://docs.goquorum.consensys.io/configure-and-manage/configure/consensus-protocols/qbft#configure-block-time-on-an-existing-network
- https://besu.hyperledger.org/private-networks/tutorials/qbft
