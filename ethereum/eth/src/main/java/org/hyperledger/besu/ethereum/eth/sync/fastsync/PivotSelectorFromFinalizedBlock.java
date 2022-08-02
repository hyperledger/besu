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
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;

import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PivotSelectorFromFinalizedBlock implements PivotBlockSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PivotSelectorFromFinalizedBlock.class);

  private final GenesisConfigOptions genesisConfig;
  private final Supplier<Optional<Hash>> finalizedBlockHashSupplier;
  private final Runnable cleanupAction;

  public PivotSelectorFromFinalizedBlock(
      final GenesisConfigOptions genesisConfig,
      final Supplier<Optional<Hash>> finalizedBlockHashSupplier,
      final Runnable cleanupAction) {
    this.genesisConfig = genesisConfig;
    this.finalizedBlockHashSupplier = finalizedBlockHashSupplier;
    this.cleanupAction = cleanupAction;
  }

  @Override
  public Optional<FastSyncState> selectNewPivotBlock(final EthPeer peer) {
    final Optional<Hash> maybeHash = finalizedBlockHashSupplier.get();
    if (maybeHash.isPresent()) {
      return Optional.of(selectLastFinalizedBlockAsPivot(maybeHash.get()));
    }
    LOG.trace("No finalized block hash announced yet");
    return Optional.empty();
  }

  private FastSyncState selectLastFinalizedBlockAsPivot(final Hash finalizedHash) {
    LOG.trace("Returning finalized block hash as pivot: {}", finalizedHash);
    return new FastSyncState(finalizedHash);
  }

  @Override
  public void close() {
    cleanupAction.run();
  }

  @Override
  public long getMinRequiredBlockNumber() {
    return genesisConfig.getTerminalBlockNumber().orElse(0L);
  }
}
