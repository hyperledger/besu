/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.IncompleteResultsException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingGetBlockFromPeersTask
    extends AbstractRetryingPeerTask<AbstractPeerTask.PeerTaskResult<Block>> {

  private static final Logger LOG = LoggerFactory.getLogger(RetryingGetBlockFromPeersTask.class);

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final Optional<Hash> blockHash;
  private final long blockNumber;
  private final Set<EthPeer> triedPeers = new HashSet<>();

  protected RetryingGetBlockFromPeersTask(
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final Optional<Hash> blockHash,
      final long blockNumber) {
    super(ethContext, maxRetries, Objects::isNull, metricsSystem);
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
  }

  public static RetryingGetBlockFromPeersTask create(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final int maxRetries,
      final Optional<Hash> hash,
      final long blockNumber) {
    return new RetryingGetBlockFromPeersTask(
        protocolContext,
        ethContext,
        protocolSchedule,
        metricsSystem,
        maxRetries,
        hash,
        blockNumber);
  }

  @Override
  public void assignPeer(final EthPeer peer) {
    super.assignPeer(peer);
    triedPeers.add(peer);
  }

  @Override
  protected CompletableFuture<AbstractPeerTask.PeerTaskResult<Block>> executePeerTask(
      final Optional<EthPeer> assignedPeer) {

    final GetBlockFromPeerTask getBlockTask =
        GetBlockFromPeerTask.create(
            protocolSchedule, getEthContext(), blockHash, blockNumber, getMetricsSystem());

    getBlockTask.assignPeer(
        assignedPeer
            .filter(unused -> getRetryCount() == 1) // first try with the assigned preferred peer
            .orElseGet( // then selecting a new one from the pool
                () -> {
                  assignPeer(selectNextPeer());
                  return getAssignedPeer().get();
                }));

    LOG.debug(
        "Getting block {} ({}) from peer {}, attempt {}",
        blockNumber,
        blockHash,
        getAssignedPeer(),
        getRetryCount());

    return executeSubTask(getBlockTask::run)
        .thenApply(
            peerResult -> {
              result.complete(peerResult);
              return peerResult;
            });
  }

  private EthPeer selectNextPeer() {
    return getEthContext()
        .getEthPeers()
        .streamBestPeers()
        .filter(peer -> !triedPeers.contains(peer))
        .findFirst()
        .orElseThrow(NoAvailablePeersException::new);
  }

  @Override
  protected boolean isRetryableError(final Throwable error) {
    return (blockNumber > protocolContext.getBlockchain().getChainHeadBlockNumber())
        && (super.isRetryableError(error) || error instanceof IncompleteResultsException);
  }

  @Override
  protected void handleTaskError(final Throwable error) {
    if (getRetryCount() < getMaxRetries()) {
      LOG.debug(
          "Failed to get block {} ({}) from peer {}, attempt {}, retrying later",
          blockNumber,
          blockHash,
          getAssignedPeer(),
          getRetryCount());
    } else {
      LOG.warn(
          "Failed to get block {} ({}) after {} retries", blockNumber, blockHash, getRetryCount());
    }
    super.handleTaskError(error);
  }
}
