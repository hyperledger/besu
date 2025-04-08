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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.AbstractFlatDbStrategyProviderTest;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.AccountHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BonsaiFlatDbStrategyProviderTest extends AbstractFlatDbStrategyProviderTest {

  @Test
  void loadsPartialFlatDbStrategyWhenNoFlatDbModeStored() {
    final BonsaiFlatDbStrategyProvider bonsaiFlatDbStrategyProvider =
        createFlatDbStrategyProvider(DataStorageConfiguration.DEFAULT_CONFIG);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.FULL);
  }

  @Test
  void upgradesFlatDbStrategyToFullFlatDbMode() {
    final SegmentedKeyValueStorage segmentedKeyValueStorage = createSegmentedKeyValueStorage();
    updateFlatDbMode(FlatDbMode.PARTIAL, segmentedKeyValueStorage);

    final BonsaiFlatDbStrategyProvider bonsaiFlatDbStrategyProvider =
        createFlatDbStrategyProvider(
            DataStorageConfiguration.DEFAULT_CONFIG, segmentedKeyValueStorage);
    bonsaiFlatDbStrategyProvider.upgradeToFullFlatDbMode(segmentedKeyValueStorage);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.FULL);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbStrategy()).isNotNull();
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbStrategy())
        .isInstanceOf(BonsaiFullFlatDbStrategy.class);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbStrategy().getCodeStorageStrategy())
        .isInstanceOf(CodeHashCodeStorageStrategy.class);
  }

  @Test
  void downgradesFlatDbStrategyToPartiallyFlatDbMode() {
    final SegmentedKeyValueStorage segmentedKeyValueStorage = createSegmentedKeyValueStorage();
    updateFlatDbMode(FlatDbMode.FULL, segmentedKeyValueStorage);

    final BonsaiFlatDbStrategyProvider bonsaiFlatDbStrategyProvider =
        createFlatDbStrategyProvider(
            DataStorageConfiguration.DEFAULT_CONFIG, segmentedKeyValueStorage);

    bonsaiFlatDbStrategyProvider.downgradeToPartialFlatDbMode(segmentedKeyValueStorage);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.PARTIAL);
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbStrategy()).isNotNull();
    assertThat(bonsaiFlatDbStrategyProvider.getFlatDbStrategy())
        .isInstanceOf(BonsaiPartialFlatDbStrategy.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void existingAccountHashDbUsesAccountHash(final boolean codeByHashEnabled) {
    final DataStorageConfiguration dataStorageConfiguration =
        ImmutableDataStorageConfiguration.builder()
            .dataStorageFormat(DataStorageFormat.BONSAI)
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
    final AccountHashCodeStorageStrategy accountHashCodeStorageStrategy =
        new AccountHashCodeStorageStrategy();
    // key representing account hash just needs to not be the code hash
    final Hash accountHash = Hash.wrap(Bytes32.fromHexString("0001"));
    accountHashCodeStorageStrategy.putFlatCode(transaction, accountHash, null, Bytes.of(2));
    transaction.commit();

    final FlatDbStrategyProvider flatDbStrategyProvider =
        createFlatDbStrategyProvider(dataStorageConfiguration, segmentedKeyValueStorage);
    assertThat(flatDbStrategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.FULL);
    assertThat(flatDbStrategyProvider.getFlatDbStrategy().getCodeStorageStrategy())
        .isInstanceOf(AccountHashCodeStorageStrategy.class);
  }

  @Override
  protected DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.BONSAI;
  }

  @Override
  protected BonsaiFlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration,
      final SegmentedKeyValueStorage segmentedKeyValueStorage) {
    return new BonsaiFlatDbStrategyProvider(
        new NoOpMetricsSystem(), dataStorageConfiguration, segmentedKeyValueStorage);
  }

  @Override
  protected BonsaiFlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration) {
    return createFlatDbStrategyProvider(dataStorageConfiguration, createSegmentedKeyValueStorage());
  }
}
