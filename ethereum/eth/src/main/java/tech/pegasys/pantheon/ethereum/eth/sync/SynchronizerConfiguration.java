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

import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import picocli.CommandLine;

public class SynchronizerConfiguration {

  // TODO: Determine reasonable defaults here
  private static final int DEFAULT_PIVOT_DISTANCE_FROM_HEAD = 50;
  private static final float DEFAULT_FULL_VALIDATION_RATE = .1f;
  private static final int DEFAULT_FAST_SYNC_MINIMUM_PEERS = 5;
  private static final int DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST = 384;
  private static final int DEFAULT_WORLD_STATE_REQUEST_PARALLELISM = 10;
  private static final int DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS = 1000;
  private static final long DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING =
      TimeUnit.MINUTES.toMillis(5);

  // Fast sync config
  private final int fastSyncPivotDistance;
  private final float fastSyncFullValidationRate;
  private final int fastSyncMinimumPeerCount;
  private final int worldStateHashCountPerRequest;
  private final int worldStateRequestParallelism;
  private final int worldStateMaxRequestsWithoutProgress;

  // Block propagation config
  private final Range<Long> blockPropagationRange;

  // General config
  private final SyncMode syncMode;

  // Downloader config
  private final long downloaderChangeTargetThresholdByHeight;
  private final UInt256 downloaderChangeTargetThresholdByTd;
  private final int downloaderHeaderRequestSize;
  private final int downloaderCheckpointTimeoutsPermitted;
  private final int downloaderChainSegmentSize;
  private final int downloaderParallelism;
  private final int transactionsParallelism;
  private final int computationParallelism;
  private final int maxTrailingPeers;
  private final long worldStateMinMillisBeforeStalling;

  private SynchronizerConfiguration(
      final int fastSyncPivotDistance,
      final float fastSyncFullValidationRate,
      final int fastSyncMinimumPeerCount,
      final int worldStateHashCountPerRequest,
      final int worldStateRequestParallelism,
      final int worldStateMaxRequestsWithoutProgress,
      final long worldStateMinMillisBeforeStalling,
      final Range<Long> blockPropagationRange,
      final SyncMode syncMode,
      final long downloaderChangeTargetThresholdByHeight,
      final UInt256 downloaderChangeTargetThresholdByTd,
      final int downloaderHeaderRequestSize,
      final int downloaderCheckpointTimeoutsPermitted,
      final int downloaderChainSegmentSize,
      final int downloaderParallelism,
      final int transactionsParallelism,
      final int computationParallelism,
      final int maxTrailingPeers) {
    this.fastSyncPivotDistance = fastSyncPivotDistance;
    this.fastSyncFullValidationRate = fastSyncFullValidationRate;
    this.fastSyncMinimumPeerCount = fastSyncMinimumPeerCount;
    this.worldStateHashCountPerRequest = worldStateHashCountPerRequest;
    this.worldStateRequestParallelism = worldStateRequestParallelism;
    this.worldStateMaxRequestsWithoutProgress = worldStateMaxRequestsWithoutProgress;
    this.worldStateMinMillisBeforeStalling = worldStateMinMillisBeforeStalling;
    this.blockPropagationRange = blockPropagationRange;
    this.syncMode = syncMode;
    this.downloaderChangeTargetThresholdByHeight = downloaderChangeTargetThresholdByHeight;
    this.downloaderChangeTargetThresholdByTd = downloaderChangeTargetThresholdByTd;
    this.downloaderHeaderRequestSize = downloaderHeaderRequestSize;
    this.downloaderCheckpointTimeoutsPermitted = downloaderCheckpointTimeoutsPermitted;
    this.downloaderChainSegmentSize = downloaderChainSegmentSize;
    this.downloaderParallelism = downloaderParallelism;
    this.transactionsParallelism = transactionsParallelism;
    this.computationParallelism = computationParallelism;
    this.maxTrailingPeers = maxTrailingPeers;
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
    return syncMode;
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

  public int downloaderChainSegmentSize() {
    return downloaderChainSegmentSize;
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

  public int getWorldStateHashCountPerRequest() {
    return worldStateHashCountPerRequest;
  }

  public int getWorldStateRequestParallelism() {
    return worldStateRequestParallelism;
  }

  public int getWorldStateMaxRequestsWithoutProgress() {
    return worldStateMaxRequestsWithoutProgress;
  }

  public long getWorldStateMinMillisBeforeStalling() {
    return worldStateMinMillisBeforeStalling;
  }

  public int getMaxTrailingPeers() {
    return maxTrailingPeers;
  }

  public static class Builder {
    private SyncMode syncMode = SyncMode.FULL;
    private int fastSyncMinimumPeerCount = DEFAULT_FAST_SYNC_MINIMUM_PEERS;
    private int maxTrailingPeers = Integer.MAX_VALUE;

    @CommandLine.Option(
        names = "--Xsynchronizer-block-propagation-range",
        hidden = true,
        defaultValue = "-10..30",
        paramLabel = "<LONG>..<LONG>",
        description =
            "Range around chain head where inbound blocks are propagated (default: ${DEFAULT-VALUE})")
    public void parseBlockPropagationRange(final String arg) {
      // Use a method instead of a registered converter because of generics.
      checkArgument(
          arg.matches("-?\\d+\\.\\.-?\\d+"),
          "--Xsynchronizer-block-propagation-range should be of the form '<LONG>..<LONG>'");
      final Iterator<String> ends = Splitter.on("..").split(arg).iterator();
      blockPropagationRange =
          Range.closed(Long.parseLong(ends.next()), Long.parseLong(ends.next()));
    }

    private Range<Long> blockPropagationRange = Range.closed(-10L, 30L);

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-change-target-threshold-by-height",
        hidden = true,
        defaultValue = "200",
        paramLabel = "<LONG>",
        description =
            "Minimum height difference before switching fast sync download peers (default: ${DEFAULT-VALUE})")
    private long downloaderChangeTargetThresholdByHeight = 200L;

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-change-target-threshold-by-td",
        hidden = true,
        defaultValue = "1000000000000000000",
        paramLabel = "<UINT256>",
        description =
            "Minimum total difficulty difference before switching fast sync download peers (default: ${DEFAULT-VALUE})")
    private UInt256 downloaderChangeTargetThresholdByTd = UInt256.of(1_000_000_000_000_000_000L);

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-header-request-size",
        hidden = true,
        defaultValue = "200",
        paramLabel = "<INTEGER>",
        description = "Number of headers to request per packet (default: ${DEFAULT-VALUE})")
    private int downloaderHeaderRequestSize = 200;

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-checkpoint-timeouts-permitted",
        hidden = true,
        defaultValue = "5",
        paramLabel = "<INTEGER>",
        description =
            "Number of tries to attempt to download checkpoints before stopping (default: ${DEFAULT-VALUE})")
    private int downloaderCheckpointTimeoutsPermitted = 5;

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-chain-segment-size",
        hidden = true,
        defaultValue = "200",
        paramLabel = "<INTEGER>",
        description = "Distance between checkpoint headers (default: ${DEFAULT-VALUE})")
    private int downloaderChainSegmentSize = 200;

    @CommandLine.Option(
        names = "--Xsynchronizer-downloader-parallelism",
        hidden = true,
        defaultValue = "4",
        paramLabel = "<INTEGER>",
        description =
            "Number of threads to provide to chain downloader (default: ${DEFAULT-VALUE})")
    private int downloaderParallelism = 4;

    @CommandLine.Option(
        names = "--Xsynchronizer-transactions-parallelism",
        hidden = true,
        defaultValue = "2",
        paramLabel = "<INTEGER>",
        description =
            "Number of threads to commit to transaction processing (default: ${DEFAULT-VALUE})")
    private int transactionsParallelism = 2;

    @CommandLine.Option(
        names = "--Xsynchronizer-computation-parallelism",
        hidden = true,
        paramLabel = "<INTEGER>",
        description =
            "Number of threads to make available for bulk hash computations durring downloads (default: # of processors)")
    private int computationParallelism = Runtime.getRuntime().availableProcessors();

    @CommandLine.Option(
        names = "--Xsynchronizer-fast-sync-pivot-distance",
        hidden = true,
        defaultValue = "50",
        paramLabel = "<INTEGER>",
        description =
            "Distance from initial chain head to fast sync target (default: ${DEFAULT-VALUE})")
    private int fastSyncPivotDistance = DEFAULT_PIVOT_DISTANCE_FROM_HEAD;

    @CommandLine.Option(
        names = "--Xsynchronizer-fast-sync-full-validation-rate",
        hidden = true,
        defaultValue = "0.1",
        paramLabel = "<FLOAT>",
        description =
            "Fraction of headers fast sync will fully validate (default: ${DEFAULT-VALUE})")
    private float fastSyncFullValidationRate = DEFAULT_FULL_VALIDATION_RATE;

    @CommandLine.Option(
        names = "--Xsynchronizer-world-state-hash-count-per-request",
        hidden = true,
        defaultValue = "348",
        paramLabel = "<INTEGER>",
        description =
            "Fast sync world state hashes queried per request (default: ${DEFAULT-VALUE})")
    private int worldStateHashCountPerRequest = DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST;

    @CommandLine.Option(
        names = "--Xsynchronizer-world-state-request-parallelism",
        hidden = true,
        defaultValue = "10",
        paramLabel = "<INTEGER>",
        description =
            "Number of concurrent requests to use when downloading fast sync world state (default: ${DEFAULT-VALUE})")
    private int worldStateRequestParallelism = DEFAULT_WORLD_STATE_REQUEST_PARALLELISM;

    @CommandLine.Option(
        names = "--Xsynchronizer-world-state-max-requests-without-progress",
        hidden = true,
        defaultValue = "1000",
        paramLabel = "<INTEGER>",
        description =
            "Number of world state requests accepted without progress before considering the download stalled (default: ${DEFAULT-VALUE})")
    private int worldStateMaxRequestsWithoutProgress =
        DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS;

    @CommandLine.Option(
        names = "--Xsynchronizer-world-state-min-millis-before-stalling",
        hidden = true,
        defaultValue = "300000",
        paramLabel = "<LONG>",
        description =
            "Minimum time in ms without progress before considering a world state download as stalled (default: ${DEFAULT-VALUE})")
    private long worldStateMinMillisBeforeStalling = DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING;

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

    public Builder downloaderChainSegmentSize(final int downloaderChainSegmentSize) {
      this.downloaderChainSegmentSize = downloaderChainSegmentSize;
      return this;
    }

    public Builder blockPropagationRange(final long min, final long max) {
      checkArgument(min < max, "Invalid range: min must be less than max.");
      blockPropagationRange = Range.closed(min, max);
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

    public Builder worldStateHashCountPerRequest(final int worldStateHashCountPerRequest) {
      this.worldStateHashCountPerRequest = worldStateHashCountPerRequest;
      return this;
    }

    public Builder worldStateRequestParallelism(final int worldStateRequestParallelism) {
      this.worldStateRequestParallelism = worldStateRequestParallelism;
      return this;
    }

    public Builder worldStateMaxRequestsWithoutProgress(
        final int worldStateMaxRequestsWithoutProgress) {
      this.worldStateMaxRequestsWithoutProgress = worldStateMaxRequestsWithoutProgress;
      return this;
    }

    public Builder worldStateMinMillisBeforeStalling(final long worldStateMinMillisBeforeStalling) {
      this.worldStateMinMillisBeforeStalling = worldStateMinMillisBeforeStalling;
      return this;
    }

    public Builder maxTrailingPeers(final int maxTailingPeers) {
      this.maxTrailingPeers = maxTailingPeers;
      return this;
    }

    public SynchronizerConfiguration build() {
      return new SynchronizerConfiguration(
          fastSyncPivotDistance,
          fastSyncFullValidationRate,
          fastSyncMinimumPeerCount,
          worldStateHashCountPerRequest,
          worldStateRequestParallelism,
          worldStateMaxRequestsWithoutProgress,
          worldStateMinMillisBeforeStalling,
          blockPropagationRange,
          syncMode,
          downloaderChangeTargetThresholdByHeight,
          downloaderChangeTargetThresholdByTd,
          downloaderHeaderRequestSize,
          downloaderCheckpointTimeoutsPermitted,
          downloaderChainSegmentSize,
          downloaderParallelism,
          transactionsParallelism,
          computationParallelism,
          maxTrailingPeers);
    }
  }
}
