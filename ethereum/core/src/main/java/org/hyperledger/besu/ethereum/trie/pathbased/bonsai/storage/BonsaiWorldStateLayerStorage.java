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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.pathbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedLayeredWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.LayeredKeyValueStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

/**
 * Layered world state storage with integrated cache reading.
 *
 * <p>This is the ONLY storage component that reads from cache.
 *
 * <p>Read strategy (3 levels): 1. Check layer's in-memory changes (most recent, uncommitted) 2. If
 * not in layer, check cache (using version from snapshot) 3. If not in cache, check parent storage
 *
 * <p>The layer avoids LayeredKeyValueStorage's automatic fallback to parent, allowing cache
 * interception between layer and parent.
 */
public class BonsaiWorldStateLayerStorage extends BonsaiSnapshotWorldStateStorage
    implements PathBasedLayeredWorldStateKeyValueStorage, StorageSubscriber {

  public BonsaiWorldStateLayerStorage(final BonsaiWorldStateKeyValueStorage parent) {
    this(
        new LayeredKeyValueStorage(parent.getComposedWorldStateStorage()),
        parent.getTrieLogStorage(),
        parent);
  }

  protected BonsaiWorldStateLayerStorage(
      final SnappedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final BonsaiWorldStateKeyValueStorage parent) {
    super(parent, composedWorldStateStorage, trieLogStorage);
  }

  /**
   * Get value from layer only (no fallback to parent or cache). Returns null if key not present in
   * layer.
   */
  private Optional<byte[]> getFromLayerOnly(final SegmentIdentifier segment, final byte[] key) {
    final NavigableMap<Bytes, Optional<byte[]>> segmentMap =
        getComposedWorldStateStorage().getHashValueStore().get(segment);
    if (segmentMap == null) {
      return null; // Segment not modified in layer
    }
    return segmentMap.get(Bytes.wrap(key)); // null if key not in layer
  }

  /** Get multiple values from layer only. Returns null for keys not present in layer. */
  private List<Optional<byte[]>> getMultipleFromLayerOnly(
      final SegmentIdentifier segment, final List<byte[]> keys) {
    final NavigableMap<Bytes, Optional<byte[]>> segmentMap =
        getComposedWorldStateStorage().getHashValueStore().get(segment);
    final List<Optional<byte[]>> results = new ArrayList<>(keys.size());

    if (segmentMap == null) {
      // Segment not modified in layer - all keys are missing
      for (int i = 0; i < keys.size(); i++) {
        results.add(null);
      }
      return results;
    }

    // Check each key in layer
    for (byte[] key : keys) {
      results.add(segmentMap.get(Bytes.wrap(key))); // null if not in layer
    }

    return results;
  }

  /** Three-level read: Layer -> Cache -> Parent. */
  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    final byte[] key = accountHash.getBytes().toArrayUnsafe();

    // Level 1: Check layer
    final Optional<byte[]> layerResult = getFromLayerOnly(ACCOUNT_INFO_STATE, key);
    if (layerResult != null) {
      return layerResult.map(Bytes::wrap);
    }

    // Level 2 & 3: Check cache, then parent
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_INFO_STATE,
        key,
        getCurrentVersion(),
        () -> parentWorldStateStorage.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    final byte[] key =
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe();

    // Level 1: Check layer
    final Optional<byte[]> layerResult = getFromLayerOnly(ACCOUNT_STORAGE_STORAGE, key);
    if (layerResult != null) {
      return layerResult.map(Bytes::wrap);
    }

    // Level 2 & 3: Check cache, then parent
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_STORAGE_STORAGE,
        key,
        getCurrentVersion(),
        () -> parentWorldStateStorage.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final byte[] key =
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe();

    // Level 1: Check layer
    final Optional<byte[]> layerResult = getFromLayerOnly(ACCOUNT_STORAGE_STORAGE, key);
    if (layerResult != null) {
      return layerResult.map(Bytes::wrap);
    }

    // Level 2 & 3: Check cache, then parent
    return cacheManager.getFromCacheOrStorage(
        ACCOUNT_STORAGE_STORAGE,
        key,
        getCurrentVersion(),
        () ->
            parentWorldStateStorage.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }

  @Override
  public List<Optional<byte[]>> getMultipleKeys(
      final SegmentIdentifier segmentIdentifier, final List<byte[]> keys) {

    // Level 1: Get from layer (without fallback)
    final List<Optional<byte[]>> layerResults = getMultipleFromLayerOnly(segmentIdentifier, keys);

    // Find keys missing from layer
    final List<byte[]> missingKeys = new ArrayList<>();
    final List<Integer> missingIndices = new ArrayList<>();

    for (int i = 0; i < keys.size(); i++) {
      if (layerResults.get(i) == null) {
        missingKeys.add(keys.get(i));
        missingIndices.add(i);
      }
    }

    if (missingKeys.isEmpty()) {
      // All keys found in layer - just return as-is
      return layerResults;
    }

    // Level 2 & 3: Get missing keys from cache/parent
    final List<Optional<byte[]>> cachedResults =
        cacheManager.getMultipleFromCacheOrStorage(
            segmentIdentifier,
            missingKeys,
            getCurrentVersion(),
            keysToFetch -> parentWorldStateStorage.getMultipleKeys(segmentIdentifier, keysToFetch));

    // Merge results: layer results + cache/parent results
    final List<Optional<byte[]>> finalResults = new ArrayList<>(layerResults);
    for (int i = 0; i < missingIndices.size(); i++) {
      finalResults.set(missingIndices.get(i), cachedResults.get(i));
    }

    return finalResults;
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

  /** Merge this layer to a storage transaction. */
  @Override
  public void mergeTo(final SegmentedKeyValueStorageTransaction transaction) {
    getComposedWorldStateStorage().mergeTo(transaction);
  }

  @Override
  public LayeredKeyValueStorage getComposedWorldStateStorage() {
    return (LayeredKeyValueStorage) super.getComposedWorldStateStorage();
  }
}
