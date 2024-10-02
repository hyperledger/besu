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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class VerkleFlatDbStrategy extends FlatDbStrategy {

  protected final Counter getAccountNotFoundInFlatDatabaseCounter;

  protected final Counter getStorageValueNotFoundInFlatDatabaseCounter;

  public VerkleFlatDbStrategy(
      final MetricsSystem metricsSystem, final CodeStorageStrategy codeStorageStrategy) {
    super(metricsSystem, codeStorageStrategy);

    getAccountNotFoundInFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_missing_flat_database",
            "Number of accounts not found in the flat database");

    getStorageValueNotFoundInFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_missing_flat_database",
            "Number of storage slots not found in the flat database");
  }

  public Optional<Bytes> getStem(final Bytes stem, final SegmentedKeyValueStorage storage) {
    return storage.get(TRIE_BRANCH_STORAGE, stem.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getFlatCode(
      final Hash codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    return super.getFlatCode(codeHash, accountHash, storage);
  }

  @Override
  public void clearAll(final SegmentedKeyValueStorage storage) {
    // NOOP
    // we cannot clear flatdb in verkle as we are using directly the trie
  }

  @Override
  public void resetOnResync(final SegmentedKeyValueStorage storage) {
    // NOOP
    // not need to reset anything in full mode
  }
}
