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
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainHeadTracker {

  private static final Logger LOG = LoggerFactory.getLogger(ChainHeadTracker.class);

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
    ethContext.getEthPeers().subscribeStatusExchanged(tracker);
    blockchain.observeBlockAdded(trailingPeerLimiter);
  }

  public CompletableFuture<Void> onPeerConnected(final EthPeer peer) {
    LOG.atDebug()
        .setMessage("Requesting chain head info from {}...")
        .addArgument(peer::getLoggableId)
        .log();
    final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>>
        bestHeaderFromPeerCompletableFuture = getBestHeaderFromPeerCompletableFuture(peer);
    final CompletableFuture<Void> future = new CompletableFuture<>();
    bestHeaderFromPeerCompletableFuture.whenComplete(
        (peerResult, error) -> {
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
            LOG.atDebug()
                .setMessage("Failed to retrieve chain head info. Disconnecting {}... {}")
                .addArgument(peer::getLoggableId)
                .addArgument(error)
                .log();
            peer.disconnect(DisconnectMessage.DisconnectReason.USELESS_PEER);
          }
          future.complete(null);
        });
    return future;
  }

  public CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>>
      getBestHeaderFromPeerCompletableFuture(final EthPeer peer) {
    return GetHeadersFromPeerByHashTask.forSingleHash(
            protocolSchedule,
            ethContext,
            Hash.wrap(peer.chainState().getBestBlock().getHash()),
            0,
            metricsSystem)
        .assignPeer(peer)
        .run();
  }
}
