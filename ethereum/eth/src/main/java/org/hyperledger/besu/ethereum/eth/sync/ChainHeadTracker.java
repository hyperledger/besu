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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers.ConnectCallback;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainHeadTracker implements ConnectCallback {

  private static final Logger LOG = LoggerFactory.getLogger(ChainHeadTracker.class);
  public static final int MAX_RETRIES = 4;

  private final EthContext ethContext;
  private final ProtocolSchedule protocolSchedule;
  private final TrailingPeerLimiter trailingPeerLimiter;
  private final MetricsSystem metricsSystem;

  public ChainHeadTracker(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final TrailingPeerLimiter trailingPeerLimiter,
      final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.protocolSchedule = protocolSchedule;
    this.trailingPeerLimiter = trailingPeerLimiter;
    this.metricsSystem = metricsSystem;
  }

  public static void trackChainHeadForPeers(
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final Blockchain blockchain,
      final Supplier<TrailingPeerRequirements> trailingPeerRequirementsCalculator,
      final MetricsSystem metricsSystem) {
    final TrailingPeerLimiter trailingPeerLimiter =
        new TrailingPeerLimiter(ethContext.getEthPeers(), trailingPeerRequirementsCalculator);
    final ChainHeadTracker tracker =
        new ChainHeadTracker(ethContext, protocolSchedule, trailingPeerLimiter, metricsSystem);
    ethContext.getEthPeers().subscribeConnect(tracker);
    blockchain.observeBlockAdded(trailingPeerLimiter);
  }

  @Override
  public void onPeerConnected(final EthPeer peer) {
    LOG.atDebug()
        .setMessage("Requesting chain head info from {}...")
        .addArgument(peer::getLoggableId)
        .log();
    createAndRunGetHeaderTask(peer).whenComplete(checkResultAndMaybeRetry(peer, MAX_RETRIES));
  }

  private CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>>
      createAndRunGetHeaderTask(final EthPeer peer) {
    return GetHeadersFromPeerByHashTask.forSingleHash(
            protocolSchedule,
            ethContext,
            Hash.wrap(peer.chainState().getBestBlock().getHash()),
            0,
            metricsSystem)
        .assignFixedPeer(
            peer) // want to make sure we are using this peer. If it can't even provide this header,
        // it's useless!
        .run()
        .exceptionally(handleException(peer));
  }

  private static Function<Throwable, AbstractPeerTask.PeerTaskResult<List<BlockHeader>>>
      handleException(final EthPeer peer) {
    return e -> {
      LOG.atDebug()
          .setMessage("Failed to retrieve chain head info from {}. Reason: {}")
          .addArgument(peer::getLoggableId)
          .addArgument(e::toString)
          .log();
      peer.disconnect(DisconnectReason.USELESS_PEER);
      return null;
    };
  }

  private BiConsumer<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>, Throwable>
      checkResultAndMaybeRetry(final EthPeer peer, final int retries) {
    return (peerResult, error) -> {
      if (peerResult != null && !peerResult.getResult().isEmpty()) {
        final BlockHeader chainHeadHeader = peerResult.getResult().get(0);
        peer.chainState().update(chainHeadHeader);
        trailingPeerLimiter.enforceTrailingPeerLimit();
        LOG.atDebug()
            .setMessage("Retrieved chain head info {} from {}...")
            .addArgument(
                () -> chainHeadHeader.getNumber() + " (" + chainHeadHeader.getBlockHash() + ")")
            .addArgument(peer::getLoggableId)
            .log();
      } else {
        if (retries > 0) {
          LOG.atDebug()
              .setMessage(
                  "Failed to retrieve chain head info from {}. Reason: {}. {} retires left}")
              .addArgument(peer::getLoggableId)
              .addArgument(() -> getReason(peerResult, error))
              .addArgument(() -> retries)
              .log();
          Executor delayed = CompletableFuture.delayedExecutor(2L, TimeUnit.SECONDS);
          delayed.execute(
              () ->
                  createAndRunGetHeaderTask(peer)
                      .whenComplete(checkResultAndMaybeRetry(peer, retries - 1)));
        } else {
          LOG.atDebug()
              .setMessage(
                  "Failed to retrieve chain head info from {}. Disconnecting after "
                      + MAX_RETRIES
                      + 1
                      + " tries. Reason: {}.")
              .addArgument(peer::getLoggableId)
              .addArgument(() -> getReason(peerResult, error))
              .log();
          // If that peer does not the block header of it's best block after MAX_RETRIES, it's
          // useless. The best block of that peer was set based on the status massage that the peer
          // sent moments ago.
          peer.disconnect(DisconnectReason.USELESS_PEER);
        }
      }
    };
  }

  private String getReason(
      final AbstractPeerTask.PeerTaskResult<List<BlockHeader>> peerResult, final Throwable error) {
    return peerResult != null && peerResult.getResult().isEmpty()
        ? "Empty result"
        : error.toString();
  }
}
