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
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromPeers implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromPeers.class);

  private final SynchronizerConfiguration syncConfig;

  public PivotSelectorFromPeers(final SynchronizerConfiguration syncConfig) {
    this.syncConfig = syncConfig;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock(final EthPeer peer) {
    return fromBestPeer(peer);
  }

  private Optional<FastSyncState> fromBestPeer(final EthPeer peer) {
    final long pivotBlockNumber =
        peer.chainState().getEstimatedHeight() - syncConfig.getFastSyncPivotDistance();
    if (pivotBlockNumber <= BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Peer's chain isn't long enough, return an empty value so we can try again.
      LOG.info("Waiting for peers with sufficient chain height");
      return Optional.empty();
    }
    LOG.info("Selecting block number {} as fast sync pivot block.", pivotBlockNumber);
    return Optional.of(new FastSyncState(pivotBlockNumber));
  }
}
