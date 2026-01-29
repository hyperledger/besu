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

import java.util.List;
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

  private final VersionedCacheManager cacheManager;
  private final long snapshotVersion;

  public BonsaiCachedSnapshotWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage parent,
      final VersionedCacheManager cacheManager,
      final long snapshotVersion) {
    super(parent);
    this.cacheManager = cacheManager;
    this.snapshotVersion = snapshotVersion;
  }

  @Override
  public Optional<Bytes> getAccount(final Hash accountHash) {
    return cacheManager.getFromCacheOrSnapshotStorage(
        ACCOUNT_INFO_STATE,
        accountHash.getBytes().toArrayUnsafe(),
        snapshotVersion,
        () -> super.getAccount(accountHash));
  }

  @Override
  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    }
    return cacheManager.getFromCacheOrSnapshotStorage(
        CODE_STORAGE,
        accountHash.getBytes().toArrayUnsafe(),
        snapshotVersion,
        () -> super.getCode(codeHash, accountHash));
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return cacheManager.getFromCacheOrSnapshotStorage(
        TRIE_BRANCH_STORAGE,
        nodeHash.toArrayUnsafe(),
        snapshotVersion,
        () -> super.getAccountStateTrieNode(location, nodeHash));
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return cacheManager.getFromCacheOrSnapshotStorage(
        TRIE_BRANCH_STORAGE,
        nodeHash.toArrayUnsafe(),
        snapshotVersion,
        () -> super.getAccountStorageTrieNode(accountHash, location, nodeHash));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return cacheManager.getFromCacheOrSnapshotStorage(
        ACCOUNT_STORAGE_STORAGE,
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe(),
        snapshotVersion,
        () -> super.getStorageValueByStorageSlotKey(accountHash, storageSlotKey));
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    return cacheManager.getFromCacheOrSnapshotStorage(
        ACCOUNT_STORAGE_STORAGE,
        Bytes.concatenate(accountHash.getBytes(), storageSlotKey.getSlotHash().getBytes())
            .toArrayUnsafe(),
        snapshotVersion,
        () ->
            super.getStorageValueByStorageSlotKey(
                storageRootSupplier, accountHash, storageSlotKey));
  }

  @Override
  public List<Optional<byte[]>> getMultipleKeys(
      final SegmentIdentifier segmentIdentifier, final List<byte[]> keys) {
    return cacheManager.getMultipleFromCacheOrSnapshotStorage(
        segmentIdentifier,
        keys,
        snapshotVersion,
        keysToFetch -> super.getMultipleKeys(segmentIdentifier, keysToFetch));
  }
}
