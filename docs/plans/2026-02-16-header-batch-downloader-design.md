# HeaderBatchDownloader Design

## Overview

Extract header batch download logic from `DownloadBackwardHeadersStep` into a shared `HeaderBatchDownloader` helper class. This enables both forward and backward header downloads to use the same tested implementation, and allows removal of the more complex `DownloadHeaderSequenceTask`.

## Goals

- Download all headers until complete (retry loop) for forward downloads in `DownloadHeadersStep`
- Reuse existing working code from `DownloadBackwardHeadersStep`
- Remove `DownloadHeaderSequenceTask` and its complexity
- Minimize logic changes to reduce risk

## Design

### New Class: HeaderBatchDownloader

Location: `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloader.java`

```java
public class HeaderBatchDownloader {

    public HeaderBatchDownloader(
        ProtocolSchedule protocolSchedule,
        EthContext ethContext,
        int batchSize
    );

    public CompletableFuture<List<BlockHeader>> downloadHeaders(
        long startBlockNumber,
        int count,
        Direction direction  // FORWARD or REVERSE
    );
}
```

The `downloadHeaders` method is extracted directly from `DownloadBackwardHeadersStep.downloadAllHeaders()` with the following adaptation:
- Add `Direction` parameter
- Adapt continuity validation for both directions:
  - REVERSE: `newHeaders.first().hash == existingHeaders.last().parentHash`
  - FORWARD: `newHeaders.first().parentHash == existingHeaders.last().hash`

### Modified: DownloadBackwardHeadersStep

Becomes a thin wrapper:

```java
public CompletableFuture<List<BlockHeader>> apply(final Long startBlockNumber) {
    // ... calculate headersToRequest ...
    return ethContext.getScheduler()
        .scheduleServiceTask(() ->
            headerBatchDownloader.downloadHeaders(startBlockNumber, headersToRequest, REVERSE))
        .orTimeout(2L, TimeUnit.MINUTES);
}
```

### Modified: DownloadHeadersStep

Both branches use `HeaderBatchDownloader`:

```java
private CompletableFuture<List<BlockHeader>> downloadHeaders(final SyncTargetRange range) {
    if (range.hasEnd()) {
        // Backward: download headers between checkpoints
        return headerBatchDownloader.downloadHeaders(
            range.getEnd().getNumber() - 1,
            range.getSegmentLengthExclusive(),
            REVERSE);
    } else {
        // Forward: download headers to chain head
        return headerBatchDownloader.downloadHeaders(
            range.getStart().getNumber() + 1,
            headerRequestSize,
            FORWARD);
    }
}
```

### Deleted: DownloadHeaderSequenceTask

No longer needed. Full header validation (previously done by this class) is handled during import.

## Data Flow

### Forward Download (hasEnd = false)
```
DownloadHeadersStep.apply(range)
    -> HeaderBatchDownloader.downloadHeaders(startBlock+1, count, FORWARD)
    -> Returns [n, n+1, n+2, ...]
```

### Backward Download (hasEnd = true)
```
DownloadHeadersStep.apply(range)
    -> HeaderBatchDownloader.downloadHeaders(endBlock-1, count, REVERSE)
    -> Returns [n, n-1, n-2, ...]
```

### Backward Headers Pipeline
```
DownloadBackwardHeadersStep.apply(startBlock)
    -> HeaderBatchDownloader.downloadHeaders(startBlock, count, REVERSE)
    -> Returns [n, n-1, n-2, ...]
```

## Error Handling

- **Peer unavailable:** Call `ethContext.getEthPeers().waitForPeer()` then retry
- **Internal server error:** Throw `RuntimeException`
- **Timeout:** 2 minute timeout on overall operation
- **Continuity validation failure:** Throw `IllegalStateException`

## Files Changed

### Create
- `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloader.java`
- `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloaderTest.java`

### Modify
- `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/DownloadHeadersStep.java`
- `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/DownloadHeadersStepTest.java`
- `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/fastsync/DownloadBackwardHeadersStep.java`
- `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/fastsync/DownloadBackwardHeadersStepTest.java`

### Delete
- `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTask.java`
- `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTaskTest.java`

## Testing

### HeaderBatchDownloaderTest (moved from DownloadBackwardHeadersStepTest)
- `downloadsAllHeaders_singleBatch`
- `downloadsAllHeaders_multipleBatches`
- `retriesOnPeerUnavailable`
- `validatesParentHashContinuity`
- `throwsOnContinuityFailure`

### New tests for FORWARD direction
- `downloadsAllHeadersForward_singleBatch`
- `downloadsAllHeadersForward_multipleBatches`
- `validatesParentHashContinuity_forward`

### DownloadHeadersStepTest
- Update to test both hasEnd=true and hasEnd=false cases use HeaderBatchDownloader

### DownloadBackwardHeadersStepTest
- Simplify to test wrapper behavior only

## Benefits

- Single implementation for batch header downloading
- Removes ~300 lines of complex code in DownloadHeaderSequenceTask
- Minimal changes to working, tested code
- Both forward and backward use identical logic
