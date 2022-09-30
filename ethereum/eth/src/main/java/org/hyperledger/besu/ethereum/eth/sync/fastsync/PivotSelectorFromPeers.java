/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromPeers implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromPeers.class);

  private final EthPeers ethPeers;
  private final SynchronizerConfiguration syncConfig;

  public PivotSelectorFromPeers(
      final EthPeers ethPeers, final SynchronizerConfiguration syncConfig) {
    this.ethPeers = ethPeers;
    this.syncConfig = syncConfig;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock() {
    return selectBestPeer().flatMap(this::fromBestPeer);
  }

  private Optional<FastSyncState> fromBestPeer(final EthPeer peer) {
    final long pivotBlockNumber =
        peer.chainState().getEstimatedHeight() - syncConfig.getFastSyncPivotDistance();
    if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Peer's chain isn't long enough, return an empty value, so we can try again.
      LOG.info("Waiting for peers with sufficient chain height");
      return Optional.empty();
    }
    LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
    return Optional.of(new FastSyncState(pivotBlockNumber));
  }

  private Optional<EthPeer> selectBestPeer() {
    return ethPeers
        .bestPeerMatchingCriteria(this::canPeerDeterminePivotBlock)
        // Only select a pivot block number when we have a minimum number of height estimates
        .filter(unused -> enoughFastSyncPeersArePresent());
  }

  private boolean enoughFastSyncPeersArePresent() {
    final long peerCount = countPeersThatCanDeterminePivotBlock();
    final int minPeerCount = syncConfig.getFastSyncMinimumPeerCount();
    if (peerCount < minPeerCount) {
      LOG.info(
          "Waiting for valid peers with chain height information.  {} / {} required peers currently available.",
          peerCount,
          minPeerCount);
      return false;
    }
    return true;
  }

  private long countPeersThatCanDeterminePivotBlock() {
    return ethPeers.streamAvailablePeers().filter(this::canPeerDeterminePivotBlock).count();
  }

  private boolean canPeerDeterminePivotBlock(final EthPeer peer) {
    LOG.debug(
        "peer {} hasEstimatedHeight {} isFullyValidated? {}",
        peer.getShortNodeId(),
        peer.chainState().hasEstimatedHeight(),
        peer.isFullyValidated());
    return peer.chainState().hasEstimatedHeight() && peer.isFullyValidated();
  }
}
