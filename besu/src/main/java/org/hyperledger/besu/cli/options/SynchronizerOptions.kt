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
package org.hyperledger.besu.cli.options

import com.google.common.collect.Range
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.cli.options.OptionParser.format
import org.hyperledger.besu.cli.options.OptionParser.parseLongRange
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.sync.snapsync.ImmutableSnapSyncConfiguration
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration
import picocli.CommandLine
import java.util.*

/** The Synchronizer Cli options.  */
class SynchronizerOptions private constructor() : CLIOptions<SynchronizerConfiguration.Builder?> {
    /**
     * Parse block propagation range.
     *
     * @param arg the range such as -10..30
     */
    @CommandLine.Option(
        names = [BLOCK_PROPAGATION_RANGE_FLAG],
        hidden = true,
        defaultValue = "-10..30",
        paramLabel = "<LONG>..<LONG>",
        description = ["Range around chain head where inbound blocks are propagated (default: \${DEFAULT-VALUE})"]
    )
    fun parseBlockPropagationRange(arg: String) {
        blockPropagationRange = parseLongRange(arg)
    }

    private var blockPropagationRange: Range<Long> = SynchronizerConfiguration.DEFAULT_BLOCK_PROPAGATION_RANGE

    @CommandLine.Option(
        names = [DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG],
        hidden = true,
        paramLabel = "<LONG>",
        description = ["Minimum height difference before switching fast sync download peers (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderChangeTargetThresholdByHeight =
        SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT

    @CommandLine.Option(
        names = [DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG],
        hidden = true,
        paramLabel = "<UINT256>",
        description = ["Minimum total difficulty difference before switching fast sync download peers (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderChangeTargetThresholdByTd: UInt256 =
        SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD

    @CommandLine.Option(
        names = [DOWNLOADER_HEADER_REQUEST_SIZE_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of headers to request per packet (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderHeaderRequestSize = SynchronizerConfiguration.DEFAULT_DOWNLOADER_HEADER_REQUEST_SIZE

    @CommandLine.Option(
        names = [DOWNLOADER_CHECKPOINT_RETRIES_FLAG, DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of tries to attempt to download checkpoints before stopping (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderCheckpointRetries = SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED

    @CommandLine.Option(
        names = [DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Distance between checkpoint headers (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderChainSegmentSize = SynchronizerConfiguration.DEFAULT_DOWNLOADER_CHAIN_SEGMENT_SIZE

    @CommandLine.Option(
        names = [DOWNLOADER_PARALLELISM_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of threads to provide to chain downloader (default: \${DEFAULT-VALUE})"]
    )
    private var downloaderParallelism = SynchronizerConfiguration.DEFAULT_DOWNLOADER_PARALLELISM

    @CommandLine.Option(
        names = [TRANSACTIONS_PARALLELISM_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of threads to commit to transaction processing (default: \${DEFAULT-VALUE})"]
    )
    private var transactionsParallelism = SynchronizerConfiguration.DEFAULT_TRANSACTIONS_PARALLELISM

    @CommandLine.Option(
        names = [COMPUTATION_PARALLELISM_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of threads to make available for bulk hash computations during downloads (default: # of processors)"]
    )
    private var computationParallelism = Runtime.getRuntime().availableProcessors()

    @CommandLine.Option(
        names = [PIVOT_DISTANCE_FROM_HEAD_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Distance from initial chain head to fast sync target (default: \${DEFAULT-VALUE})"]
    )
    private var fastSyncPivotDistance = SynchronizerConfiguration.DEFAULT_PIVOT_DISTANCE_FROM_HEAD

    @CommandLine.Option(
        names = [FULL_VALIDATION_RATE_FLAG],
        hidden = true,
        paramLabel = "<FLOAT>",
        description = ["Fraction of headers fast sync will fully validate (default: \${DEFAULT-VALUE})"]
    )
    private var fastSyncFullValidationRate = SynchronizerConfiguration.DEFAULT_FULL_VALIDATION_RATE

    @CommandLine.Option(
        names = [WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Fast sync world state hashes queried per request (default: \${DEFAULT-VALUE})"]
    )
    private var worldStateHashCountPerRequest = SynchronizerConfiguration.DEFAULT_WORLD_STATE_HASH_COUNT_PER_REQUEST

    @CommandLine.Option(
        names = [WORLD_STATE_REQUEST_PARALLELISM_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of concurrent requests to use when downloading fast sync world state (default: \${DEFAULT-VALUE})"]
    )
    private var worldStateRequestParallelism = SynchronizerConfiguration.DEFAULT_WORLD_STATE_REQUEST_PARALLELISM

    @CommandLine.Option(
        names = [WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Number of world state requests accepted without progress before considering the download stalled (default: \${DEFAULT-VALUE})"]
    )
    private var worldStateMaxRequestsWithoutProgress =
        SynchronizerConfiguration.DEFAULT_WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS

    @CommandLine.Option(
        names = [WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG],
        hidden = true,
        paramLabel = "<LONG>",
        description = ["Minimum time in ms without progress before considering a world state download as stalled (default: \${DEFAULT-VALUE})"]
    )
    private var worldStateMinMillisBeforeStalling =
        SynchronizerConfiguration.DEFAULT_WORLD_STATE_MIN_MILLIS_BEFORE_STALLING

    @CommandLine.Option(
        names = [WORLD_STATE_TASK_CACHE_SIZE_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["The max number of pending node data requests cached in-memory during fast sync world state download. (default: \${DEFAULT-VALUE})"]
    )
    private var worldStateTaskCacheSize = SynchronizerConfiguration.DEFAULT_WORLD_STATE_TASK_CACHE_SIZE

    @CommandLine.Option(
        names = [SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["The size of the pivot block window before having to change it (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncPivotBlockWindowValidity = SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_WINDOW_VALIDITY

    @CommandLine.Option(
        names = [SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["The distance from the head before loading a pivot block into the cache to have a ready pivot block when the window is finished (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncPivotBlockDistanceBeforeCaching =
        SnapSyncConfiguration.DEFAULT_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING

    @CommandLine.Option(
        names = [SNAP_STORAGE_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Snap sync storage queried per request (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncStorageCountPerRequest = SnapSyncConfiguration.DEFAULT_STORAGE_COUNT_PER_REQUEST

    @CommandLine.Option(
        names = [SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Snap sync bytecode queried per request (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncBytecodeCountPerRequest = SnapSyncConfiguration.DEFAULT_BYTECODE_COUNT_PER_REQUEST

    @CommandLine.Option(
        names = [SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Snap sync trie node queried per request (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncTrieNodeCountPerRequest = SnapSyncConfiguration.DEFAULT_TRIENODE_COUNT_PER_REQUEST

    @CommandLine.Option(
        names = [SNAP_FLAT_ACCOUNT_HEALED_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Snap sync flat accounts verified and healed per request (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncFlatAccountHealedCountPerRequest =
        SnapSyncConfiguration.DEFAULT_LOCAL_FLAT_ACCOUNT_COUNT_TO_HEAL_PER_REQUEST

    @CommandLine.Option(
        names = [SNAP_FLAT_STORAGE_HEALED_COUNT_PER_REQUEST_FLAG],
        hidden = true,
        paramLabel = "<INTEGER>",
        description = ["Snap sync flat slots verified and healed per request (default: \${DEFAULT-VALUE})"]
    )
    private var snapsyncFlatStorageHealedCountPerRequest =
        SnapSyncConfiguration.DEFAULT_LOCAL_FLAT_STORAGE_COUNT_TO_HEAL_PER_REQUEST

    /**
     * Flag to know whether the Snap sync server feature is enabled or disabled.
     *
     * @return true if snap sync server is enabled
     */
    @CommandLine.Option(
        names = [SNAP_SERVER_ENABLED_FLAG],
        hidden = true,
        paramLabel = "<Boolean>",
        arity = "0..1",
        description = ["Snap sync server enabled (default: \${DEFAULT-VALUE})"]
    )
    var isSnapsyncServerEnabled: Boolean = SnapSyncConfiguration.DEFAULT_SNAP_SERVER_ENABLED
        private set

    @CommandLine.Option(
        names = [CHECKPOINT_POST_MERGE_FLAG],
        hidden = true,
        description = ["Enable the sync to start from a post-merge block."]
    )
    private var checkpointPostMergeSyncEnabled = SynchronizerConfiguration.DEFAULT_CHECKPOINT_POST_MERGE_ENABLED

    /**
     * Flag to know whether the Snap sync should be enabled for a BFT chain
     *
     * @return true if snap sync for BFT is enabled
     */
    // TODO --Xsnapsync-bft-enabled is deprecated,
    // remove in a future release
    @CommandLine.Option(
        names = [SNAP_SYNC_BFT_ENABLED_FLAG],
        hidden = true,
        paramLabel = "<Boolean>",
        arity = "0..1",
        description = ["This option is now deprecated and ignored, and will be removed in future release. Snap sync for BFT is supported by default."]
    )
    val isSnapSyncBftEnabled: Boolean = SnapSyncConfiguration.DEFAULT_SNAP_SYNC_BFT_ENABLED

    /**
     * Flag to indicate whether the peer task system should be used where available
     *
     * @return true if the peer task system should be used where available
     */
    @CommandLine.Option(
        names = ["--Xpeertask-system-enabled"],
        hidden = true,
        description = ["Temporary feature toggle to enable using the new peertask system (default: \${DEFAULT-VALUE})"]
    )
    val isPeerTaskSystemEnabled: Boolean = false

    @CommandLine.Option(
        names = [SNAP_TRANSACTION_INDEXING_ENABLED_FLAG],
        hidden = true,
        paramLabel = "<Boolean>",
        arity = "0..1",
        description = ["Enable transaction indexing during snap sync. (default: \${DEFAULT-VALUE})"]
    )
    private var snapTransactionIndexingEnabled: Boolean =
        SnapSyncConfiguration.DEFAULT_SNAP_SYNC_TRANSACTION_INDEXING_ENABLED

    override fun toDomainObject(): SynchronizerConfiguration.Builder {
        val builder = SynchronizerConfiguration.builder()
        builder.blockPropagationRange(blockPropagationRange)
        builder.downloaderChangeTargetThresholdByHeight(downloaderChangeTargetThresholdByHeight)
        builder.downloaderChangeTargetThresholdByTd(downloaderChangeTargetThresholdByTd)
        builder.downloaderHeadersRequestSize(downloaderHeaderRequestSize)
        builder.downloaderCheckpointRetries(downloaderCheckpointRetries)
        builder.downloaderChainSegmentSize(downloaderChainSegmentSize)
        builder.downloaderParallelism(downloaderParallelism)
        builder.transactionsParallelism(transactionsParallelism)
        builder.computationParallelism(computationParallelism)
        builder.syncPivotDistance(fastSyncPivotDistance)
        builder.fastSyncFullValidationRate(fastSyncFullValidationRate)
        builder.worldStateHashCountPerRequest(worldStateHashCountPerRequest)
        builder.worldStateRequestParallelism(worldStateRequestParallelism)
        builder.worldStateMaxRequestsWithoutProgress(worldStateMaxRequestsWithoutProgress)
        builder.worldStateMinMillisBeforeStalling(worldStateMinMillisBeforeStalling)
        builder.worldStateTaskCacheSize(worldStateTaskCacheSize)
        builder.snapSyncConfiguration(
            ImmutableSnapSyncConfiguration.builder()
                .pivotBlockWindowValidity(snapsyncPivotBlockWindowValidity)
                .pivotBlockDistanceBeforeCaching(snapsyncPivotBlockDistanceBeforeCaching)
                .storageCountPerRequest(snapsyncStorageCountPerRequest)
                .bytecodeCountPerRequest(snapsyncBytecodeCountPerRequest)
                .trienodeCountPerRequest(snapsyncTrieNodeCountPerRequest)
                .localFlatAccountCountToHealPerRequest(snapsyncFlatAccountHealedCountPerRequest)
                .localFlatStorageCountToHealPerRequest(snapsyncFlatStorageHealedCountPerRequest)
                .isSnapServerEnabled(isSnapsyncServerEnabled)
                .isSnapSyncTransactionIndexingEnabled(snapTransactionIndexingEnabled)
                .build()
        )
        builder.checkpointPostMergeEnabled(checkpointPostMergeSyncEnabled)
        builder.isPeerTaskSystemEnabled(isPeerTaskSystemEnabled)
        return builder
    }

    override fun getCLIOptions(): List<String> {
        val value =
            Arrays.asList(
                BLOCK_PROPAGATION_RANGE_FLAG,
                format(Optional.ofNullable(blockPropagationRange)),
                DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG,
                format(downloaderChangeTargetThresholdByHeight),
                DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG,
                format(downloaderChangeTargetThresholdByTd),
                DOWNLOADER_HEADER_REQUEST_SIZE_FLAG,
                format(downloaderHeaderRequestSize),
                DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG,
                format(downloaderCheckpointRetries),
                DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG,
                format(downloaderChainSegmentSize),
                DOWNLOADER_PARALLELISM_FLAG,
                format(downloaderParallelism),
                TRANSACTIONS_PARALLELISM_FLAG,
                format(transactionsParallelism),
                COMPUTATION_PARALLELISM_FLAG,
                format(computationParallelism),
                PIVOT_DISTANCE_FROM_HEAD_FLAG,
                format(fastSyncPivotDistance),
                FULL_VALIDATION_RATE_FLAG,
                format(fastSyncFullValidationRate),
                WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG,
                format(worldStateHashCountPerRequest),
                WORLD_STATE_REQUEST_PARALLELISM_FLAG,
                format(worldStateRequestParallelism),
                WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG,
                format(worldStateMaxRequestsWithoutProgress),
                WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG,
                format(worldStateMinMillisBeforeStalling),
                WORLD_STATE_TASK_CACHE_SIZE_FLAG,
                format(worldStateTaskCacheSize),
                SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG,
                format(snapsyncPivotBlockWindowValidity),
                SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG,
                format(snapsyncPivotBlockDistanceBeforeCaching),
                SNAP_STORAGE_COUNT_PER_REQUEST_FLAG,
                format(snapsyncStorageCountPerRequest),
                SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG,
                format(snapsyncBytecodeCountPerRequest),
                SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG,
                format(snapsyncTrieNodeCountPerRequest),
                SNAP_FLAT_ACCOUNT_HEALED_COUNT_PER_REQUEST_FLAG,
                format(snapsyncFlatAccountHealedCountPerRequest),
                SNAP_FLAT_STORAGE_HEALED_COUNT_PER_REQUEST_FLAG,
                format(snapsyncFlatStorageHealedCountPerRequest),
                SNAP_SERVER_ENABLED_FLAG,
                format(isSnapsyncServerEnabled),
                SNAP_TRANSACTION_INDEXING_ENABLED_FLAG,
                format(snapTransactionIndexingEnabled)
            )
        return value
    }

    companion object {
        private const val BLOCK_PROPAGATION_RANGE_FLAG = "--Xsynchronizer-block-propagation-range"
        private const val DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_HEIGHT_FLAG =
            "--Xsynchronizer-downloader-change-target-threshold-by-height"
        private const val DOWNLOADER_CHANGE_TARGET_THRESHOLD_BY_TD_FLAG =
            "--Xsynchronizer-downloader-change-target-threshold-by-td"
        private const val DOWNLOADER_HEADER_REQUEST_SIZE_FLAG = "--Xsynchronizer-downloader-header-request-size"
        private const val DOWNLOADER_CHECKPOINT_TIMEOUTS_PERMITTED_FLAG =
            "--Xsynchronizer-downloader-checkpoint-timeouts-permitted"
        private const val DOWNLOADER_CHECKPOINT_RETRIES_FLAG = "--Xsynchronizer-downloader-checkpoint-RETRIES"
        private const val DOWNLOADER_CHAIN_SEGMENT_SIZE_FLAG = "--Xsynchronizer-downloader-chain-segment-size"
        private const val DOWNLOADER_PARALLELISM_FLAG = "--Xsynchronizer-downloader-parallelism"
        private const val TRANSACTIONS_PARALLELISM_FLAG = "--Xsynchronizer-transactions-parallelism"
        private const val COMPUTATION_PARALLELISM_FLAG = "--Xsynchronizer-computation-parallelism"
        private const val PIVOT_DISTANCE_FROM_HEAD_FLAG = "--Xsynchronizer-fast-sync-pivot-distance"
        private const val FULL_VALIDATION_RATE_FLAG = "--Xsynchronizer-fast-sync-full-validation-rate"
        private const val WORLD_STATE_HASH_COUNT_PER_REQUEST_FLAG = "--Xsynchronizer-world-state-hash-count-per-request"
        private const val WORLD_STATE_REQUEST_PARALLELISM_FLAG = "--Xsynchronizer-world-state-request-parallelism"
        private const val WORLD_STATE_MAX_REQUESTS_WITHOUT_PROGRESS_FLAG =
            "--Xsynchronizer-world-state-max-requests-without-progress"
        private const val WORLD_STATE_MIN_MILLIS_BEFORE_STALLING_FLAG =
            "--Xsynchronizer-world-state-min-millis-before-stalling"
        private const val WORLD_STATE_TASK_CACHE_SIZE_FLAG = "--Xsynchronizer-world-state-task-cache-size"

        private const val SNAP_PIVOT_BLOCK_WINDOW_VALIDITY_FLAG = "--Xsnapsync-synchronizer-pivot-block-window-validity"
        private const val SNAP_PIVOT_BLOCK_DISTANCE_BEFORE_CACHING_FLAG =
            "--Xsnapsync-synchronizer-pivot-block-distance-before-caching"
        private const val SNAP_STORAGE_COUNT_PER_REQUEST_FLAG = "--Xsnapsync-synchronizer-storage-count-per-request"
        private const val SNAP_BYTECODE_COUNT_PER_REQUEST_FLAG = "--Xsnapsync-synchronizer-bytecode-count-per-request"
        private const val SNAP_TRIENODE_COUNT_PER_REQUEST_FLAG = "--Xsnapsync-synchronizer-trienode-count-per-request"
        private const val SNAP_TRANSACTION_INDEXING_ENABLED_FLAG =
            "--Xsnapsync-synchronizer-transaction-indexing-enabled"

        private const val SNAP_FLAT_ACCOUNT_HEALED_COUNT_PER_REQUEST_FLAG =
            "--Xsnapsync-synchronizer-flat-account-healed-count-per-request"

        private const val SNAP_FLAT_STORAGE_HEALED_COUNT_PER_REQUEST_FLAG =
            "--Xsnapsync-synchronizer-flat-slot-healed-count-per-request"

        private const val SNAP_SERVER_ENABLED_FLAG = "--Xsnapsync-server-enabled"

        private const val CHECKPOINT_POST_MERGE_FLAG = "--Xcheckpoint-post-merge-enabled"

        private const val SNAP_SYNC_BFT_ENABLED_FLAG = "--Xsnapsync-bft-enabled"

        /**
         * Create synchronizer options.
         *
         * @return the synchronizer options
         */
        @JvmStatic
        fun create(): SynchronizerOptions {
            return SynchronizerOptions()
        }

        /**
         * Create synchronizer options from Synchronizer Configuration.
         *
         * @param config the Synchronizer Configuration
         * @return the synchronizer options
         */
        @JvmStatic
        fun fromConfig(config: SynchronizerConfiguration): SynchronizerOptions {
            val options = SynchronizerOptions()
            options.blockPropagationRange = config.blockPropagationRange
            options.downloaderChangeTargetThresholdByHeight =
                config.downloaderChangeTargetThresholdByHeight
            options.downloaderChangeTargetThresholdByTd = config.downloaderChangeTargetThresholdByTd
            options.downloaderHeaderRequestSize = config.downloaderHeaderRequestSize
            options.downloaderCheckpointRetries = config.downloaderCheckpointRetries
            options.downloaderChainSegmentSize = config.downloaderChainSegmentSize
            options.downloaderParallelism = config.downloaderParallelism
            options.transactionsParallelism = config.transactionsParallelism
            options.computationParallelism = config.computationParallelism
            options.fastSyncPivotDistance = config.syncPivotDistance
            options.fastSyncFullValidationRate = config.fastSyncFullValidationRate
            options.worldStateHashCountPerRequest = config.worldStateHashCountPerRequest
            options.worldStateRequestParallelism = config.worldStateRequestParallelism
            options.worldStateMaxRequestsWithoutProgress = config.worldStateMaxRequestsWithoutProgress
            options.worldStateMinMillisBeforeStalling = config.worldStateMinMillisBeforeStalling
            options.worldStateTaskCacheSize = config.worldStateTaskCacheSize
            options.snapsyncPivotBlockWindowValidity =
                config.snapSyncConfiguration.pivotBlockWindowValidity
            options.snapsyncPivotBlockDistanceBeforeCaching =
                config.snapSyncConfiguration.pivotBlockDistanceBeforeCaching
            options.snapsyncStorageCountPerRequest =
                config.snapSyncConfiguration.storageCountPerRequest
            options.snapsyncBytecodeCountPerRequest =
                config.snapSyncConfiguration.bytecodeCountPerRequest
            options.snapsyncTrieNodeCountPerRequest =
                config.snapSyncConfiguration.trienodeCountPerRequest
            options.snapsyncFlatAccountHealedCountPerRequest =
                config.snapSyncConfiguration.localFlatAccountCountToHealPerRequest
            options.snapsyncFlatStorageHealedCountPerRequest =
                config.snapSyncConfiguration.localFlatStorageCountToHealPerRequest
            options.checkpointPostMergeSyncEnabled = config.isCheckpointPostMergeEnabled
            options.isSnapsyncServerEnabled = config.snapSyncConfiguration.isSnapServerEnabled
            options.snapTransactionIndexingEnabled =
                config.snapSyncConfiguration.isSnapSyncTransactionIndexingEnabled
            return options
        }
    }
}
