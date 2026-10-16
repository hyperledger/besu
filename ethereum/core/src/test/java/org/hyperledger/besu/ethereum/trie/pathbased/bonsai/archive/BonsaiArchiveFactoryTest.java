/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveTrieNodeStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.code.BonsaiCodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.accumulator.preload.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BonsaiArchiveFactoryTest {

  private Blockchain blockchain;
  private Synchronizer synchronizer;
  private BonsaiArchiveService archive;

  @BeforeEach
  void setUp() {
    blockchain = mock(Blockchain.class);
    synchronizer = mock(Synchronizer.class);
  }

  @AfterEach
  void tearDown() {
    if (archive != null) {
      archive.close();
    }
  }

  private static DataStorageConfiguration archiveConfig(final boolean proofsEnabled) {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(DataStorageFormat.X_BONSAI_ARCHIVE)
        .extraStorageConfiguration(
            ImmutableExtraStorageConfiguration.builder()
                .unstable(
                    ImmutableExtraStorageConfiguration.Unstable.builder()
                        .bonsaiArchiveStateProofsEnabled(proofsEnabled)
                        .build())
                .build())
        .build();
  }

  private BonsaiWorldStateKeyValueStorage storage(final DataStorageConfiguration config) {
    return new BonsaiWorldStateKeyValueStorage(
        new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem(), config);
  }

  private BonsaiArchiveService createArchive(
      final DataStorageConfiguration config, final BonsaiWorldStateKeyValueStorage storage) {
    final BonsaiArchiveWorldStateProvider provider =
        new BonsaiArchiveWorldStateProvider(
            storage,
            blockchain,
            config,
            new BonsaiCachedMerkleTrieLoader(new NoOpMetricsSystem()),
            null,
            EvmConfiguration.DEFAULT,
            new BonsaiCodeCache(),
            new NoOpMetricsSystem());
    return BonsaiArchiveFactory.create(
        config,
        provider,
        new WorldStateStorageCoordinator(storage),
        blockchain,
        new NoOpMetricsSystem(),
        1);
  }

  @Test
  void proofsEnabledInstallsArchiveStrategy() {
    final DataStorageConfiguration config = archiveConfig(true);
    final BonsaiWorldStateKeyValueStorage storage = storage(config);
    archive = createArchive(config, storage);

    assertThat(storage.getTrieNodeStrategy()).isInstanceOf(ArchiveTrieNodeStrategy.class);
  }

  @Test
  void proofsDisabledLeavesBaseStrategy() {
    final DataStorageConfiguration config = archiveConfig(false);
    final BonsaiWorldStateKeyValueStorage storage = storage(config);
    archive = createArchive(config, storage);

    assertThat(storage.getTrieNodeStrategy()).isNotInstanceOf(ArchiveTrieNodeStrategy.class);
  }

  @Test
  void fullFlatModeMigratesOnFirstInSyncEventThenUnsubscribes() {
    final DataStorageConfiguration config = archiveConfig(false);
    final BonsaiWorldStateKeyValueStorage storage = storage(config);
    when(synchronizer.subscribeInSync(any(Synchronizer.InSyncListener.class), eq(0L)))
        .thenReturn(7L);
    archive = createArchive(config, storage);

    archive.start(synchronizer);

    final ArgumentCaptor<Synchronizer.InSyncListener> listener =
        ArgumentCaptor.forClass(Synchronizer.InSyncListener.class);
    verify(synchronizer).subscribeInSync(listener.capture(), eq(0L));
    listener.getValue().onInSyncStatusChange(true);

    verify(synchronizer).unsubscribeInSync(7L);
    await().until(() -> storage.getFlatDbMode() == FlatDbMode.ARCHIVE);
  }

  @Test
  void archiveFlatModeStartsOngoingMigrationWithoutSubscribing() {
    final DataStorageConfiguration config = archiveConfig(false);
    final BonsaiWorldStateKeyValueStorage storage = storage(config);
    storage.upgradeToArchiveFlatDbMode();
    archive = createArchive(config, storage);

    archive.start(synchronizer);

    verify(synchronizer, never())
        .subscribeInSync(any(Synchronizer.InSyncListener.class), anyLong());
    verify(blockchain).observeBlockAdded(any());
  }

  @Test
  void proofsGateSubscribesWithHistoricalBlockLimitTolerance() {
    final DataStorageConfiguration config = archiveConfig(true);
    final BonsaiWorldStateKeyValueStorage storage = storage(config);
    archive = createArchive(config, storage);

    archive.start(synchronizer);

    verify(synchronizer)
        .subscribeInSync(
            (ArchiveTrieNodeStrategy) storage.getTrieNodeStrategy(),
            config.getExtraStorageConfiguration().getMaxLayersToLoad());
  }

  @Test
  void secondStartThrows() {
    final DataStorageConfiguration config = archiveConfig(false);
    archive = createArchive(config, storage(config));
    archive.start(synchronizer);

    assertThatThrownBy(() -> archive.start(synchronizer)).isInstanceOf(IllegalStateException.class);
  }
}
