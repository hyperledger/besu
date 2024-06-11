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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache;

import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.VERKLE_NODE_WIDTH;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.CachedPedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TrieKeyPreloader {
  private static final int TRIE_KEY_CACHE_SIZE = 10_000;
  private final Cache<Address, Map<Bytes32, Bytes32>> trieKeysCache =
      CacheBuilder.newBuilder().maximumSize(TRIE_KEY_CACHE_SIZE).build();

  private final TrieKeyAdapter trieKeyAdapter;

  private final Hasher hasher;

  private long missedTrieKeys = 0;

  public TrieKeyPreloader() {
    this.hasher = new PedersenHasher();
    this.trieKeyAdapter = new TrieKeyAdapter(hasher);
    this.trieKeyAdapter.versionKey(
        Address.ZERO); // TODO REMOVE is just to preload the native library for performance check
  }

  public HasherContext createPreloadedHasher(
      final Address address,
      final Map<StorageSlotKey, DiffBasedValue<UInt256>> storageUpdate,
      final DiffBasedValue<Bytes> codeUpdate) {

    // generate account triekeys
    final List<Bytes32> accountKeyIds = new ArrayList<>(generateAccountKeyIds());

    // generate storage triekeys
    final List<Bytes32> storageKeyIds = new ArrayList<>();
    boolean isStorageUpdateNeeded;
    if (storageUpdate != null) {
      final Set<StorageSlotKey> storageSlotKeys = storageUpdate.keySet();
      isStorageUpdateNeeded = !storageSlotKeys.isEmpty();
      if (isStorageUpdateNeeded) {
        storageKeyIds.addAll(generateStorageKeyIds(storageSlotKeys));
      }
    }

    // generate code triekeys
    final List<Bytes32> codeChunkIds = new ArrayList<>();
    boolean isCodeUpdateNeeded;
    if (codeUpdate != null) {
      final Bytes previousCode = codeUpdate.getPrior();
      final Bytes updatedCode = codeUpdate.getUpdated();
      isCodeUpdateNeeded =
          !codeUpdate.isUnchanged() && !(codeIsEmpty(previousCode) && codeIsEmpty(updatedCode));
      if (isCodeUpdateNeeded) {
        accountKeyIds.add(Parameters.CODE_SIZE_LEAF_KEY);
        codeChunkIds.addAll(
            generateCodeChunkKeyIds(updatedCode == null ? previousCode : updatedCode));
      }
    }

    return new HasherContext(
        new CachedPedersenHasher(
            generateManyTrieKeyHashes(address, accountKeyIds, storageKeyIds, codeChunkIds, true)),
        !storageKeyIds.isEmpty(),
        !codeChunkIds.isEmpty());
  }

  public void preLoadAccount(final Address account) {
    CompletableFuture.runAsync(() -> cacheAccountTrieKeys(account));
  }

  public void preLoadStorageSlot(final Address account, final StorageSlotKey slotKey) {
    CompletableFuture.runAsync(() -> cacheSlotTrieKeys(account, slotKey));
  }

  public void preLoadCode(final Address account, final Bytes code) {
    CompletableFuture.runAsync(() -> cacheCodeTrieKeys(account, code));
  }

  public Map<Bytes32, Bytes32> generateManyTrieKeyHashes(
      final Address address,
      final List<Bytes32> accountKeyIds,
      final List<Bytes32> storageKeyIds,
      final List<Bytes32> codeChunkIds,
      final boolean checkCacheBeforeGeneration) {

    final Set<Bytes32> offsetsToGenerate = new HashSet<>();

    if (!accountKeyIds.isEmpty()) {
      offsetsToGenerate.add(UInt256.ZERO);
    }
    for (Bytes32 storageKey : storageKeyIds) {
      offsetsToGenerate.add(trieKeyAdapter.locateStorageKeyOffset(storageKey));
    }
    for (Bytes32 codeChunkId : codeChunkIds) {
      final UInt256 codeChunkOffset = trieKeyAdapter.locateCodeChunkKeyOffset(codeChunkId);
      offsetsToGenerate.add(codeChunkOffset.divide(VERKLE_NODE_WIDTH));
    }

    if (checkCacheBeforeGeneration) {

      // not regenerate data already preloaded
      final Map<Bytes32, Bytes32> cachedTrieKeys = getCachedTrieKeysForAccount(address);
      cachedTrieKeys.forEach((key, __) -> offsetsToGenerate.remove(key));
      missedTrieKeys += offsetsToGenerate.size();

      if (offsetsToGenerate.isEmpty()) {
        return cachedTrieKeys;
      }

      final Map<Bytes32, Bytes32> trieKeyHashes =
          hasher.manyTrieKeyHashes(address, new ArrayList<>(offsetsToGenerate));
      trieKeyHashes.putAll(cachedTrieKeys);

      return trieKeyHashes;

    } else {
      return hasher.manyTrieKeyHashes(address, new ArrayList<>(offsetsToGenerate));
    }
  }

  @VisibleForTesting
  public Cache<Address, Map<Bytes32, Bytes32>> getTrieKeysCache() {
    return trieKeysCache;
  }

  public long getMissedTrieKeys() {
    return missedTrieKeys;
  }

  public void reset() {
    missedTrieKeys = 0;
  }

  @VisibleForTesting
  public void cacheAccountTrieKeys(final Address account) {
    try {
      final Map<Bytes32, Bytes32> cache = trieKeysCache.get(account, this::createAccountCache);
      cache.putAll(
          generateManyTrieKeyHashes(
              account,
              generateAccountKeyIds(),
              Collections.emptyList(),
              Collections.emptyList(),
              false));
    } catch (ExecutionException e) {
      // no op
    }
  }

  @VisibleForTesting
  public void cacheSlotTrieKeys(final Address account, final StorageSlotKey slotKey) {
    try {
      final Map<Bytes32, Bytes32> cache = trieKeysCache.get(account, this::createAccountCache);
      if (slotKey.getSlotKey().isPresent()) {
        cache.putAll(
            generateManyTrieKeyHashes(
                account,
                Collections.emptyList(),
                List.of(slotKey.getSlotKey().get()),
                Collections.emptyList(),
                false));
      }
    } catch (ExecutionException e) {
      // no op
    }
  }

  @VisibleForTesting
  public void cacheCodeTrieKeys(final Address account, final Bytes code) {
    try {
      final Map<Bytes32, Bytes32> cache = trieKeysCache.get(account, this::createAccountCache);
      cache.putAll(
          generateManyTrieKeyHashes(
              account,
              Collections.emptyList(),
              Collections.emptyList(),
              generateCodeChunkKeyIds(code),
              false));
    } catch (ExecutionException e) {
      // no op
    }
  }

  private List<Bytes32> generateAccountKeyIds() {
    final List<Bytes32> keys = new ArrayList<>();
    keys.add(Parameters.VERSION_LEAF_KEY);
    keys.add(Parameters.BALANCE_LEAF_KEY);
    keys.add(Parameters.NONCE_LEAF_KEY);
    keys.add(Parameters.CODE_KECCAK_LEAF_KEY);
    return keys;
  }

  private List<Bytes32> generateCodeChunkKeyIds(final Bytes code) {
    return new ArrayList<>(
        IntStream.range(0, trieKeyAdapter.getNbChunk(code)).mapToObj(UInt256::valueOf).toList());
  }

  private List<Bytes32> generateStorageKeyIds(final Set<StorageSlotKey> storageSlotKeys) {
    return storageSlotKeys.stream()
        .map(storageSlotKey -> storageSlotKey.getSlotKey().orElseThrow())
        .map(Bytes32::wrap)
        .toList();
  }

  private Map<Bytes32, Bytes32> createAccountCache() {
    return new ConcurrentHashMap<>();
  }

  private Map<Bytes32, Bytes32> getCachedTrieKeysForAccount(final Address address) {
    return Optional.ofNullable(trieKeysCache.getIfPresent(address))
        .orElseGet(ConcurrentHashMap::new);
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  public record HasherContext(Hasher hasher, boolean hasStorageTrieKeys, boolean hasCodeTrieKeys) {}
}
