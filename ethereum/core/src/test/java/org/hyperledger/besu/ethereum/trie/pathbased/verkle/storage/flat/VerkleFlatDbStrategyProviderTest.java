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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.AbstractFlatDbStrategyProviderTest;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerkleFlatDbStrategyProviderTest extends AbstractFlatDbStrategyProviderTest {

  @Test
  void loadsLegacyFlatDbStrategyWhenNoFlatDbModeStored() {
    final FlatDbStrategyProvider strategyProvider =
        createFlatDbStrategyProvider(DataStorageConfiguration.DEFAULT_CONFIG);
    assertThat(strategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.FULL);
    assertThat(strategyProvider.getFlatDbStrategy()).isInstanceOf(VerkleLegacyFlatDbStrategy.class);
  }

  @Test
  void loadsStemBasedFlatDbStrategyWhenConfigured() {
    final FlatDbStrategyProvider strategyProvider =
        createFlatDbStrategyProvider(DataStorageConfiguration.DEFAULT_VERKLE_STEM_DB_CONFIG);
    assertThat(strategyProvider.getFlatDbMode()).isEqualTo(FlatDbMode.STEM);
    assertThat(strategyProvider.getFlatDbStrategy()).isInstanceOf(VerkleStemFlatDbStrategy.class);
  }

  @Override
  protected DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.VERKLE;
  }

  @Override
  protected FlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration,
      final SegmentedKeyValueStorage segmentedKeyValueStorage) {
    return new VerkleFlatDbStrategyProvider(
        new NoOpMetricsSystem(), dataStorageConfiguration, segmentedKeyValueStorage);
  }

  @Override
  protected FlatDbStrategyProvider createFlatDbStrategyProvider(
      final DataStorageConfiguration dataStorageConfiguration) {
    return createFlatDbStrategyProvider(dataStorageConfiguration, createSegmentedKeyValueStorage());
  }
}
