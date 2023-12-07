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

package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.chain.VariablesStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlatDbStrategyProviderTest {
  private final VariablesStorage variablesStorage =
      new VariablesKeyValueStorage(new InMemoryKeyValueStorage());
  private final FlatDbStrategyProvider flatDbStrategyProvider =
      new FlatDbStrategyProvider(
          new NoOpMetricsSystem(), DataStorageConfiguration.DEFAULT_CONFIG, variablesStorage);
  private final SegmentedKeyValueStorage composedWorldStateStorage =
      new SegmentedInMemoryKeyValueStorage(List.of(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE));

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  void loadsFlatDbStrategyForStoredFlatDbMode(final FlatDbMode flatDbMode) {
    updateFlatDbMode(flatDbMode);

    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.getFlatDbMode()).isEqualTo(flatDbMode);
  }

  @Test
  void loadsPartialFlatDbStrategyWhenNoFlatDbMode() {
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.PARTIAL);
  }

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  void loadsFlatDbStrategy_usingCodeHashCodeStrategy_whenCodeHashEnabled(
      final FlatDbMode flatDbMode) {
    updateFlatDbMode(flatDbMode);
    updateCodeHashMode(true);

    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy)
        .isInstanceOf(CodeHashCodeStorageStrategy.class);
  }

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  void loadsFlatDbStrategy_usingCodeHashCodeStrategy_withDeleteCodeEnabled(
      final FlatDbMode flatDbMode) {
    updateFlatDbMode(flatDbMode);
    updateCodeHashMode(true);

    final var dataStorageConfiguration = dataStorageConfig(true);
    final var flatDbStrategyProvider =
        new FlatDbStrategyProvider(
            new NoOpMetricsSystem(), dataStorageConfiguration, variablesStorage);
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);

    CodeHashCodeStorageStrategy codeStorageStrategy =
        (CodeHashCodeStorageStrategy) flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy;
    assertThat(codeStorageStrategy.deleteCode).isTrue();
  }

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  void loadsFlatDbStrategy_usingCodeHashCodeStrategy_withDeleteCodeDisabled(
      final FlatDbMode flatDbMode) {
    updateFlatDbMode(flatDbMode);
    updateCodeHashMode(true);

    final var dataStorageConfiguration = dataStorageConfig(false);
    final var flatDbStrategyProvider =
        new FlatDbStrategyProvider(
            new NoOpMetricsSystem(), dataStorageConfiguration, variablesStorage);
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);

    CodeHashCodeStorageStrategy codeStorageStrategy =
        (CodeHashCodeStorageStrategy) flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy;
    assertThat(codeStorageStrategy.deleteCode).isFalse();
  }

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  void loadsFlatDbStrategy_usingAccountHashCodeStrategy_whenCodeHashDisabled(
      final FlatDbMode flatDbMode) {
    updateFlatDbMode(flatDbMode);
    updateCodeHashMode(false);

    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy)
        .isInstanceOf(AccountHashCodeStorageStrategy.class);
  }

  @Test
  void upgradesFlatDbStrategyToFullFlatDbMode() {
    updateFlatDbMode(FlatDbMode.PARTIAL);

    flatDbStrategyProvider.upgradeToFullFlatDbMode(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.flatDbMode).isEqualTo(FlatDbMode.FULL);
    assertThat(flatDbStrategyProvider.flatDbStrategy).isNotNull();
    assertThat(flatDbStrategyProvider.getFlatDbStrategy(composedWorldStateStorage))
        .isInstanceOf(FullFlatDbStrategy.class);
  }

  @Test
  void downgradesFlatDbStrategyToPartiallyFlatDbMode() {
    updateFlatDbMode(FlatDbMode.FULL);

    flatDbStrategyProvider.downgradeToPartialFlatDbMode(composedWorldStateStorage);
    assertThat(flatDbStrategyProvider.flatDbMode).isEqualTo(FlatDbMode.PARTIAL);
    assertThat(flatDbStrategyProvider.flatDbStrategy).isNotNull();
    assertThat(flatDbStrategyProvider.getFlatDbStrategy(composedWorldStateStorage))
        .isInstanceOf(PartialFlatDbStrategy.class);
  }

  private void updateFlatDbMode(final FlatDbMode flatDbMode) {
    final SegmentedKeyValueStorageTransaction transaction =
        composedWorldStateStorage.startTransaction();
    transaction.put(
        KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE,
        FlatDbStrategyProvider.FLAT_DB_MODE,
        flatDbMode.getVersion().toArrayUnsafe());
    transaction.commit();
  }

  private void updateCodeHashMode(final boolean isCodeHash) {
    VariablesStorage.Updater updater = variablesStorage.updater();
    updater.setCodeStoredUsingCodeHash(isCodeHash);
    updater.commit();
  }

  private DataStorageConfiguration dataStorageConfig(
      final boolean bonsaiCodeStoredByCodeHashDeletionEnabled) {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(DataStorageFormat.BONSAI)
        .bonsaiMaxLayersToLoad(DataStorageConfiguration.DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiCodeStoredByCodeHashDeletionEnabled(
                    bonsaiCodeStoredByCodeHashDeletionEnabled)
                .build())
        .build();
  }
}
