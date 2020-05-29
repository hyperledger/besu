# Known Issues 

Details on previously identified known issues are provided below. Details on known issues identfied 
in the current release are provided in the [Changelog](CHANGELOG.md).

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy). 

## Scope of logs query causing Besu to hang

[`eth_getLogs` queries that are too large or too broad can cause Besu to never return](https://github.com/hyperledger/besu/issues/944). 

Workaround - Limit the number of blocks queried by each `eth_getLogs` call.

A fix for this issue is being actively worked on.

## Eth/65 loses peers 

From v1.4.4, `eth/65` is [disabled by default](https://github.com/hyperledger/besu/pull/741). 

If enabled, peers will slowly drop off and eventually Besu will fall out of sync or stop syncing.

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

## Privacy users with private transactions created using v1.3.4 or earlier 

A critical issue for privacy users with private transactions created using Hyperledger Besu v1.3.4 
or earlier has been identified. If you have a network with private transaction created using v1.3.4 
or earlier, please read the following and take the appropriate steps: 
https://wiki.hyperledger.org/display/BESU/Critical+Issue+for+Privacy+Users 
