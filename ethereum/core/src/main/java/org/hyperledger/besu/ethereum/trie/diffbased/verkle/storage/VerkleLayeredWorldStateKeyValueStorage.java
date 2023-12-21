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
 *
 */
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage;

import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.LayeredKeyValueStorage;

public class VerkleLayeredWorldStateKeyValueStorage extends VerkleSnapshotWorldStateKeyValueStorage
    implements DiffBasedLayeredWorldStateKeyValueStorage, StorageSubscriber {

  public VerkleLayeredWorldStateKeyValueStorage(final VerkleWorldStateKeyValueStorage parent) {
    this(
        new LayeredKeyValueStorage(parent.getComposedWorldStateStorage()),
        parent.getTrieLogStorage(),
        parent,
        parent.getMetricsSystem());
  }

  public VerkleLayeredWorldStateKeyValueStorage(
      final SnappedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final VerkleWorldStateKeyValueStorage parent,
      final ObservableMetricsSystem metricsSystem) {
    super(parent, composedWorldStateStorage, trieLogStorage, metricsSystem);
  }

  @Override
  public VerkleLayeredWorldStateKeyValueStorage clone() {
    return new VerkleLayeredWorldStateKeyValueStorage(
        ((LayeredKeyValueStorage) composedWorldStateStorage).clone(),
        trieLogStorage,
        parentWorldStateStorage,
        metricsSystem);
  }
}
