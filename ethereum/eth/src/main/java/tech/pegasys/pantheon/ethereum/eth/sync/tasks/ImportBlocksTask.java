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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.task.AbstractPeerTask;
import tech.pegasys.pantheon.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Download and import blocks from a peer.
 *
 * @param <C> the consensus algorithm context
 */
public class ImportBlocksTask<C> extends AbstractPeerTask<List<Hash>> {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolContext<C> protocolContext;
  private final ProtocolSchedule<C> protocolSchedule;
  private final long startNumber;

  private final BlockHeader referenceHeader;
  private final int maxBlocks;
  private final MetricsSystem metricsSystem;
  private EthPeer peer;

  protected ImportBlocksTask(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final BlockHeader referenceHeader,
      final int maxBlocks,
      final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.referenceHeader = referenceHeader;
    this.maxBlocks = maxBlocks;
    this.metricsSystem = metricsSystem;

    this.startNumber = referenceHeader.getNumber();
  }

  public static <C> ImportBlocksTask<C> fromHeader(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final BlockHeader previousHeader,
      final int maxBlocks,
      final MetricsSystem metricsSystem) {
    return new ImportBlocksTask<>(
        protocolSchedule, protocolContext, ethContext, previousHeader, maxBlocks, metricsSystem);
  }

  @Override
  protected void executeTaskWithPeer(final EthPeer peer) throws PeerNotConnected {
    this.peer = peer;
    LOG.debug("Importing blocks from {}", startNumber);
    downloadHeaders()
        .thenCompose(this::completeBlocks)
        .thenCompose(this::importBlocks)
        .whenComplete(
            (r, t) -> {
              if (t != null) {
                LOG.debug("Import from block {} failed: {}.", startNumber, t);
                result.get().completeExceptionally(t);
              } else {
                LOG.debug("Import from block {} succeeded.", startNumber);
                result
                    .get()
                    .complete(
                        new PeerTaskResult<>(
                            peer, r.stream().map(Block::getHash).collect(Collectors.toList())));
              }
            });
  }

  @Override
  protected Optional<EthPeer> findSuitablePeer() {
    return ethContext.getEthPeers().idlePeer(referenceHeader.getNumber());
  }

  private CompletableFuture<PeerTaskResult<List<BlockHeader>>> downloadHeaders() {
    final AbstractPeerTask<List<BlockHeader>> task =
        GetHeadersFromPeerByHashTask.startingAtHash(
                protocolSchedule,
                ethContext,
                referenceHeader.getHash(),
                referenceHeader.getNumber(),
                maxBlocks,
                metricsSystem)
            .assignPeer(peer);
    return executeSubTask(task::run);
  }

  private CompletableFuture<List<Block>> completeBlocks(
      final PeerTaskResult<List<BlockHeader>> headers) {
    if (headers.getResult().isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }
    final CompleteBlocksTask<C> task =
        CompleteBlocksTask.forHeaders(
            protocolSchedule, ethContext, headers.getResult(), metricsSystem);
    task.assignPeer(peer);
    return executeSubTask(() -> ethContext.getScheduler().timeout(task));
  }

  private CompletableFuture<List<Block>> importBlocks(final List<Block> blocks) {
    // Don't import reference block if we already know about it
    if (protocolContext.getBlockchain().contains(referenceHeader.getHash())) {
      blocks.removeIf(b -> b.getHash().equals(referenceHeader.getHash()));
    }
    if (blocks.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }
    final Supplier<CompletableFuture<List<Block>>> task =
        PersistBlockTask.forSequentialBlocks(
            protocolSchedule, protocolContext, blocks, HeaderValidationMode.FULL, metricsSystem);
    return executeWorkerSubTask(ethContext.getScheduler(), task);
  }
}
