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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage;

import org.hyperledger.besu.ethereum.trie.pathbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.LayeredKeyValueStorage;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.tuweni.bytes.Bytes;

public class BonsaiWorldStateLayerStorage extends BonsaiSnapshotWorldStateKeyValueStorage
    implements PathBasedLayeredWorldStateKeyValueStorage, StorageSubscriber {

  public BonsaiWorldStateLayerStorage(final BonsaiWorldStateKeyValueStorage parent) {
    this(
        new LayeredKeyValueStorage(parent.getComposedWorldStateStorage()),
        parent.getTrieLogStorage(),
        parent);
  }

  public BonsaiWorldStateLayerStorage(
      final SnappedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final BonsaiWorldStateKeyValueStorage parent) {
    super(parent, composedWorldStateStorage, trieLogStorage);
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return parentWorldStateStorage.getFlatDbMode();
  }

  @Override
  public BonsaiWorldStateLayerStorage clone() {
    return new BonsaiWorldStateLayerStorage(
        ((LayeredKeyValueStorage) composedWorldStateStorage).clone(),
        trieLogStorage,
        parentWorldStateStorage);
  }

  @Override
  public ConcurrentMap<SegmentIdentifier, NavigableMap<Bytes, Optional<byte[]>>>
      streamUpdatedEntries() {
    final ConcurrentMap<SegmentIdentifier, NavigableMap<Bytes, Optional<byte[]>>> map;
    if (parentWorldStateStorage
        instanceof PathBasedLayeredWorldStateKeyValueStorage layeredWorldStateKeyValueStorage) {
      map = layeredWorldStateKeyValueStorage.streamUpdatedEntries();
    } else {
      map = new ConcurrentHashMap<>();
    }
    getComposedWorldStateStorage()
        .getHashValueStore()
        .forEach(
            (segmentIdentifier, entries) -> {
              NavigableMap<Bytes, Optional<byte[]>> subMap = map.get(segmentIdentifier);
              if (subMap == null) {
                map.put(segmentIdentifier, entries);
              } else {
                subMap.putAll(entries);
              }
            });
    return map;
  }

  @Override
  public LayeredKeyValueStorage getComposedWorldStateStorage() {
    return (LayeredKeyValueStorage) super.getComposedWorldStateStorage();
  }
}
