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
package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.options.OptionParser;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.ImmutableSnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Range;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;

public class SynchronizerOptions implements CLIOptions<SynchronizerConfiguration.Builder> {
  private static final String BLOCK_PROPAGATION_RANGE_FLAG =
      "--Xsynchronizer-block-propagation-range";
  private static final String DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG =
      "--Xsynchronizer-downloader-change-target-threshold-by-height";
  private static final String DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG =
      "--Xsynchronizer-downloader-change-target-threshold-by-td";
  private static final String DOWNLOADER_HEADER_REQUEST_SIZE_FLAG =
      "--Xsynchronizer-downloader-header-request-size";
  private static final String DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG =
      "--Xsynchronizer-downloader-checkpoint-timeouts-permitted";
  private static final String DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG =
      "--Xsynchronizer-downloader-chain-segment-size";
  private static final String DOWNLOADER_PARALLELISM_FLAG =
      "--Xsynchronizer-downloader-parallelism";
  private static final String TRANSACTIONS_PARALLELISM_FLAG =
      "--Xsynchronizer-transactions-parallelism";
  private static final String COMPUTATION_PARALLELISM_FLAG =
      "--Xsynchronizer-computation-parallelism";
  private static final String PIVOT_DISTANCE_FROM_HEAD_FLAG =
      "--Xsynchronizer-fast-sync-pivot-distance";
  private static final String FULL_VALIDATION_RATE_FLAG =
      "--Xsynchronizer-fast-sync-full-validation-rate";
  private static final String WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG =
      "--Xsynchronizer-world-state-hash-count-per-request";
  private static final String WORLD_STATE_REQUEST_PARALLELISM_FLAG =
      "--Xsynchronizer-world-state-request-parallelism";
  private static final String WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG =
      "--Xsynchronizer-world-state-max-requests-without-progress";
  private static final String WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG =
      "--Xsynchronizer-world-state-min-millis-before-stalling";
  private static final String WORLD_STATE_TASK_CACHE_SIZE_FLAG =
      "--Xsynchronizer-world-state-task-cache-size";

  private static final String SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG =
      "--Xsnapsync-synchronizer-pivot-block-window-validity";
  private static final String SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG =
      "--Xsnapsync-synchronizer-pivot-block-distance-before-caching";
  private static final String SNAP_STORAGE_COUNT_PER_REQUEST_FLAG =
      "--Xsnapsync-synchronizer-storage-count-per-request";
  private static final String SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG =
      "--Xsnapsync-synchronizer-bytecode-count-per-request";
  private static final String SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG =
      "--Xsnapsync-synchronizer-trienode-count-per-request";

  @CommandLine.Option(
      names = BLOCK_PROPAGATION_RANGE_FLAG,
      hidden = true,
      defaultValue = "-10..30",
      paramLabel = "<LONG>..<LONG>",
      description =
          "Range around chain head where inbound blocks are propagated (default: ${DEFAULT-VALUE})")
  public void parseBlockPropagationRange(final String arg) {
    blockPropagationRange = OptionParser.parseLongRange(arg);
  }

  private Range<Long> blockPropagationRange =
      SynchronizerConfiguration.DEFAULT_BLOCK_PROPAGATION_RANGE;

  @CommandLine.Option(
      names = DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG,
      hidden = true,
      defaultValue = "200",
      paramLabel = "<LONG>",
      description =
          "Minimum height difference before switching fast sync download peers (default: ${DEFAULT-VALUE})")
  private long downloaderChangeTargetThresholdByHeight =
      SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT;

  @CommandLine.Option(
      names = DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG,
      hidden = true,
      defaultValue = "1000000000000000000",
      paramLabel = "<UINT256>",
      description =
          "Minimum total difficulty difference before switching fast sync download peers (default: ${DEFAULT-VALUE})")
  private UInt256 downloaderChangeTargetThresholdByTd =
      SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD;

  @CommandLine.Option(
      names = DOWNLOADER_HEADER_REQUEST_SIZE_FLAG,
      hidden = true,
      defaultValue = "200",
      paramLabel = "<INTEGER>",
      description = "Number of headers to request per packet (default: ${DEFAULT-VALUE})")
  private int downloaderHeaderRequestSize =
      SynchronizerConfiguration.DEFAULT_DOWNLOADER_HEADER_REQUEST_SIZE;

  @CommandLine.Option(
      names = DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG,
      hidden = true,
      defaultValue = "5",
      paramLabel = "<INTEGER>",
      description =
          "Number of tries to attempt to download checkpoints before stopping (default: ${DEFAULT-VALUE})")
  private int downloaderCheckpointTimeoutsPermitted =
      SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED;

  @CommandLine.Option(
      names = DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG,
      hidden = true,
      defaultValue = "200",
      paramLabel = "<INTEGER>",
      description = "Distance between checkpoint headers (default: ${DEFAULT-VALUE})")
  private int downloaderChainSegmentSize =
      SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHAIN_SEGMENT_SIZE;

  @CommandLine.Option(
      names = DOWNLOADER_PARALLELISM_FLAG,
      hidden = true,
      defaultValue = "4",
      paramLabel = "<INTEGER>",
      description = "Number of threads to provide to chain downloader (default: ${DEFAULT-VALUE})")
  private int downloaderParallelism = SynchronizerConfiguration.DEFAULT_DOWNLOADER_PARALLELISM;

  @CommandLine.Option(
      names = TRANSACTIONS_PARALLELISM_FLAG,
      hidden = true,
      defaultValue = "2",
      paramLabel = "<INTEGER>",
      description =
          "Number of threads to commit to transaction processing (default: ${DEFAULT-VALUE})")
  private int transactionsParallelism = SynchronizerConfiguration.DEFAULT_TRANSACTIONS_PARALLELISM;

  @CommandLine.Option(
      names = COMPUTATION_PARALLELISM_FLAG,
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Number of threads to make available for bulk hash computations during downloads (default: # of processors)")
  private int computationParallelism = Runtime.getRuntime().availableProcessors();

  @CommandLine.Option(
      names = PIVOT_DISTANCE_FROM_HEAD_FLAG,
      hidden = true,
      defaultValue = "50",
      paramLabel = "<INTEGER>",
      description =
          "Distance from initial chain head to fast sync target (default: ${DEFAULT-VALUE})")
  private int fastSyncPivotDistance = SynchronizerConfiguration.DEFAULT_PIVOT_DISTANCE_FROM_HEAD;

  @CommandLine.Option(
      names = FULL_VALIDATION_RATE_FLAG,
      hidden = true,
      defaultValue = "0.1",
      paramLabel = "<FLOAT>",
      description = "Fraction of headers fast sync will fully validate (default: ${DEFAULT-VALUE})")
  private float fastSyncFullValidationRate = SynchronizerConfiguration.DEFAULT_FULL_VALIDATION_RATE;

  @CommandLine.Option(
      names = WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG,
      hidden = true,
      defaultValue = "384",
      paramLabel = "<INTEGER>",
      description = "Fast sync world state hashes queried per request (default: ${DEFAULT-VALUE})")
  private int worldStateHashCountPerRequest =
      SynchronizerConfiguration.DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST;

  @CommandLine.Option(
      names = WORLD_STATE_REQUEST_PARALLELISM_FLAG,
      hidden = true,
      defaultValue = "10",
      paramLabel = "<INTEGER>",
      description =
          "Number of concurrent requests to use when downloading fast sync world state (default: ${DEFAULT-VALUE})")
  private int worldStateRequestParallelism =
      SynchronizerConfiguration.DEFAULT_WORLD_STATE_REQUEST_PARALLELISM;

  @CommandLine.Option(
      names = WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG,
      hidden = true,
      defaultValue = "1000",
      paramLabel = "<INTEGER>",
      description =
          "Number of world state requests accepted without progress before considering the download stalled (default: ${DEFAULT-VALUE})")
  private int worldStateMaxRequestsWithoutProgress =
      SynchronizerConfiguration.DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS;

  @CommandLine.Option(
      names = WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG,
      hidden = true,
      defaultValue = "300000",
      paramLabel = "<LONG>",
      description =
          "Minimum time in ms without progress before considering a world state download as stalled (default: ${DEFAULT-VALUE})")
  private long worldStateMinMillisBeforeStalling =
      SynchronizerConfiguration.DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING;

  @CommandLine.Option(
      names = WORLD_STATE_TASK_CACHE_SIZE_FLAG,
      hidden = true,
      defaultValue = "1000000",
      paramLabel = "<INTEGER>",
      description =
          "The max number of pending node data requests cached in-memory during fast sync world state download. (default: ${DEFAULT-VALUE})")
  private int worldStateTaskCacheSize =
      SynchronizerConfiguration.DEFAULT_WORLD_STATE_TASK_CACHE_SIZE;

  @CommandLine.Option(
      names = SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG,
      hidden = true,
      defaultValue = "126",
      paramLabel = "<INTEGER>",
      description =
          "The size of the pivot block window before having to change it (default: ${DEFAULT-VALUE})")
  private int snapsyncPivotBlockWindowValidity =
      SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY;

  @CommandLine.Option(
      names = SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG,
      hidden = true,
      defaultValue = "60",
      paramLabel = "<INTEGER>",
      description =
          "The distance from the head before loading a pivot block into the cache to have a ready pivot block when the window is finished (default: ${DEFAULT-VALUE})")
  private int snapsyncPivotBlockDistanceBeforeCaching =
      SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING;

  @CommandLine.Option(
      names = SNAP_STORAGE_COUNT_PER_REQUEST_FLAG,
      hidden = true,
      defaultValue = "384",
      paramLabel = "<INTEGER>",
      description = "Snap sync sync storage queried per request (default: ${DEFAULT-VALUE})")
  private int snapsyncStorageCountPerRequest =
      SnapSyncConfiguration.DEFAULT_STORAGE_COUNT_PER_REQUEST;

  @CommandLine.Option(
      names = SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG,
      hidden = true,
      defaultValue = "84",
      paramLabel = "<INTEGER>",
      description = "Snap sync sync bytecode queried per request (default: ${DEFAULT-VALUE})")
  private int snapsyncBytecodeCountPerRequest =
      SnapSyncConfiguration.DEFAULT_BYTECODE_COUNT_PER_REQUEST;

  @CommandLine.Option(
      names = SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG,
      hidden = true,
      defaultValue = "384",
      paramLabel = "<INTEGER>",
      description = "Snap sync sync trie node queried per request (default: ${DEFAULT-VALUE})")
  private int snapsyncTrieNodeCountPerRequest =
      SnapSyncConfiguration.DEFAULT_TRIENODE_COUNT_PER_REQUEST;

  private SynchronizerOptions() {}

  public static SynchronizerOptions create() {
    return new SynchronizerOptions();
  }

  public static SynchronizerOptions fromConfig(final SynchronizerConfiguration config) {
    final SynchronizerOptions options = new SynchronizerOptions();
    options.blockPropagationRange = config.getBlockPropagationRange();
    options.downloaderChangeTargetThresholdByHeight =
        config.getDownloaderChangeTargetThresholdByHeight();
    options.downloaderChangeTargetThresholdByTd = config.getDownloaderChangeTargetThresholdByTd();
    options.downloaderHeaderRequestSize = config.getDownloaderHeaderRequestSize();
    options.downloaderCheckpointTimeoutsPermitted =
        config.getDownloaderCheckpointTimeoutsPermitted();
    options.downloaderChainSegmentSize = config.getDownloaderChainSegmentSize();
    options.downloaderParallelism = config.getDownloaderParallelism();
    options.transactionsParallelism = config.getTransactionsParallelism();
    options.computationParallelism = config.getComputationParallelism();
    options.fastSyncPivotDistance = config.getFastSyncPivotDistance();
    options.fastSyncFullValidationRate = config.getFastSyncFullValidationRate();
    options.worldStateHashCountPerRequest = config.getWorldStateHashCountPerRequest();
    options.worldStateRequestParallelism = config.getWorldStateRequestParallelism();
    options.worldStateMaxRequestsWithoutProgress = config.getWorldStateMaxRequestsWithoutProgress();
    options.worldStateMinMillisBeforeStalling = config.getWorldStateMinMillisBeforeStalling();
    options.worldStateTaskCacheSize = config.getWorldStateTaskCacheSize();
    options.snapsyncPivotBlockWindowValidity =
        config.getSnapSyncConfiguration().getPivotBlockWindowValidity();
    options.snapsyncPivotBlockDistanceBeforeCaching =
        config.getSnapSyncConfiguration().getPivotBlockDistanceBeforeCaching();
    options.snapsyncStorageCountPerRequest =
        config.getSnapSyncConfiguration().getStorageCountPerRequest();
    options.snapsyncBytecodeCountPerRequest =
        config.getSnapSyncConfiguration().getBytecodeCountPerRequest();
    options.snapsyncTrieNodeCountPerRequest =
        config.getSnapSyncConfiguration().getTrienodeCountPerRequest();
    return options;
  }

  @Override
  public SynchronizerConfiguration.Builder toDomainObject() {
    final SynchronizerConfiguration.Builder builder = SynchronizerConfiguration.builder();
    builder.blockPropagationRange(blockPropagationRange);
    builder.downloaderChangeTargetThresholdByHeight(downloaderChangeTargetThresholdByHeight);
    builder.downloaderChangeTargetThresholdByTd(downloaderChangeTargetThresholdByTd);
    builder.downloaderHeadersRequestSize(downloaderHeaderRequestSize);
    builder.downloaderCheckpointTimeoutsPermitted(downloaderCheckpointTimeoutsPermitted);
    builder.downloaderChainSegmentSize(downloaderChainSegmentSize);
    builder.downloaderParallelism(downloaderParallelism);
    builder.transactionsParallelism(transactionsParallelism);
    builder.computationParallelism(computationParallelism);
    builder.fastSyncPivotDistance(fastSyncPivotDistance);
    builder.fastSyncFullValidationRate(fastSyncFullValidationRate);
    builder.worldStateHashCountPerRequest(worldStateHashCountPerRequest);
    builder.worldStateRequestParallelism(worldStateRequestParallelism);
    builder.worldStateMaxRequestsWithoutProgress(worldStateMaxRequestsWithoutProgress);
    builder.worldStateMinMillisBeforeStalling(worldStateMinMillisBeforeStalling);
    builder.worldStateTaskCacheSize(worldStateTaskCacheSize);
    builder.snapSyncConfiguration(
        ImmutableSnapSyncConfiguration.builder()
            .pivotBlockWindowValidity(snapsyncPivotBlockWindowValidity)
            .pivotBlockDistanceBeforeCaching(snapsyncPivotBlockDistanceBeforeCaching)
            .storageCountPerRequest(snapsyncStorageCountPerRequest)
            .bytecodeCountPerRequest(snapsyncBytecodeCountPerRequest)
            .trienodeCountPerRequest(snapsyncTrieNodeCountPerRequest)
            .build());

    return builder;
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        BLOCK_PROPAGATION_RANGE_FLAG,
        OptionParser.format(blockPropagationRange),
        DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG,
        OptionParser.format(downloaderChangeTargetThresholdByHeight),
        DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG,
        OptionParser.format(downloaderChangeTargetThresholdByTd),
        DOWNLOADER_HEADER_REQUEST_SIZE_FLAG,
        OptionParser.format(downloaderHeaderRequestSize),
        DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG,
        OptionParser.format(downloaderCheckpointTimeoutsPermitted),
        DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG,
        OptionParser.format(downloaderChainSegmentSize),
        DOWNLOADER_PARALLELISM_FLAG,
        OptionParser.format(downloaderParallelism),
        TRANSACTIONS_PARALLELISM_FLAG,
        OptionParser.format(transactionsParallelism),
        COMPUTATION_PARALLELISM_FLAG,
        OptionParser.format(computationParallelism),
        PIVOT_DISTANCE_FROM_HEAD_FLAG,
        OptionParser.format(fastSyncPivotDistance),
        FULL_VALIDATION_RATE_FLAG,
        OptionParser.format(fastSyncFullValidationRate),
        WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG,
        OptionParser.format(worldStateHashCountPerRequest),
        WORLD_STATE_REQUEST_PARALLELISM_FLAG,
        OptionParser.format(worldStateRequestParallelism),
        WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG,
        OptionParser.format(worldStateMaxRequestsWithoutProgress),
        WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG,
        OptionParser.format(worldStateMinMillisBeforeStalling),
        WORLD_STATE_TASK_CACHE_SIZE_FLAG,
        OptionParser.format(worldStateTaskCacheSize),
        SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG,
        OptionParser.format(snapsyncPivotBlockWindowValidity),
        SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG,
        OptionParser.format(snapsyncPivotBlockDistanceBeforeCaching),
        SNAP_STORAGE_COUNT_PER_REQUEST_FLAG,
        OptionParser.format(snapsyncStorageCountPerRequest),
        SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG,
        OptionParser.format(snapsyncBytecodeCountPerRequest),
        SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG,
        OptionParser.format(snapsyncTrieNodeCountPerRequest));
  }
}
