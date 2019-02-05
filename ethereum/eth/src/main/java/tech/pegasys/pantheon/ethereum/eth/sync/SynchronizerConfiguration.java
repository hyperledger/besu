/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.time.Duration;
import java.util.Optional;

import com.google.common.collect.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SynchronizerConfiguration {
  private static final Logger LOG = LogManager.getLogger();

  // TODO: Determine reasonable defaults here
  public static final int DEFAULT_PIVOT_DISTANCE_FROM_HEAD = 500;
  public static final float DEFAULT_FULL_VALIDATION_RATE = .1f;
  public static final int DEFAULT_FAST_SYNC_MINIMUM_PEERS = 5;
  private static final Duration DEFAULT_FAST_SYNC_MAXIMUM_PEER_WAIT_TIME = Duration.ofMinutes(5);
  private static final int DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST = 200;
  private static final int DEFAULT_WORLD_STATE_REQUEST_PARALLELISM = 10;

  // Fast sync config
  private final int fastSyncPivotDistance;
  private final float fastSyncFullValidationRate;
  private final int fastSyncMinimumPeerCount;
  private final Duration fastSyncMaximumPeerWaitTime;
  private final int worldStateHashCountPerRequest;
  private final int worldStateRequestParallelism;

  // Block propagation config
  private final Range<Long> blockPropagationRange;

  // General config
  private final SyncMode requestedSyncMode;
  private final Optional<SyncMode> syncMode;

  // Downloader config
  private final long downloaderChangeTargetThresholdByHeight;
  private final UInt256 downloaderChangeTargetThresholdByTd;
  private final int downloaderHeaderRequestSize;
  private final int downloaderCheckpointTimeoutsPermitted;
  private final int downloaderChainSegmentTimeoutsPermitted;
  private final int downloaderChainSegmentSize;
  private final long trailingPeerBlocksBehindThreshold;
  private final int maxTrailingPeers;
  private final int downloaderParallelism;
  private final int transactionsParallelism;
  private final int computationParallelism;

  private SynchronizerConfiguration(
      final SyncMode requestedSyncMode,
      final int fastSyncPivotDistance,
      final float fastSyncFullValidationRate,
      final int fastSyncMinimumPeerCount,
      final Duration fastSyncMaximumPeerWaitTime,
      final int worldStateHashCountPerRequest,
      final int worldStateRequestParallelism,
      final Range<Long> blockPropagationRange,
      final Optional<SyncMode> syncMode,
      final long downloaderChangeTargetThresholdByHeight,
      final UInt256 downloaderChangeTargetThresholdByTd,
      final int downloaderHeaderRequestSize,
      final int downloaderCheckpointTimeoutsPermitted,
      final int downloaderChainSegmentTimeoutsPermitted,
      final int downloaderChainSegmentSize,
      final long trailingPeerBlocksBehindThreshold,
      final int maxTrailingPeers,
      final int downloaderParallelism,
      final int transactionsParallelism,
      final int computationParallelism) {
    this.requestedSyncMode = requestedSyncMode;
    this.fastSyncPivotDistance = fastSyncPivotDistance;
    this.fastSyncFullValidationRate = fastSyncFullValidationRate;
    this.fastSyncMinimumPeerCount = fastSyncMinimumPeerCount;
    this.fastSyncMaximumPeerWaitTime = fastSyncMaximumPeerWaitTime;
    this.worldStateHashCountPerRequest = worldStateHashCountPerRequest;
    this.worldStateRequestParallelism = worldStateRequestParallelism;
    this.blockPropagationRange = blockPropagationRange;
    this.syncMode = syncMode;
    this.downloaderChangeTargetThresholdByHeight = downloaderChangeTargetThresholdByHeight;
    this.downloaderChangeTargetThresholdByTd = downloaderChangeTargetThresholdByTd;
    this.downloaderHeaderRequestSize = downloaderHeaderRequestSize;
    this.downloaderCheckpointTimeoutsPermitted = downloaderCheckpointTimeoutsPermitted;
    this.downloaderChainSegmentTimeoutsPermitted = downloaderChainSegmentTimeoutsPermitted;
    this.downloaderChainSegmentSize = downloaderChainSegmentSize;
    this.trailingPeerBlocksBehindThreshold = trailingPeerBlocksBehindThreshold;
    this.maxTrailingPeers = maxTrailingPeers;
    this.downloaderParallelism = downloaderParallelism;
    this.transactionsParallelism = transactionsParallelism;
    this.computationParallelism = computationParallelism;
  }

  /**
   * Validates the sync configuration against the blockchain, to define the actual sync mode.
   *
   * @param blockchain the local blockchain
   * @return a new, validated config instance
   */
  public SynchronizerConfiguration validated(final Blockchain blockchain) {
    if (syncMode.isPresent()) {
      return this;
    }

    final SyncMode actualSyncMode;
    if (requestedSyncMode.equals(SyncMode.FAST)) {
      final boolean blockchainIsEmpty =
          blockchain.getChainHeadBlockNumber() != BlockHeader.GENESIS_BLOCK_NUMBER;
      actualSyncMode = blockchainIsEmpty ? SyncMode.FULL : SyncMode.FAST;
      if (!actualSyncMode.equals(requestedSyncMode)) {
        LOG.info(
            "Fast sync was requested, but cannot be enabled because the local blockchain is not empty.");
      }
    } else {
      actualSyncMode = requestedSyncMode;
    }

    return new SynchronizerConfiguration(
        requestedSyncMode,
        fastSyncPivotDistance,
        fastSyncFullValidationRate,
        fastSyncMinimumPeerCount,
        fastSyncMaximumPeerWaitTime,
        worldStateHashCountPerRequest,
        worldStateRequestParallelism,
        blockPropagationRange,
        Optional.of(actualSyncMode),
        downloaderChangeTargetThresholdByHeight,
        downloaderChangeTargetThresholdByTd,
        downloaderHeaderRequestSize,
        downloaderCheckpointTimeoutsPermitted,
        downloaderChainSegmentTimeoutsPermitted,
        downloaderChainSegmentSize,
        trailingPeerBlocksBehindThreshold,
        maxTrailingPeers,
        downloaderParallelism,
        transactionsParallelism,
        computationParallelism);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * The actual sync mode to be used.
   *
   * @return the sync mode
   */
  public SyncMode syncMode() {
    if (!syncMode.isPresent()) {
      throw new IllegalStateException(
          "Attempt to access sync mode without first validating configuration.");
    }
    return syncMode.get();
  }

  /**
   * The range of block numbers (relative to the current chain head and the best network block) that
   * are considered appropriate to import as new blocks are announced on the network.
   *
   * @return the range of blocks considered valid to import from the network, relative to the the
   *     current chain head.
   */
  public Range<Long> blockPropagationRange() {
    return blockPropagationRange;
  }

  /**
   * The distance from the chain head at which we should switch from fast sync to full sync.
   *
   * @return distance from the chain head at which we should switch from fast sync to full sync.
   */
  public int fastSyncPivotDistance() {
    return fastSyncPivotDistance;
  }

  public long downloaderChangeTargetThresholdByHeight() {
    return downloaderChangeTargetThresholdByHeight;
  }

  public UInt256 downloaderChangeTargetThresholdByTd() {
    return downloaderChangeTargetThresholdByTd;
  }

  public int downloaderHeaderRequestSize() {
    return downloaderHeaderRequestSize;
  }

  public int downloaderCheckpointTimeoutsPermitted() {
    return downloaderCheckpointTimeoutsPermitted;
  }

  public int downloaderChainSegmentTimeoutsPermitted() {
    return downloaderChainSegmentTimeoutsPermitted;
  }

  public int downloaderChainSegmentSize() {
    return downloaderChainSegmentSize;
  }

  /**
   * The number of blocks behind we allow a peer to be before considering them a trailing peer.
   *
   * @return the maximum number of blocks behind a peer can be while being considered current.
   */
  public long trailingPeerBlocksBehindThreshold() {
    return trailingPeerBlocksBehindThreshold;
  }

  public int maxTrailingPeers() {
    return maxTrailingPeers;
  }

  public int downloaderParallelism() {
    return downloaderParallelism;
  }

  public int transactionsParallelism() {
    return transactionsParallelism;
  }

  public int computationParallelism() {
    return computationParallelism;
  }

  /**
   * The rate at which blocks should be fully validated during fast sync. At a rate of 1f, all
   * blocks are fully validated. At rates less than 1f, a subset of blocks will undergo light-weight
   * validation.
   *
   * @return rate at which blocks should be fully validated during fast sync.
   */
  public float fastSyncFullValidationRate() {
    return fastSyncFullValidationRate;
  }

  public int getFastSyncMinimumPeerCount() {
    return fastSyncMinimumPeerCount;
  }

  public Duration getFastSyncMaximumPeerWaitTime() {
    return fastSyncMaximumPeerWaitTime;
  }

  public int getWorldStateHashCountPerRequest() {
    return worldStateHashCountPerRequest;
  }

  public int getWorldStateRequestParallelism() {
    return worldStateRequestParallelism;
  }

  public static class Builder {
    private SyncMode syncMode = SyncMode.FULL;
    private Range<Long> blockPropagationRange = Range.closed(-10L, 30L);
    private long downloaderChangeTargetThresholdByHeight = 20L;
    private UInt256 downloaderChangeTargetThresholdByTd = UInt256.of(1_000_000_000L);
    private int downloaderHeaderRequestSize = 10;
    private int downloaderCheckpointTimeoutsPermitted = 5;
    private int downloaderChainSegmentTimeoutsPermitted = 5;
    private int downloaderChainSegmentSize = 20;
    private long trailingPeerBlocksBehindThreshold;
    private int maxTrailingPeers = Integer.MAX_VALUE;
    private int downloaderParallelism = 2;
    private int transactionsParallelism = 2;
    private int computationParallelism = Runtime.getRuntime().availableProcessors();
    private int fastSyncPivotDistance = DEFAULT_PIVOT_DISTANCE_FROM_HEAD;
    private float fastSyncFullValidationRate = DEFAULT_FULL_VALIDATION_RATE;
    private int fastSyncMinimumPeerCount = DEFAULT_FAST_SYNC_MINIMUM_PEERS;
    private Duration fastSyncMaximumPeerWaitTime = DEFAULT_FAST_SYNC_MAXIMUM_PEER_WAIT_TIME;

    public Builder fastSyncPivotDistance(final int distance) {
      fastSyncPivotDistance = distance;
      return this;
    }

    public Builder fastSyncFastSyncFullValidationRate(final float rate) {
      this.fastSyncFullValidationRate = rate;
      return this;
    }

    public Builder syncMode(final SyncMode mode) {
      this.syncMode = mode;
      return this;
    }

    public Builder downloaderChangeTargetThresholdByHeight(
        final long downloaderChangeTargetThresholdByHeight) {
      this.downloaderChangeTargetThresholdByHeight = downloaderChangeTargetThresholdByHeight;
      return this;
    }

    public Builder downloaderChangeTargetThresholdByTd(
        final UInt256 downloaderChangeTargetThresholdByTd) {
      this.downloaderChangeTargetThresholdByTd = downloaderChangeTargetThresholdByTd;
      return this;
    }

    public Builder downloaderHeadersRequestSize(final int downloaderHeaderRequestSize) {
      this.downloaderHeaderRequestSize = downloaderHeaderRequestSize;
      return this;
    }

    public Builder downloaderCheckpointTimeoutsPermitted(
        final int downloaderCheckpointTimeoutsPermitted) {
      this.downloaderCheckpointTimeoutsPermitted = downloaderCheckpointTimeoutsPermitted;
      return this;
    }

    public Builder downloaderChainSegmentTimeoutsPermitted(
        final int downloaderChainSegmentTimeoutsPermitted) {
      this.downloaderChainSegmentTimeoutsPermitted = downloaderChainSegmentTimeoutsPermitted;
      return this;
    }

    public Builder downloaderChainSegmentSize(final int downloaderChainSegmentSize) {
      this.downloaderChainSegmentSize = downloaderChainSegmentSize;
      return this;
    }

    public Builder blockPropagationRange(final long min, final long max) {
      checkArgument(min < max, "Invalid range: min must be less than max.");
      blockPropagationRange = Range.closed(min, max);
      return this;
    }

    public Builder trailingPeerBlocksBehindThreshold(final long trailingPeerBlocksBehindThreshold) {
      this.trailingPeerBlocksBehindThreshold = trailingPeerBlocksBehindThreshold;
      return this;
    }

    public Builder maxTrailingPeers(final int maxTrailingPeers) {
      this.maxTrailingPeers = maxTrailingPeers;
      return this;
    }

    public Builder downloaderParallelisim(final int downloaderParallelism) {
      this.downloaderParallelism = downloaderParallelism;
      return this;
    }

    public Builder transactionsParallelism(final int transactionsParallelism) {
      this.transactionsParallelism = transactionsParallelism;
      return this;
    }

    public Builder computationParallelism(final int computationParallelism) {
      this.computationParallelism = computationParallelism;
      return this;
    }

    public Builder fastSyncMinimumPeerCount(final int fastSyncMinimumPeerCount) {
      this.fastSyncMinimumPeerCount = fastSyncMinimumPeerCount;
      return this;
    }

    public Builder fastSyncMaximumPeerWaitTime(final Duration fastSyncMaximumPeerWaitTime) {
      this.fastSyncMaximumPeerWaitTime = fastSyncMaximumPeerWaitTime;
      return this;
    }

    public SynchronizerConfiguration build() {
      return new SynchronizerConfiguration(
          syncMode,
          fastSyncPivotDistance,
          fastSyncFullValidationRate,
          fastSyncMinimumPeerCount,
          fastSyncMaximumPeerWaitTime,
          DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST,
          DEFAULT_WORLD_STATE_REQUEST_PARALLELISM,
          blockPropagationRange,
          Optional.empty(),
          downloaderChangeTargetThresholdByHeight,
          downloaderChangeTargetThresholdByTd,
          downloaderHeaderRequestSize,
          downloaderCheckpointTimeoutsPermitted,
          downloaderChainSegmentTimeoutsPermitted,
          downloaderChainSegmentSize,
          trailingPeerBlocksBehindThreshold,
          maxTrailingPeers,
          downloaderParallelism,
          transactionsParallelism,
          computationParallelism);
    }
  }
}
