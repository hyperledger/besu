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
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage.VersionedCacheManager;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * IMMUTABLE snapshot of cached world state storage at a specific version. This provides a
 * consistent view of the state as it existed when the snapshot was created.
 *
 * <p>Key differences from BonsaiCachedWorldStateStorage: - It's IMMUTABLE (no updater, extends
 * BonsaiSnapshotWorldStateKeyValueStorage) - It only sees cached values with version <=
 * snapshotVersion - It only caches new reads if snapshotVersion matches current global version
 */
public class BonsaiCachedSnapshotWorldStateStorage extends BonsaiSnapshotWorldStateStorage {

  // Shared cache manager
  private final VersionedCacheManager cacheManager;
  // The version at which this snapshot was created
  private final long snapshotVersion;

  public BonsaiCachedSnapshotWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage parent,
      final VersionedCacheManager cacheManager,
      final long snapshotVersion) {
    super(parent);
    this.cacheManager = cacheManager;
    this.snapshotVersion = snapshotVersion;
  }

  /**
   * Get from cache if version <= snapshotVersion, otherwise get from snapshot storage. ONLY updates
   * the cache with read-through values if snapshotVersion == current global version.
   */
  private Optional<Bytes> getFromCacheOrSnapshot(
      final SegmentIdentifier segment,
      final Bytes key,
      final Supplier<Optional<Bytes>> snapshotGetter) {

    return cacheManager.getFromCacheOrSnapshotStorage(
        segment, key, snapshotVersion, snapshotGetter);
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFromCacheOrSnapshot(
        ACCOUNT_INFO_STATE, accountHash.getBytes(), () -> super.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return getFromCacheOrSnapshot(
        CODE_STORAGE, accountHash.getBytes(), () -> super.getCode(codeHash, accountHash));
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return getFromCacheOrSnapshot(
        TRIE_BRANCH_STORAGE, nodeHash, () -> super.getAccountStateTrieNode(location, nodeHash));
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return getFromCacheOrSnapshot(
        TRIE_BRANCH_STORAGE,
        nodeHash,
        () -> super.getAccountStorageTrieNode(accountHash, location, nodeHash));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    final Bytes key =
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes());
    return getFromCacheOrSnapshot(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () -> super.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    final Bytes key =
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes());
    return getFromCacheOrSnapshot(
        ACCOUNT_STORAGE_STORAGE,
        key,
        () ->
            super.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }
}
