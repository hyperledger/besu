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
package org.hyperledger.besu.ethereum.eth.sync.tasks;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractEthTask;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByNumberTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.util.BlockchainUtil;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the common ancestor with the given peer. It is assumed that the peer will at least share
 * the same genesis block with this node. Running this task against a peer with a non-matching
 * genesis block will result in undefined behavior: the task may complete exceptionally or in some
 * cases this node's genesis block will be returned.
 */
public class DetermineCommonAncestorTask extends AbstractEthTask<BlockHeader> {
  private static final Logger LOG = LoggerFactory.getLogger(DetermineCommonAncestorTask.class);
  private final EthContext ethContext;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthPeer peer;
  private final int headerRequestSize;
  private final MetricsSystem metricsSystem;

  private long maximumPossibleCommonAncestorNumber;
  private long minimumPossibleCommonAncestorNumber;
  private BlockHeader commonAncestorCandidate;
  private boolean initialQuery = true;

  private DetermineCommonAncestorTask(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final EthPeer peer,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.protocolContext = protocolContext;
    this.peer = peer;
    this.headerRequestSize = headerRequestSize;
    this.metricsSystem = metricsSystem;

    maximumPossibleCommonAncestorNumber =
        Math.min(
            protocolContext.getBlockchain().getChainHeadBlockNumber(),
            peer.chainState().getEstimatedHeight());
    minimumPossibleCommonAncestorNumber = BlockHeader.GENESIS_BLOCK_NUMBER;
    commonAncestorCandidate =
        protocolContext.getBlockchain().getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get();
  }

  public static DetermineCommonAncestorTask create(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final EthPeer peer,
      final int headerRequestSize,
      final MetricsSystem metricsSystem) {
    return new DetermineCommonAncestorTask(
        protocolSchedule, protocolContext, ethContext, peer, headerRequestSize, metricsSystem);
  }

  @Override
  protected void executeTask() {
    if (maximumPossibleCommonAncestorNumber == minimumPossibleCommonAncestorNumber) {
      // Bingo, we found our common ancestor.
      result.complete(commonAncestorCandidate);
      return;
    }
    if (maximumPossibleCommonAncestorNumber < BlockHeader.GENESIS_BLOCK_NUMBER
        && !result.isDone()) {
      result.completeExceptionally(new IllegalStateException("No common ancestor."));
      return;
    }
    requestHeaders()
        .thenCompose(this::processHeaders)
        .whenComplete(
            (peerResult, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else if (!result.isDone()) {
                executeTaskTimed();
              }
            });
  }

  @VisibleForTesting
  CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> requestHeaders() {
    final long range = maximumPossibleCommonAncestorNumber - minimumPossibleCommonAncestorNumber;
    final int skipInterval = initialQuery ? 0 : calculateSkipInterval(range, headerRequestSize);
    final int count =
        initialQuery ? headerRequestSize : calculateCount((double) range, skipInterval);
    LOG.debug(
        "Searching for common ancestor with {} between {} and {}",
        peer,
        minimumPossibleCommonAncestorNumber,
        maximumPossibleCommonAncestorNumber);
    return executeSubTask(
        () ->
            GetHeadersFromPeerByNumberTask.endingAtNumber(
                    protocolSchedule,
                    ethContext,
                    maximumPossibleCommonAncestorNumber,
                    count,
                    skipInterval,
                    metricsSystem)
                .assignPeer(peer)
                .run());
  }

  /**
   * In the case where the remote chain contains 100 blocks, the initial count work out to 11, and
   * the skip interval would be 9. This would yield the headers (0, 10, 20, 30, 40, 50, 60, 70, 80,
   * 90, 100).
   */
  @VisibleForTesting
  static int calculateSkipInterval(final long range, final int headerRequestSize) {
    return Math.max(0, Math.toIntExact(range / (headerRequestSize - 1) - 1) - 1);
  }

  @VisibleForTesting
  static int calculateCount(final double range, final int skipInterval) {
    return Math.toIntExact((long) Math.ceil(range / (skipInterval + 1)) + 1);
  }

  private CompletableFuture<Void> processHeaders(
      final AbstractPeerTask.PeerTaskResult<List<BlockHeader>> headersResult) {
    initialQuery = false;
    final List<BlockHeader> headers = headersResult.getResult();
    if (headers.isEmpty()) {
      // Nothing to do
      return CompletableFuture.completedFuture(null);
    }

    final OptionalInt maybeAncestorNumber =
        BlockchainUtil.findHighestKnownBlockIndex(protocolContext.getBlockchain(), headers, false);

    // Means the insertion point is in the next header request.
    if (!maybeAncestorNumber.isPresent()) {
      maximumPossibleCommonAncestorNumber = headers.get(headers.size() - 1).getNumber() - 1L;
      return CompletableFuture.completedFuture(null);
    }
    final int ancestorNumber = maybeAncestorNumber.getAsInt();
    commonAncestorCandidate = headers.get(ancestorNumber);

    if (ancestorNumber - 1 >= 0) {
      maximumPossibleCommonAncestorNumber = headers.get(ancestorNumber - 1).getNumber() - 1L;
    }
    minimumPossibleCommonAncestorNumber = headers.get(ancestorNumber).getNumber();

    return CompletableFuture.completedFuture(null);
  }
}
