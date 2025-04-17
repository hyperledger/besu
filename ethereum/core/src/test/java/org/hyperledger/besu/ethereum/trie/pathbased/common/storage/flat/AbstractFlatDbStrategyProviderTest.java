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
package org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractFlatDbStrategyProviderTest {

  @ParameterizedTest
  @EnumSource(FlatDbMode.class)
  public void loadsFlatDbStrategyForStoredFlatDbMode(final FlatDbMode flatDbMode) {
    final SegmentedKeyValueStorage segmentedKeyValueStorage = createSegmentedKeyValueStorage();
    updateFlatDbMode(flatDbMode, segmentedKeyValueStorage);
    final FlatDbStrategyProvider strategyProvider =
        createFlatDbStrategyProvider(
            DataStorageConfiguration.DEFAULT_CONFIG, segmentedKeyValueStorage);
    assertThat(strategyProvider.getFlatDbMode()).isEqualTo(flatDbMode);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void emptyDbCreatesFlatDbStrategyUsingCodeByHashConfig(final boolean codeByHashEnabled) {
    final DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(getDataStorageFormat())
            .pathBasedExtraStorageConfiguration(
                ImmutablePathBasedExtraStorageConfiguration.builder()
                    .maxLayersToLoad(PathBasedExtraStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD)
                    .unstable(
                        ImmutablePathBasedExtraStorageConfiguration.PathBasedUnstable.builder()
                            .codeStoredByCodeHashEnabled(codeByHashEnabled)
                            .build())
                    .build())
            .build();
    final FlatDbStrategyProvider flatDbStrategyProvider =
        createFlatDbStrategyProvider(dataStorageConfiguration);
    final Class<? extends CodeStorageStrategy> expectedCodeStorageClass =
        codeByHashEnabled
            ? CodeHashCodeStorageStrategy.class
            : AccountHashCodeStorageStrategy.class;
    assertThat(flatDbStrategyProvider.flatDbMode).isEqualTo(FlatDbMode.FULL);
    assertThat(flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy)
        .isInstanceOf(expectedCodeStorageClass);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void existingCodeHashDbUsesCodeHash(final boolean codeByHashEnabled) {
    final DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(getDataStorageFormat())
            .pathBasedExtraStorageConfiguration(
                ImmutablePathBasedExtraStorageConfiguration.builder()
                    .maxLayersToLoad(PathBasedExtraStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD)
                    .unstable(
                        ImmutablePathBasedExtraStorageConfiguration.PathBasedUnstable.builder()
                            .codeStoredByCodeHashEnabled(codeByHashEnabled)
                            .build())
                    .build())
            .build();

    final SegmentedKeyValueStorage segmentedKeyValueStorage = createSegmentedKeyValueStorage();
    final SegmentedKeyValueStorageTransaction transaction =
        segmentedKeyValueStorage.startTransaction();
    final CodeHashCodeStorageStrategy codeHashCodeStorageStrategy =
        new CodeHashCodeStorageStrategy();
    codeHashCodeStorageStrategy.putFlatCode(transaction, null, Hash.hash(Bytes.of(1)), Bytes.of(1));
    transaction.commit();

    final FlatDbStrategyProvider flatDbStrategyProvider =
        createFlatDbStrategyProvider(dataStorageConfiguration, segmentedKeyValueStorage);

    assertThat(flatDbStrategyProvider.flatDbMode).isEqualTo(FlatDbMode.FULL);
    assertThat(flatDbStrategyProvider.flatDbStrategy.codeStorageStrategy)
        .isInstanceOf(CodeHashCodeStorageStrategy.class);
  }

  protected void updateFlatDbMode(
      final FlatDbMode flatDbMode, final SegmentedKeyValueStorage segmentedKeyValueStorage) {
    final SegmentedKeyValueStorageTransaction transaction =
        segmentedKeyValueStorage.startTransaction();
    transaction.put(
        KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE,
        FlatDbStrategyProvider.FLAT_DB_MODE,
        flatDbMode.getVersion().toArrayUnsafe());
    transaction.commit();
  }

  protected SegmentedKeyValueStorage createSegmentedKeyValueStorage() {
    return new SegmentedInMemoryKeyValueStorage(
        List.of(
            KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE, KeyValueSegmentIdentifier.CODE_STORAGE));
  }

  protected abstract DataStorageFormat getDataStorageFormat();

  protected abstract FlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration,
      final SegmentedKeyValueStorage segmentedKeyValueStorage);

  protected abstract FlatDbStrategyProvider createFlatDbStrategyProvider(
      DataStorageConfiguration dataStorageConfiguration);
}
