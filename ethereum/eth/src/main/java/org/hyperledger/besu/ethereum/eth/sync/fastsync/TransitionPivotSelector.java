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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionPivotSelector implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(TransitionPivotSelector.class);

  private final Difficulty totalTerminalDifficulty;
  private final Supplier<Optional<Hash>> finalizedBlockHashSupplier;
  private final PivotBlockSelector pivotSelectorFromPeers;
  private final PivotBlockSelector pivotSelectorFromFinalizedBlock;

  public TransitionPivotSelector(
      final GenesisConfigOptions genesisConfig,
      final Supplier<Optional<Hash>> finalizedBlockHashSupplier,
      final PivotBlockSelector pivotSelectorFromPeers,
      final PivotBlockSelector pivotSelectorFromFinalizedBlock) {
    this.totalTerminalDifficulty =
        genesisConfig
            .getTerminalTotalDifficulty()
            .map(Difficulty::of)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "This class can only be used when TTD is present"));
    this.finalizedBlockHashSupplier = finalizedBlockHashSupplier;
    this.pivotSelectorFromPeers = pivotSelectorFromPeers;
    this.pivotSelectorFromFinalizedBlock = pivotSelectorFromFinalizedBlock;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock(final EthPeer peer) {
    return routeDependingOnTotalTerminalDifficulty(peer);
  }

  private Optional<FastSyncState> routeDependingOnTotalTerminalDifficulty(final EthPeer peer) {

    Difficulty bestPeerEstDifficulty = peer.chainState().getEstimatedTotalDifficulty();

    if (finalizedBlockHashSupplier.get().isPresent()) {
      LOG.trace("A finalized block is present, use it as pivot");
      return pivotSelectorFromFinalizedBlock.selectNewPivotBlock(peer);
    }

    if (bestPeerEstDifficulty.greaterOrEqualThan(totalTerminalDifficulty)) {
      LOG.debug(
          "Chain has reached TTD, best peer has estimated difficulty {},"
              + " select pivot from finalized block",
          bestPeerEstDifficulty);
      return pivotSelectorFromFinalizedBlock.selectNewPivotBlock(peer);
    }

    LOG.info(
        "Chain has not yet reached TTD, best peer has estimated difficulty {},"
            + " select pivot from peers",
        bestPeerEstDifficulty);
    return pivotSelectorFromPeers.selectNewPivotBlock(peer);
  }

  @Override
  public void close() {
    pivotSelectorFromFinalizedBlock.close();
    pivotSelectorFromPeers.close();
  }

  @Override
  public long getMinRequiredBlockNumber() {
    return pivotSelectorFromFinalizedBlock.getMinRequiredBlockNumber();
  }
}
