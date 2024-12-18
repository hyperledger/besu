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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat;

import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

public class VerkleFlatDbStrategyProvider extends FlatDbStrategyProvider {

  public VerkleFlatDbStrategyProvider(
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration,
      final SegmentedKeyValueStorage segmentedKeyValueStorage) {
    super(metricsSystem, dataStorageConfiguration, segmentedKeyValueStorage);
  }

  @Override
  protected FlatDbMode getRequestedFlatDbMode(
      final DataStorageConfiguration dataStorageConfiguration) {
    return dataStorageConfiguration
            .getVerkleSubStorageConfiguration()
            .getUnstable()
            .getStemFlatDbEnabled()
        ? FlatDbMode.STEM
        : FlatDbMode.FULL;
  }

  @Override
  protected FlatDbMode alternativeFlatDbModeForExistingDatabase() {
    return FlatDbMode.FULL;
  }

  @Override
  protected FlatDbStrategy createFlatDbStrategy(
      final FlatDbMode flatDbMode,
      final MetricsSystem metricsSystem,
      final CodeStorageStrategy codeStorageStrategy) {
    if (flatDbMode == FlatDbMode.FULL) {
      return new VerkleLegacyFlatDbStrategy(metricsSystem, codeStorageStrategy);
    } else {
      return new VerkleStemFlatDbStrategy(metricsSystem, codeStorageStrategy);
    }
  }
}
