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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.VERKLE_TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkleFlatDbStrategyProvider extends FlatDbStrategyProvider {

  private static final Logger LOG = LoggerFactory.getLogger(VerkleFlatDbStrategyProvider.class);

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

  @VisibleForTesting
  @Override
  protected FlatDbMode deriveFlatDbStrategy(
      final SegmentedKeyValueStorage composedWorldStateStorage) {
    final FlatDbMode requestedFlatDbMode = getRequestedFlatDbMode(dataStorageConfiguration);

    final var existingTrieData =
        composedWorldStateStorage.get(VERKLE_TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY).isPresent();

    var flatDbMode =
        FlatDbMode.fromVersion(
            composedWorldStateStorage
                .get(VERKLE_TRIE_BRANCH_STORAGE, FLAT_DB_MODE)
                .map(Bytes::wrap)
                .orElseGet(
                    () -> {
                      // if we do not have a db-supplied config for flatdb, derive it:
                      // default to partial if trie data exists, but the flat config does not,
                      // and default to the storage config otherwise
                      var flatDbModeVal =
                          existingTrieData
                              ? alternativeFlatDbModeForExistingDatabase().getVersion()
                              : requestedFlatDbMode.getVersion();
                      // persist this config in the db
                      var setDbModeTx = composedWorldStateStorage.startTransaction();
                      setDbModeTx.put(
                          VERKLE_TRIE_BRANCH_STORAGE, FLAT_DB_MODE, flatDbModeVal.toArrayUnsafe());
                      setDbModeTx.commit();

                      return flatDbModeVal;
                    }));
    LOG.info("Flat db mode found {}", flatDbMode);

    return flatDbMode;
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
