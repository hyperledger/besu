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
package org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDiffBasedSubStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

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
            .diffBasedSubStorageConfiguration(
                ImmutableDiffBasedSubStorageConfiguration.builder()
                    .maxLayersToLoad(DiffBasedSubStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD)
                    .unstable(
                        ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder()
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
            .diffBasedSubStorageConfiguration(
                ImmutableDiffBasedSubStorageConfiguration.builder()
                    .maxLayersToLoad(DiffBasedSubStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD)
                    .unstable(
                        ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder()
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

  protected abstract void updateFlatDbMode(
      final FlatDbMode flatDbMode, final SegmentedKeyValueStorage segmentedKeyValueStorage);

  protected abstract SegmentedKeyValueStorage createSegmentedKeyValueStorage();

  protected abstract DataStorageFormat getDataStorageFormat();

  protected abstract FlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration,
      final SegmentedKeyValueStorage segmentedKeyValueStorage);

  protected abstract FlatDbStrategyProvider createFlatDbStrategyProvider(
      DataStorageConfiguration dataStorageConfiguration);
}
