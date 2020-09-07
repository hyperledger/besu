# Known Issues 

Details on previously identified known issues are provided below. Details on known issues identfied 
in the current release are provided in the [Changelog](CHANGELOG.md).

Known issues are open issues categorized as [Very High or High impact](https://wiki.hyperledger.org/display/BESU/Defect+Prioritisation+Policy). 

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

## Kubernetes permissioning uses Service IPs rather than pod IPs which can fail

When using permissioning on Kubernetes, nodes don't join and start the network and chain as expected: they check permissions and allow the services. It appears that some of the socket connections use the pod IP which [causes Besu to halt](https://github.com/hyperledger/besu/issues/1190). 

Workaround -> Do not use permissioning with Kubernetes. 

A fix for this issue is being actively worked on. 

## Restart caused by insufficient memory can cause inconsistent private state

While running reorg testing on Besu and Orion, insufficient memory caused Besu to restart, resulting in the state on that machine to become inconsistent with the rest of the members of the privacy group. 

Workaround -> Ensure you allocate enough memory for the Java Runtime Environment that the node does not run out of memory.

A fix for this issue is being actively worked on. 
