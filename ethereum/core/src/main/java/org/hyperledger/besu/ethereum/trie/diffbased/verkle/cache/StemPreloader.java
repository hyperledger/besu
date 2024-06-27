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

/**
 * Preloads stems for accounts, storage slots, and code. This class is designed to optimize block
 * processing by caching trie stems associated with specific accounts, storage slots, and code data.
 * By preloading these stems, the class aims to reduce the computational overhead and access times
 * typically required during the state root computation.
 *
 * <p>The preloading process involves generating and caching stems for:
 *
 * <ul>
 *   <li>Account-related keys (e.g., version, balance, nonce, and code hash keys)
 *   <li>Storage slot keys for a given account
 *   <li>Code chunk keys for smart contract code associated with an account
 * </ul>
 *
 * <p>This class utilizes a {@link Cache} to store preloaded stems, with the cache size configurable
 * through the {@code STEM_CACHE_SIZE} constant. The {@link Hasher} used for stem generation is
 * configurable, allowing for different hashing strategies (e.g., Pedersen hashing) to be employed.
 *
 * @see org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter
 * @see org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher
 * @see org.hyperledger.besu.ethereum.trie.verkle.hasher.CachedPedersenHasher
 */
public class StemPreloader {
  private static final int STEM_CACHE_SIZE = 10_000;
  private final Cache<Address, Map<Bytes32, Bytes>> stemByAddressCache =
      CacheBuilder.newBuilder().maximumSize(STEM_CACHE_SIZE).build();

  private final TrieKeyAdapter trieKeyAdapter;

  private final Hasher hasher;

  private long missedStemCounter = 0;

  private long cachedStemCounter = 0;

  public StemPreloader() {
    this.hasher = new PedersenHasher();
    this.trieKeyAdapter = new TrieKeyAdapter(hasher);
    this.trieKeyAdapter.versionKey(
        Address.ZERO); // TODO REMOVE is just to preload the native library for performance check
  }

  /**
   * Creates a preloaded hasher context for a given updated address, storage, and code. This method
   * preloads stems for account keys, storage slot keys, and code chunk keys based on the provided
   * updates. It then returns a {@link HasherContext} containing the preloaded stems and flags
   * indicating whether storage or code trie keys are present.
   *
   * <p>This method is particularly useful for optimizing the hashing process during state root
   * computation by ensuring that stems are preloaded and readily available, thereby reducing the
   * need for on-the-fly stem generation.
   *
   * @param address the address for which to preload trie stems
   * @param storageUpdate a map of storage slot keys to their updated values
   * @param codeUpdate the updated code value, encapsulated in a {@link DiffBasedValue}
   * @return a {@link HasherContext} containing the preloaded stems and flags for storage and code
   *     trie key presence
   */
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
            STEM_CACHE_SIZE,
            generateManyStems(address, accountKeyIds, storageKeyIds, codeChunkIds, true)),
        !storageKeyIds.isEmpty(),
        !codeChunkIds.isEmpty());
  }

  /**
   * Asynchronously preloads stems for a given account address. This method is designed to optimize
   * the access to account-related stems by caching them ahead of time, thus reducing the
   * computational overhead during state root computation.
   *
   * @param account the address of the account for which stems are to be preloaded
   */
  public void preLoadAccount(final Address account) {
    CompletableFuture.runAsync(() -> cacheAccountStems(account));
  }

  /**
   * Asynchronously preloads stems for a specific storage slot associated with an account. This
   * method enhances the efficiency of accessing storage-related stems by ensuring they are cached
   * in advance, thereby facilitating faster state root computation.
   *
   * @param account the address of the account associated with the storage slot
   * @param slotKey the key of the storage slot for which stems are to be preloaded
   */
  public void preLoadStorageSlot(final Address account, final StorageSlotKey slotKey) {
    CompletableFuture.runAsync(() -> cacheSlotStems(account, slotKey));
  }

  /**
   * Asynchronously preloads stems for the code associated with an account.
   *
   * @param account the address of the account associated with the code
   * @param code the smart contract code for which stems are to be preloaded
   */
  public void preLoadCode(final Address account, final Bytes code) {
    CompletableFuture.runAsync(() -> cacheCodeStems(account, code));
  }

  /**
   * Generates a comprehensive set of stems for an account, including stems for account keys,
   * storage slot keys, and code chunk keys.
   *
   * @param address the address for which stems are to be generated
   * @param accountKeyIds a list of trie keys associated with the account
   * @param storageKeyIds a list of trie keys associated with the account's storage slots
   * @param codeChunkIds a list of trie keys associated with the account's code chunks
   * @param checkCacheBeforeGeneration flag indicating whether to check the cache before generating
   *     new stems
   * @return a map of trie key index to their corresponding stems
   */
  public Map<Bytes32, Bytes> generateManyStems(
      final Address address,
      final List<Bytes32> accountKeyIds,
      final List<Bytes32> storageKeyIds,
      final List<Bytes32> codeChunkIds,
      final boolean checkCacheBeforeGeneration) {

    final Set<Bytes32> trieIndexes = new HashSet<>();

    if (!accountKeyIds.isEmpty()) {
      trieIndexes.add(UInt256.ZERO);
    }
    for (Bytes32 storageKey : storageKeyIds) {
      trieIndexes.add(trieKeyAdapter.getStorageKeyTrieIndex(storageKey));
    }
    for (Bytes32 codeChunkId : codeChunkIds) {
      trieIndexes.add(trieKeyAdapter.getCodeChunkKeyTrieIndex(codeChunkId));
    }

    if (checkCacheBeforeGeneration) {

      // not regenerate stem already preloaded
      final Map<Bytes32, Bytes> stemCached = getCachedStemForAccount(address);
      stemCached.forEach(
          (key, __) -> {
            trieIndexes.remove(key);
            cachedStemCounter++;
          });
      missedStemCounter += trieIndexes.size();
      if (trieIndexes.isEmpty()) {
        return stemCached;
      }

      final Map<Bytes32, Bytes> stems = hasher.manyStems(address, new ArrayList<>(trieIndexes));
      stems.putAll(stemCached);

      return stems;

    } else {
      return hasher.manyStems(address, new ArrayList<>(trieIndexes));
    }
  }

  /**
   * Retrieves the cache that maps account addresses to their corresponding cached stems.
   *
   * @return the cache mapping account addresses to trie stems
   */
  @VisibleForTesting
  public Cache<Address, Map<Bytes32, Bytes>> getStemByAddressCache() {
    return stemByAddressCache;
  }

  /**
   * Retrieves the current number of stems that were not found in the cache and had to be generated.
   * This counter can be used to monitor the effectiveness of the stem preloading mechanism.
   *
   * @return the number of stems that were missed and subsequently generated
   */
  public long getNbMissedStems() {
    return missedStemCounter;
  }

  /**
   * Retrieves the current number of stems that were found in the cache and had not to be generated.
   * This counter can be used to monitor the effectiveness of the stem preloading mechanism.
   *
   * @return the number of stems that were present in the cache
   */
  public long getNbCachedStems() {
    return cachedStemCounter;
  }

  /**
   * Resets the counter of missed trie stems. This method can be used to clear the count of trie
   * stems that were not found in the cache and had to be generated, typically used in conjunction
   * with performance monitoring or testing.
   */
  public void reset() {
    cachedStemCounter = 0;
    missedStemCounter = 0;
  }

  /**
   * Caches stems for all account-related keys for a given account address.
   *
   * @param account the address of the account for which stems are to be cached
   */
  @VisibleForTesting
  public void cacheAccountStems(final Address account) {
    try {
      final Map<Bytes32, Bytes> cache = stemByAddressCache.get(account, ConcurrentHashMap::new);
      cache.putAll(
          generateManyStems(
              account,
              generateAccountKeyIds(),
              Collections.emptyList(),
              Collections.emptyList(),
              false));
    } catch (ExecutionException e) {
      // no op
    }
  }

  /**
   * Caches stems for a specific storage slot key associated with an address.
   *
   * @param account the address of the account associated with the storage slot
   * @param slotKey the storage slot key for which trie stems are to be cached
   */
  @VisibleForTesting
  public void cacheSlotStems(final Address account, final StorageSlotKey slotKey) {
    try {
      final Map<Bytes32, Bytes> cache = stemByAddressCache.get(account, ConcurrentHashMap::new);
      if (slotKey.getSlotKey().isPresent()) {
        cache.putAll(
            generateManyStems(
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

  /**
   * Caches stems for the code associated with an account address.
   *
   * @param account the address of the account associated with the code
   * @param code the smart contract code for which trie stems are to be cached
   */
  @VisibleForTesting
  public void cacheCodeStems(final Address account, final Bytes code) {
    try {
      final Map<Bytes32, Bytes> cache = stemByAddressCache.get(account, ConcurrentHashMap::new);
      cache.putAll(
          generateManyStems(
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

  private Map<Bytes32, Bytes> getCachedStemForAccount(final Address address) {
    return Optional.ofNullable(stemByAddressCache.getIfPresent(address))
        .orElseGet(ConcurrentHashMap::new);
  }

  private boolean codeIsEmpty(final Bytes value) {
    return value == null || value.isEmpty();
  }

  /**
   * Represents the context for a hasher, including the hasher instance and flags indicating the
   * presence of storage trie keys and code trie keys. This record is used to encapsulate the state
   * and configuration of the hasher in relation to preloaded trie stems.
   *
   * @param hasher the hasher instance used for generating stems
   * @param hasStorageTrieKeys flag indicating whether storage trie keys are present in the cache
   * @param hasCodeTrieKeys flag indicating whether code trie keys are present in the cache
   */
  public record HasherContext(Hasher hasher, boolean hasStorageTrieKeys, boolean hasCodeTrieKeys) {}
}
