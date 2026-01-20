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
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage.VersionedValue;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * IMMUTABLE snapshot of cached world state storage at a specific version. This provides a
 * consistent view of the state as it existed when the snapshot was created.
 *
 * <p>Key differences from BonsaiCachedWorldStateStorage: - It's IMMUTABLE (no updater, extends
 * BonsaiSnapshotWorldStateKeyValueStorage) - It only sees cached values with version <=
 * snapshotVersion - It does NOT cache new reads (to avoid polluting the shared cache with
 * snapshot-specific data)
 */
public class BonsaiCachedSnapshotWorldStateStorage extends BonsaiSnapshotWorldStateStorage {

  private final BonsaiWorldStateKeyValueStorage parent;
  // Shared reference to the live cache's caches
  private final Map<SegmentIdentifier, Cache<Bytes, VersionedValue>> caches;
  // The version at which this snapshot was created
  private final long snapshotVersion;

  public BonsaiCachedSnapshotWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage parent,
      final Map<SegmentIdentifier, Cache<Bytes, VersionedValue>> caches,
      final long snapshotVersion) {
    super(parent);
    this.parent = parent;
    this.caches = caches;
    this.snapshotVersion = snapshotVersion;
  }

  /**
   * Get from cache if version <= snapshotVersion, otherwise get from parent. Does NOT cache the
   * result (snapshots don't modify the cache).
   */
  private Optional<Bytes> getFromCacheOrParent(
      final SegmentIdentifier segment,
      final Bytes key,
      final Supplier<Optional<Bytes>> parentGetter) {

    final Cache<Bytes, VersionedValue> cache = caches.get(segment);
    if (cache == null) {
      return parentGetter.get();
    }

    final VersionedValue versionedValue = cache.getIfPresent(key);

    // Only return cached values that existed at or before snapshot time
    if (versionedValue != null && versionedValue.version <= snapshotVersion) {
      return versionedValue.isRemoval ? Optional.empty() : Optional.of(versionedValue.value);
    }

    // Not in cache at our version, get from parent
    // Do NOT cache the result - snapshots are read-only views
    return parentGetter.get();
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFromCacheOrParent(
        ACCOUNT_INFO_STATE, accountHash, () -> parent.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return getFromCacheOrParent(
        CODE_STORAGE, accountHash, () -> parent.getCode(codeHash, accountHash));
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return getFromCacheOrParent(
        TRIE_BRANCH_STORAGE, nodeHash, () -> parent.getAccountStateTrieNode(location, nodeHash));
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return getFromCacheOrParent(
        TRIE_BRANCH_STORAGE,
        nodeHash,
        () -> parent.getAccountStorageTrieNode(accountHash, location, nodeHash));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    return getFromCacheOrParent(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () -> parent.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final Bytes key = Bytes.concatenate(accountHash, storageSlotKey.getSlotHash());
    return getFromCacheOrParent(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () ->
            parent.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }
}
