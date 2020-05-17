# Known Issues 

Details on previously identified known issues are provided below. Details on known issues identfied 
in the current release are provided in the [Changelog](CHANGELOG.md).

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy). 

## Intrinsic gas exceeds gas limit

Calling delete and set to 0 Solidity mapping in Solidity [fails with an intrinsic gas exceeds gas limit error.](https://github.com/hyperledger/besu/issues/696)

## Eth/65 not backwards compatible 

From v1.4.4, `eth/65` is [disabled by default](https://github.com/hyperledger/besu/pull/741). 

The `eth/65` change is not [backwards compatible](https://github.com/hyperledger/besu/issues/723). 
This has the following impact: 
* In a private network, nodes using the 1.4.3 client cannot interact with nodes using 1.4.2 or earlier
clients. 
* On mainnet, synchronizing eventually stalls.

A fix for this issue is being actively worked on. 

## Fast sync when running Besu on cloud providers  

A known [RocksDB issue](https://github.com/facebook/rocksdb/issues/6435) causes fast sync to fail 
when running Besu on certain cloud providers. The following error is displayed repeatedly: 

```
...
EthScheduler-Services-1 (importBlock) | ERROR | PipelineChainDownloader | Chain download failed. Restarting after short delay.
java.util.concurrent.CompletionException: org.hyperledger.besu.plugin.services.exception.StorageException: org.rocksdb.RocksDBException: block checksum mismatch:
....
```

This behaviour has been seen on AWS and Digital Ocean. 

Workaround -> On AWS, a full restart of the AWS VM is required to restart the fast sync. 

Fast sync is not currently supported on Digital Ocean. We are investigating options to 
[add support for fast sync on Digital Ocean](https://github.com/hyperledger/besu/issues/591). 

## Bootnodes must be validators when using onchain permissioning

- Onchain permissioning nodes can't peer when using a non-validator bootnode [\#528](https://github.com/hyperledger/besu/issues/528)

Workaround -> When using onchain permissioning, ensure bootnodes are also validators. 

## Privacy users with private transactions created using v1.3.4 or earlier 

A critical issue for privacy users with private transactions created using Hyperledger Besu v1.3.4 
or earlier has been identified. If you have a network with private transaction created using v1.3.4 
or earlier, please read the following and take the appropriate steps: 
https://wiki.hyperledger.org/display/BESU/Critical+Issue+for+Privacy+Users 
