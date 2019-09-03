/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.TrailingPeerRequirements;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

public class FullSyncDownloader<C> {

  private final ChainDownloader chainDownloader;
  private final SynchronizerConfiguration syncConfig;
  private final ProtocolContext<C> protocolContext;
  private final SyncState syncState;

  public FullSyncDownloader(
      final SynchronizerConfiguration syncConfig,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {
    this.syncConfig = syncConfig;
    this.protocolContext = protocolContext;
    this.syncState = syncState;

    this.chainDownloader =
        FullSyncChainDownloader.create(
            syncConfig, protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);
  }

  public void start() {
    chainDownloader.start();
  }

  public void stop() {
    chainDownloader.cancel();
  }

  public TrailingPeerRequirements calculateTrailingPeerRequirements() {
    return syncState.isInSync()
        ? TrailingPeerRequirements.UNRESTRICTED
        : new TrailingPeerRequirements(
            protocolContext.getBlockchain().getChainHeadBlockNumber(),
            syncConfig.getMaxTrailingPeers());
  }
}
