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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.NoSyncRequiredException;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromPeers;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BFTPivotSelectorFromPeers extends PivotSelectorFromPeers {

  private static final Logger LOG = LoggerFactory.getLogger(BFTPivotSelectorFromPeers.class);

  private final ProtocolContext protocolContext;
  private final NodeKey nodeKey;

  public BFTPivotSelectorFromPeers(
      final EthContext ethContext,
      final SynchronizerConfiguration syncConfig,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final ProtocolContext protocolContext,
      final NodeKey nodeKey) {
    super(ethContext, syncConfig, syncState, metricsSystem);
    this.protocolContext = protocolContext;
    this.nodeKey = nodeKey;
    LOG.info("Creating BFTPivotSelectorFromPeers");
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock() {

    final BftContext bftContext = protocolContext.getConsensusContext(BftContext.class);
    final ValidatorProvider validatorProvider = bftContext.getValidatorProvider();
    // See if we have a best peer
    Optional<EthPeer> bestPeer = selectBestPeer();

    if (bestPeer.isPresent()) {
      // For a recently created permissioned chain we can skip snap sync until we're past the
      // pivot distance
      if (bestPeer.get().chainState().getEstimatedHeight()
          <= syncConfig.getFastSyncPivotDistance()) {
        throw new NoSyncRequiredException();
      }

      return bestPeer.flatMap(this::fromBestPeer);
    } else {
      // Treat single-validator as a special case. We are the only node that can produce
      // blocks so we won't wait to sync with a non-validator node that may or may not exist
      if (validatorProvider.getValidatorsAtHead().size() == 1
          && validatorProvider
              .getValidatorsAtHead()
              .contains(Util.publicKeyToAddress(nodeKey.getPublicKey()))) {
        throw new NoSyncRequiredException();
      }

      // Treat the case where we have min-peer-count peers who don't have a chain-head estimate but who are all validators as not needing to sync
      // This is effectively handling the "new chain with N validators" case, but speaks more generally to the BFT case where a BFT chain
      // prioritises information from other validators over waiting for non-validator peers to respond.
      AtomicInteger peerValidatorCount = new AtomicInteger();
      EthPeers theList = ethContext.getEthPeers();
      theList.getAllActiveConnections().forEach(peer -> {
        if (validatorProvider
              .getValidatorsAtHead().contains(peer.getPeerInfo().getAddress())) {
          peerValidatorCount.getAndIncrement();
        }
      });
      if (peerValidatorCount.get() >= syncConfig.getFastSyncMinimumPeerCount()) {
        // We have sync-min-peers x validators connected, all of whom have no head estimate. We'll assume this is a new chain
        // and skip waiting for any more peers to sync with.
        throw new NoSyncRequiredException();
      }
    }

    return Optional.empty();
  }
}
