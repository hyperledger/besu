# HeaderBatchDownloader Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract header batch download logic from DownloadBackwardHeadersStep into a shared HeaderBatchDownloader helper that supports both forward and backward downloads.

**Architecture:** Move the `downloadAllHeaders` do-while loop from DownloadBackwardHeadersStep into a new HeaderBatchDownloader class with a Direction parameter. Both DownloadHeadersStep and DownloadBackwardHeadersStep become thin wrappers. Delete DownloadHeaderSequenceTask.

**Tech Stack:** Java 21, JUnit 5, Mockito, AssertJ

---

## Task 1: Create HeaderBatchDownloader with REVERSE support

**Files:**
- Create: `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloader.java`
- Create: `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloaderTest.java`

**Step 1: Create the HeaderBatchDownloader class**

Extract the core logic from `DownloadBackwardHeadersStep.downloadAllHeaders()`:

```java
/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads block headers in batches until the requested count is reached.
 * Supports both forward and reverse directions.
 */
public class HeaderBatchDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(HeaderBatchDownloader.class);

  /** Direction for header downloads. */
  public enum Direction {
    FORWARD,
    REVERSE
  }

  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final int batchSize;

  public HeaderBatchDownloader(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1");
    }
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.batchSize = batchSize;
  }

  /**
   * Downloads headers in batches until the target count is reached.
   *
   * @param startBlockNumber the block number to start from
   * @param count the total number of headers to download
   * @param direction FORWARD or REVERSE
   * @return list of headers in request order
   */
  public List<BlockHeader> downloadHeaders(
      final long startBlockNumber, final int count, final Direction direction) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be >= 1");
    }

    final List<BlockHeader> headers = new ArrayList<>(count);
    do {
      final int remaining = count - headers.size();
      final int headersToRequest = Math.min(batchSize, remaining);
      final long requestBlockNumber = calculateRequestBlockNumber(startBlockNumber, headers.size(), direction);

      final GetHeadersFromPeerTask task =
          new GetHeadersFromPeerTask(
              requestBlockNumber,
              headersToRequest,
              0,
              direction == Direction.REVERSE
                  ? GetHeadersFromPeerTask.Direction.REVERSE
                  : GetHeadersFromPeerTask.Direction.FORWARD,
              protocolSchedule);

      final PeerTaskExecutorResult<List<BlockHeader>> result =
          ethContext.getPeerTaskExecutor().execute(task);

      final PeerTaskExecutorResponseCode responseCode = result.responseCode();
      if (responseCode != PeerTaskExecutorResponseCode.SUCCESS) {
        if (responseCode == PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR) {
          throw new RuntimeException(
              "Failed to download " + count + " headers starting from block " + startBlockNumber);
        }
        // wait for a peer to become available before retrying
        ethContext.getEthPeers().waitForPeer(__ -> true);
      } else {
        final Optional<List<BlockHeader>> optionalBlockHeaderList = result.result();
        final List<BlockHeader> resultBlockHeaders = optionalBlockHeaderList.orElseGet(List::of);
        if (!headers.isEmpty() && !resultBlockHeaders.isEmpty()) {
          validateContinuity(headers, resultBlockHeaders, direction);
        }
        headers.addAll(resultBlockHeaders);
      }
    } while (headers.size() < count);

    LOG.atTrace()
        .setMessage("Downloaded {} headers: blocks {} to {}")
        .addArgument(headers.size())
        .addArgument(headers.getFirst().getNumber())
        .addArgument(headers.getLast().getNumber())
        .log();

    return headers;
  }

  private long calculateRequestBlockNumber(
      final long startBlockNumber, final int headersAlreadyDownloaded, final Direction direction) {
    if (direction == Direction.REVERSE) {
      return startBlockNumber - headersAlreadyDownloaded;
    } else {
      return startBlockNumber + headersAlreadyDownloaded;
    }
  }

  private void validateContinuity(
      final List<BlockHeader> existing,
      final List<BlockHeader> newHeaders,
      final Direction direction) {
    if (direction == Direction.REVERSE) {
      // For reverse: new batch's first header's hash should equal existing last header's parent hash
      if (!newHeaders.getFirst().getHash().equals(existing.getLast().getParentHash())) {
        throw new IllegalStateException(
            "Parent hash of last header does not match first header of new batch");
      }
    } else {
      // For forward: new batch's first header's parent hash should equal existing last header's hash
      if (!newHeaders.getFirst().getParentHash().equals(existing.getLast().getHash())) {
        throw new IllegalStateException(
            "First header's parent hash does not match last header of existing batch");
      }
    }
  }
}
```

**Step 2: Create the test file with REVERSE tests (moved from DownloadBackwardHeadersStepTest)**

```java
/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader.Direction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeaderBatchDownloaderTest {

  private static ProtocolContext protocolContext;
  private static ProtocolSchedule protocolSchedule;
  private static MutableBlockchain blockchain;

  private PeerTaskExecutor peerTaskExecutor;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;

  @BeforeAll
  public static void setUpClass() {
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    setupUtil.importFirstBlocks(20);
    protocolContext = setupUtil.getProtocolContext();
    protocolSchedule = setupUtil.getProtocolSchedule();
    blockchain = setupUtil.getBlockchain();
  }

  @BeforeEach
  public void setUp() {
    peerTaskExecutor = mock(PeerTaskExecutor.class);
    TransactionPool transactionPool = mock(TransactionPool.class);
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(ProtocolScheduleFixture.TESTING_NETWORK)
            .setBlockchain(blockchain)
            .setEthScheduler(new DeterministicEthScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    ethContext = ethProtocolManager.ethContext();
  }

  @Test
  public void shouldDownloadHeadersReverse_singleBatch() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    final List<BlockHeader> mockHeaders = createMockHeaders(10, 100, Direction.REVERSE);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 10, Direction.REVERSE);

    assertThat(result).hasSize(10);
    assertThat(result).isEqualTo(mockHeaders);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldDownloadHeadersReverse_multipleBatches() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 30);

    // Request 50 headers with batch size 30: first batch 30, second batch 20
    final List<BlockHeader> firstBatch = createMockHeadersWithParentHash(30, 100, Direction.REVERSE, null);
    final List<BlockHeader> secondBatch = createMockHeadersWithParentHash(20, 70, Direction.REVERSE, firstBatch.getLast().getParentHash());

    final PeerTaskExecutorResult<List<BlockHeader>> firstResult =
        new PeerTaskExecutorResult<>(
            Optional.of(firstBatch), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> secondResult =
        new PeerTaskExecutorResult<>(
            Optional.of(secondBatch), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(firstResult, secondResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 50, Direction.REVERSE);

    assertThat(result).hasSize(50);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldRetryOnNoPeerAvailable() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 5);

    final List<BlockHeader> mockHeaders = createMockHeaders(5, 100, Direction.REVERSE);

    final PeerTaskExecutorResult<List<BlockHeader>> noPeerResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE,
            Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(noPeerResult, successResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 5, Direction.REVERSE);

    assertThat(result).hasSize(5);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  public void shouldFailOnInternalServerError() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    final PeerTaskExecutorResult<List<BlockHeader>> errorResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(errorResult);

    assertThatThrownBy(() -> downloader.downloadHeaders(100L, 10, Direction.REVERSE))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to download 10 headers starting from block 100");
  }

  @Test
  public void shouldThrowOnInvalidBatchSize() {
    assertThatThrownBy(() -> new HeaderBatchDownloader(protocolSchedule, ethContext, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize must be >= 1");
  }

  @Test
  public void shouldThrowOnInvalidCount() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    assertThatThrownBy(() -> downloader.downloadHeaders(100L, 0, Direction.REVERSE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("count must be >= 1");
  }

  private List<BlockHeader> createMockHeaders(
      final int count, final long startBlock, final Direction direction) {
    return createMockHeadersWithParentHash(count, startBlock, direction, null);
  }

  private List<BlockHeader> createMockHeadersWithParentHash(
      final int count, final long startBlock, final Direction direction, final Hash firstParentHash) {
    final BlockHeaderFunctions bhf = new LocalBlockHeaderFunctions();
    final List<BlockHeader> headers = new ArrayList<>();
    Hash parentHash = firstParentHash != null ? firstParentHash : Hash.EMPTY;

    for (int i = 0; i < count; i++) {
      long blockNumber = direction == Direction.REVERSE ? startBlock - i : startBlock + i;
      BlockHeader header =
          new BlockHeaderTestFixture()
              .number(blockNumber)
              .blockHeaderFunctions(bhf)
              .parentHash(parentHash)
              .buildHeader();
      headers.add(header);
      parentHash = header.getHash();
    }
    return headers;
  }

  static class LocalBlockHeaderFunctions implements BlockHeaderFunctions {
    @Override
    public Hash hash(final BlockHeader header) {
      return Hash.hash(header.getParentHash().toHexString() + header.getNumber());
    }

    @Override
    public ParsedExtraData parseExtraData(final BlockHeader header) {
      return null;
    }
  }
}
```

**Step 3: Run tests to verify**

Run: `./gradlew :ethereum:eth:test --tests "HeaderBatchDownloaderTest" -x spotlessCheck`

Expected: All tests pass

**Step 4: Run spotless**

Run: `./gradlew :ethereum:eth:spotlessApply`

**Step 5: Commit**

```bash
git add ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloader.java \
        ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloaderTest.java
git commit -m "feat: add HeaderBatchDownloader for batch header downloads

Extract header batch download logic into reusable helper class.
Supports both FORWARD and REVERSE directions with continuity validation.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Add FORWARD direction tests to HeaderBatchDownloader

**Files:**
- Modify: `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloaderTest.java`

**Step 1: Add FORWARD direction tests**

Add these test methods to `HeaderBatchDownloaderTest.java`:

```java
@Test
public void shouldDownloadHeadersForward_singleBatch() {
  final HeaderBatchDownloader downloader =
      new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

  final List<BlockHeader> mockHeaders = createMockHeaders(10, 100, Direction.FORWARD);
  final PeerTaskExecutorResult<List<BlockHeader>> successResult =
      new PeerTaskExecutorResult<>(
          Optional.of(mockHeaders),
          PeerTaskExecutorResponseCode.SUCCESS,
          Collections.emptyList());

  when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

  final List<BlockHeader> result = downloader.downloadHeaders(100L, 10, Direction.FORWARD);

  assertThat(result).hasSize(10);
  assertThat(result.getFirst().getNumber()).isEqualTo(100);
  assertThat(result.getLast().getNumber()).isEqualTo(109);
  verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
}

@Test
@SuppressWarnings("unchecked")
public void shouldDownloadHeadersForward_multipleBatches() {
  final HeaderBatchDownloader downloader =
      new HeaderBatchDownloader(protocolSchedule, ethContext, 30);

  // Request 50 headers with batch size 30: first batch 30, second batch 20
  final List<BlockHeader> firstBatch = createMockHeadersWithParentHash(30, 100, Direction.FORWARD, null);
  final List<BlockHeader> secondBatch = createMockHeadersWithParentHash(20, 130, Direction.FORWARD, firstBatch.getLast().getHash());

  final PeerTaskExecutorResult<List<BlockHeader>> firstResult =
      new PeerTaskExecutorResult<>(
          Optional.of(firstBatch), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());
  final PeerTaskExecutorResult<List<BlockHeader>> secondResult =
      new PeerTaskExecutorResult<>(
          Optional.of(secondBatch), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());

  when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
      .thenReturn(firstResult, secondResult);

  final List<BlockHeader> result = downloader.downloadHeaders(100L, 50, Direction.FORWARD);

  assertThat(result).hasSize(50);
  verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
}
```

**Step 2: Run tests**

Run: `./gradlew :ethereum:eth:test --tests "HeaderBatchDownloaderTest" -x spotlessCheck`

Expected: All tests pass

**Step 3: Run spotless and commit**

```bash
./gradlew :ethereum:eth:spotlessApply
git add ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/HeaderBatchDownloaderTest.java
git commit -m "test: add FORWARD direction tests for HeaderBatchDownloader

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Refactor DownloadBackwardHeadersStep to use HeaderBatchDownloader

**Files:**
- Modify: `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/fastsync/DownloadBackwardHeadersStep.java`
- Modify: `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/fastsync/DownloadBackwardHeadersStepTest.java`

**Step 1: Refactor DownloadBackwardHeadersStep to delegate to HeaderBatchDownloader**

Replace the implementation:

```java
/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader;
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader.Direction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Downloads block headers in reverse direction (backward from pivot to genesis). Returns headers in
 * reverse order: [n, n-1, n-2, ...].
 */
public class DownloadBackwardHeadersStep
    implements Function<Long, CompletableFuture<List<BlockHeader>>> {

  private final EthContext ethContext;
  private final HeaderBatchDownloader headerBatchDownloader;
  private final int headerRequestSize;
  private final long trustAnchorBlockNumber;

  /**
   * Creates a new DownloadBackwardHeadersStep.
   *
   * @param protocolSchedule the protocol schedule
   * @param ethContext the eth context
   * @param headerRequestSize the number of headers to request per batch
   * @param trustAnchorBlockNumber the lowest header that we want to download
   */
  public DownloadBackwardHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final int headerRequestSize,
      final long trustAnchorBlockNumber) {
    if (headerRequestSize < 1) throw new IllegalArgumentException("headerRequestSize must be >= 1");
    this.ethContext = ethContext;
    this.headerBatchDownloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, headerRequestSize);
    this.headerRequestSize = headerRequestSize;
    this.trustAnchorBlockNumber = trustAnchorBlockNumber;
  }

  @Override
  public CompletableFuture<List<BlockHeader>> apply(final Long startBlockNumber) {
    final long remainingHeaders = startBlockNumber - trustAnchorBlockNumber;
    final int headersToRequest = (int) Math.min(headerRequestSize, remainingHeaders);
    if (headersToRequest < 1) {
      throw new IllegalStateException(
          "Number of headers to request is less than 1:" + headersToRequest);
    }

    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () ->
                CompletableFuture.completedFuture(
                    headerBatchDownloader.downloadHeaders(
                        startBlockNumber, headersToRequest, Direction.REVERSE)))
        .orTimeout(2L, TimeUnit.MINUTES);
  }
}
```

**Step 2: Run existing tests to verify behavior is unchanged**

Run: `./gradlew :ethereum:eth:test --tests "DownloadBackwardHeadersStepTest" -x spotlessCheck`

Expected: All tests pass (behavior unchanged)

**Step 3: Run spotless and commit**

```bash
./gradlew :ethereum:eth:spotlessApply
git add ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/fastsync/DownloadBackwardHeadersStep.java
git commit -m "refactor: DownloadBackwardHeadersStep delegates to HeaderBatchDownloader

No behavior change - the step now delegates to the shared helper class.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Refactor DownloadHeadersStep to use HeaderBatchDownloader

**Files:**
- Modify: `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/DownloadHeadersStep.java`

**Step 1: Update DownloadHeadersStep to use HeaderBatchDownloader for both cases**

```java
/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader.Direction;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeaders;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRange;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.FutureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadHeadersStep
    implements Function<SyncTargetRange, CompletableFuture<RangeHeaders>> {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadHeadersStep.class);
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final int headerRequestSize;
  private final HeaderBatchDownloader headerBatchDownloader;

  public DownloadHeadersStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final ValidationPolicy validationPolicy,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.headerRequestSize = headerRequestSize;
    this.headerBatchDownloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, headerRequestSize);
  }

  @Override
  public CompletableFuture<RangeHeaders> apply(final SyncTargetRange checkpointRange) {
    final CompletableFuture<List<BlockHeader>> taskFuture = downloadHeaders(checkpointRange);
    final CompletableFuture<RangeHeaders> processedFuture =
        taskFuture.thenApply(headers -> processHeaders(checkpointRange, headers));
    FutureUtils.propagateCancellation(processedFuture, taskFuture);
    return processedFuture;
  }

  private CompletableFuture<List<BlockHeader>> downloadHeaders(final SyncTargetRange range) {
    if (range.hasEnd()) {
      LOG.debug(
          "Downloading headers for range {} to {}",
          range.getStart().getNumber(),
          range.getEnd().getNumber());
      if (range.getSegmentLengthExclusive() == 0) {
        // There are no extra headers to download.
        return completedFuture(emptyList());
      }
      return ethContext
          .getScheduler()
          .scheduleServiceTask(
              () ->
                  completedFuture(
                      headerBatchDownloader.downloadHeaders(
                          range.getEnd().getNumber() - 1,
                          range.getSegmentLengthExclusive(),
                          Direction.REVERSE)));
    } else {
      LOG.debug("Downloading headers starting from {}", range.getStart().getNumber());
      return ethContext
          .getScheduler()
          .scheduleServiceTask(
              () ->
                  completedFuture(
                      headerBatchDownloader.downloadHeaders(
                          range.getStart().getNumber() + 1,
                          headerRequestSize,
                          Direction.FORWARD)));
    }
  }

  private RangeHeaders processHeaders(
      final SyncTargetRange checkpointRange, final List<BlockHeader> headers) {
    if (checkpointRange.hasEnd()) {
      final List<BlockHeader> headersToImport = new ArrayList<>(headers);
      headersToImport.add(checkpointRange.getEnd());
      return new RangeHeaders(checkpointRange, headersToImport);
    } else {
      List<BlockHeader> headersToImport = headers;
      if (!headers.isEmpty() && headers.getFirst().equals(checkpointRange.getStart())) {
        headersToImport = headers.subList(1, headers.size());
      }
      return new RangeHeaders(checkpointRange, headersToImport);
    }
  }
}
```

**Step 2: Run existing tests**

Run: `./gradlew :ethereum:eth:test --tests "DownloadHeadersStepTest" -x spotlessCheck`

Expected: Tests pass (may need adjustment if mocking expectations changed)

**Step 3: Run spotless and commit**

```bash
./gradlew :ethereum:eth:spotlessApply
git add ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/DownloadHeadersStep.java
git commit -m "refactor: DownloadHeadersStep uses HeaderBatchDownloader

Both hasEnd (backward) and !hasEnd (forward) cases now use the shared
HeaderBatchDownloader. Removes dependency on DownloadHeaderSequenceTask.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Delete DownloadHeaderSequenceTask

**Files:**
- Delete: `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTask.java`
- Delete: `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTaskTest.java`

**Step 1: Verify no other references to DownloadHeaderSequenceTask**

Run: `grep -r "DownloadHeaderSequenceTask" ethereum/eth/src --include="*.java" | grep -v "^Binary"`

Expected: Only the files we're deleting should be listed

**Step 2: Delete the files**

```bash
rm ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTask.java
rm ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/sync/tasks/DownloadHeaderSequenceTaskTest.java
```

**Step 3: Ensure the build compiles**

Run: `./gradlew :ethereum:eth:compileJava :ethereum:eth:compileTestJava -x spotlessCheck`

Expected: Build succeeds

**Step 4: Run all affected tests**

Run: `./gradlew :ethereum:eth:test -x spotlessCheck`

Expected: All tests pass

**Step 5: Commit**

```bash
git add -A
git commit -m "chore: delete DownloadHeaderSequenceTask

No longer needed - replaced by HeaderBatchDownloader.
Removes ~300 lines of complex retry/validation code.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Final verification

**Step 1: Run full build with spotless**

Run: `./gradlew :ethereum:eth:build`

Expected: Build succeeds

**Step 2: Run full test suite for eth module**

Run: `./gradlew :ethereum:eth:test`

Expected: All tests pass

**Step 3: Verify no regressions in related modules**

Run: `./gradlew :ethereum:core:test :besu:test -x spotlessCheck`

Expected: Tests pass

---

## Summary

| Task | Description | Files Changed |
|------|-------------|---------------|
| 1 | Create HeaderBatchDownloader with REVERSE support | +2 new files |
| 2 | Add FORWARD direction tests | Modify test file |
| 3 | Refactor DownloadBackwardHeadersStep | Modify 1 file |
| 4 | Refactor DownloadHeadersStep | Modify 1 file |
| 5 | Delete DownloadHeaderSequenceTask | -2 files |
| 6 | Final verification | No changes |
