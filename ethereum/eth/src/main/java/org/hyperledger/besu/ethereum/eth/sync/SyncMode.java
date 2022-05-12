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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.FastDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.CompositeSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.FullSyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapDownloaderFactory;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.nio.file.Path;
import java.time.Clock;

public enum SyncMode {
  // Fully validate all blocks as they sync
  FULL {
    @Override
    public SyncDownloader create(
        final PivotBlockSelector pivotBlockSelector,
        final SynchronizerConfiguration syncConfig,
        final Path dataDirectory,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final MetricsSystem metricsSystem,
        final EthContext ethContext,
        final WorldStateStorage worldStateStorage,
        final SyncState syncState,
        final Clock clock,
        final SyncTerminationCondition terminationCondition) {
      return new FullSyncDownloader(
          syncConfig,
          protocolSchedule,
          protocolContext,
          ethContext,
          syncState,
          metricsSystem,
          terminationCondition);
    }
  },
  // Perform light validation on older blocks, and switch to full validation for more recent blocks
  FAST {
    @Override
    public SyncDownloader create(
        final PivotBlockSelector pivotBlockSelector,
        final SynchronizerConfiguration syncConfig,
        final Path dataDirectory,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final MetricsSystem metricsSystem,
        final EthContext ethContext,
        final WorldStateStorage worldStateStorage,
        final SyncState syncState,
        final Clock clock,
        final SyncTerminationCondition terminationCondition) {
      return new CompositeSyncDownloader(
          FastDownloaderFactory.create(
                  pivotBlockSelector,
                  syncConfig,
                  dataDirectory,
                  protocolSchedule,
                  protocolContext,
                  metricsSystem,
                  ethContext,
                  worldStateStorage,
                  syncState,
                  clock)
              .orElseThrow(),
          FAST.create(
              pivotBlockSelector,
              syncConfig,
              dataDirectory,
              protocolSchedule,
              protocolContext,
              metricsSystem,
              ethContext,
              worldStateStorage,
              syncState,
              clock,
              terminationCondition));
    }
  },
  // Perform snapsync
  X_SNAP {
    @Override
    public SyncDownloader create(
        final PivotBlockSelector pivotBlockSelector,
        final SynchronizerConfiguration syncConfig,
        final Path dataDirectory,
        final ProtocolSchedule protocolSchedule,
        final ProtocolContext protocolContext,
        final MetricsSystem metricsSystem,
        final EthContext ethContext,
        final WorldStateStorage worldStateStorage,
        final SyncState syncState,
        final Clock clock,
        final SyncTerminationCondition terminationCondition) {
      return SnapDownloaderFactory.createSnapDownloader(
              pivotBlockSelector,
              syncConfig,
              dataDirectory,
              protocolSchedule,
              protocolContext,
              metricsSystem,
              ethContext,
              worldStateStorage,
              syncState,
              clock)
          .orElseThrow();
    }
  };

  public static SyncMode fromString(final String str) {
    for (final SyncMode mode : SyncMode.values()) {
      if (mode.name().equalsIgnoreCase(str)) {
        return mode;
      }
    }
    return null;
  }

  public abstract SyncDownloader create(
      final PivotBlockSelector pivotBlockSelector,
      final SynchronizerConfiguration syncConfig,
      final Path dataDirectory,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final WorldStateStorage worldStateStorage,
      final SyncState syncState,
      final Clock clock,
      final SyncTerminationCondition terminationCondition);
}
