/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.tasks.RetryingGetHeaderFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromSafeBlock implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromSafeBlock.class);
  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final GenesisConfigOptions genesisConfig;
  private final SynchronizerConfiguration synchronizerConfiguration;
  private final Supplier<Optional<ForkchoiceEvent>> forkchoiceStateSupplier;
  private final Runnable cleanupAction;

  private long lastNoFcuReceivedInfoLog = System.currentTimeMillis();
  private static final long NO_FCU_RECEIVED_LOGGING_THRESHOLD = 60000L;
  private volatile Optional<BlockHeader> maybeCachedHeadBlockHeader = Optional.empty();

  public PivotSelectorFromSafeBlock(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final GenesisConfigOptions genesisConfig,
      final SynchronizerConfiguration synchronizerConfiguration,
      final Supplier<Optional<ForkchoiceEvent>> forkchoiceStateSupplier,
      final Runnable cleanupAction) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.genesisConfig = genesisConfig;
    this.synchronizerConfiguration = synchronizerConfiguration;
    this.forkchoiceStateSupplier = forkchoiceStateSupplier;
    this.cleanupAction = cleanupAction;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock() {
    final Optional<ForkchoiceEvent> maybeForkchoice = forkchoiceStateSupplier.get();
    if (maybeForkchoice.isPresent() && maybeForkchoice.get().hasValidSafeBlockHash()) {
      return Optional.of(selectLastSafeBlockAsPivot(maybeForkchoice.get().getSafeBlockHash()));
    }
    if (lastNoFcuReceivedInfoLog + NO_FCU_RECEIVED_LOGGING_THRESHOLD < System.currentTimeMillis()) {
      lastNoFcuReceivedInfoLog = System.currentTimeMillis();
      LOG.info(
          "Waiting for consensus client, this may be because your consensus client is still syncing");
    }
    LOG.debug("No finalized block hash announced yet");
    return Optional.empty();
  }

  @Override
  public CompletableFuture<Void> prepareRetry() {
    // nothing to do
    return CompletableFuture.completedFuture(null);
  }

  private FastSyncState selectLastSafeBlockAsPivot(final Hash safeHash) {
    LOG.debug("Returning safe block hash {} as pivot", safeHash);
    return new FastSyncState(safeHash);
  }

  @Override
  public void close() {
    cleanupAction.run();
  }

  @Override
  public long getMinRequiredBlockNumber() {
    return genesisConfig.getTerminalBlockNumber().orElse(0L);
  }

  @Override
  public long getBestChainHeight() {
    final long localChainHeight = protocolContext.getBlockchain().getChainHeadBlockNumber();

    return Math.max(
        forkchoiceStateSupplier
            .get()
            .map(ForkchoiceEvent::getHeadBlockHash)
            .map(
                headBlockHash ->
                    maybeCachedHeadBlockHeader
                        .filter(
                            cachedBlockHeader -> cachedBlockHeader.getHash().equals(headBlockHash))
                        .map(BlockHeader::getNumber)
                        .orElseGet(
                            () -> {
                              LOG.debug(
                                  "Downloading chain head block header by hash {}", headBlockHash);
                              try {
                                return ethContext
                                    .getEthPeers()
                                    .waitForPeer((peer) -> true)
                                    .thenCompose(unused -> downloadBlockHeader(headBlockHash))
                                    .thenApply(
                                        blockHeader -> {
                                          maybeCachedHeadBlockHeader = Optional.of(blockHeader);
                                          return blockHeader.getNumber();
                                        })
                                    .get(20, TimeUnit.SECONDS);
                              } catch (Throwable t) {
                                LOG.debug(
                                    "Error trying to download chain head block header by hash {}",
                                    headBlockHash,
                                    t);
                              }
                              return null;
                            }))
            .orElse(0L),
        localChainHeight);
  }

  private CompletableFuture<BlockHeader> downloadBlockHeader(final Hash hash) {
    CompletableFuture<BlockHeader> resultFuture;
    if (synchronizerConfiguration.isPeerTaskSystemEnabled()) {
      resultFuture =
          ethContext
              .getScheduler()
              .scheduleServiceTask(
                  () -> {
                    GetHeadersFromPeerTask task =
                        new GetHeadersFromPeerTask(
                            hash,
                            0,
                            1,
                            0,
                            GetHeadersFromPeerTask.Direction.FORWARD,
                            ethContext.getEthPeers().peerCount(),
                            protocolSchedule);
                    PeerTaskExecutorResult<List<BlockHeader>> taskResult =
                        ethContext.getPeerTaskExecutor().execute(task);
                    if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
                        || taskResult.result().isEmpty()) {
                      return CompletableFuture.failedFuture(
                          new RuntimeException("Unable to retrieve header"));
                    }
                    return CompletableFuture.completedFuture(taskResult.result().get().getFirst());
                  });
    } else {
      resultFuture =
          RetryingGetHeaderFromPeerByHashTask.byHash(
                  protocolSchedule, ethContext, hash, 0, metricsSystem)
              .getHeader();
    }
    return resultFuture.whenComplete(
        (blockHeader, throwable) -> {
          if (throwable != null) {
            LOG.debug("Error downloading block header by hash {}", hash);
          } else {
            LOG.atDebug()
                .setMessage("Successfully downloaded pivot block header by hash {}")
                .addArgument(blockHeader::toLogString)
                .log();
          }
        });
  }
}
