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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.CachedPedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
 * <p>- Account-related keys (e.g., version, balance, nonce, and code hash keys) - Storage slot keys
 * for a given account - Code chunk keys for smart contract code associated with an account
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

  // Cache of stem by address. The sub-map will contain the trie index as the key and the stem as
  // the value.
  private final Cache<Address, CachedPedersenHasher> pedersenHasherCache =
      CacheBuilder.newBuilder().maximumSize(STEM_CACHE_SIZE).build();

  private final TrieKeyAdapter trieKeyAdapter;

  public StemPreloader() {
    this.trieKeyAdapter = new TrieKeyAdapter(new PedersenHasher());
  }

  /**
   * Creates a preloaded hasher context for a given updated address, storage, and code. This method
   * preloads stems for account keys, storage slot keys, and code chunk keys based on the provided
   * updates. It then returns the preloaded stems
   *
   * <p>This method is particularly useful for optimizing the hashing process during state root
   * computation by ensuring that stems are preloaded and readily available, thereby reducing the
   * need for on-the-fly stem generation.
   *
   * @param address the address for which to preload trie stems
   * @param keys a list of keys to use for stem generation
   * @return the preloaded stems
   */
  public Map<Bytes32, Bytes> preloadStems(final Address address, final Set<Bytes32> keys) {
    return getHasherByAddress(address).manyStems(address, new ArrayList<>(keys));
  }

  public Bytes preloadAccountStem(final Address address) {
    return getHasherByAddress(address).computeStem(address, UInt256.ZERO);
  }

  public Bytes preloadSlotStems(final Address address, final StorageSlotKey storageSlotKey) {
    return getHasherByAddress(address)
        .computeStem(
            address,
            trieKeyAdapter.getStorageKeyTrieIndex(storageSlotKey.getSlotKey().orElseThrow()));
  }

  public Map<Bytes32, Bytes> preloadCodeChunckStems(final Address address, final Bytes codeUpdate) {
    return getHasherByAddress(address).manyStems(address, generateCodeChunkKeyIds(codeUpdate));
  }

  /**
   * Retrieves the cache that maps account addresses to their corresponding cached stems.
   *
   * @return the cache mapping account addresses to trie stems
   */
  @VisibleForTesting
  public CachedPedersenHasher getHasherByAddress(final Address address) {
    CachedPedersenHasher ifPresent = pedersenHasherCache.getIfPresent(address);
    if (ifPresent != null) {
      return ifPresent;
    }
    final CachedPedersenHasher defaultHasher =
        new CachedPedersenHasher(STEM_CACHE_SIZE, new ConcurrentHashMap<>());
    pedersenHasherCache.put(address, defaultHasher);
    return defaultHasher;
  }

  public Bytes getStorageKeySuffix(final Bytes32 storageKey) {
    return trieKeyAdapter.getStorageKeySuffix(storageKey);
  }

  /**
   * Resets the counter of missed trie stems. This method can be used to clear the count of trie
   * stems that were not found in the cache and had to be generated, typically used in conjunction
   * with performance monitoring or testing.
   */
  public void reset() {
    pedersenHasherCache.invalidateAll();
  }

  public Bytes32 generateAccountKeyId() {
    return Parameters.BASIC_DATA_LEAF_KEY;
  }

  public List<Bytes32> generateCodeChunkKeyIds(final Bytes code) {
    return IntStream.range(0, trieKeyAdapter.getNbChunk(code))
        .mapToObj(UInt256::valueOf)
        .collect(Collectors.toUnmodifiableList());
  }

  public List<Bytes32> generateStorageKeyIds(final Set<StorageSlotKey> storageSlotKeys) {
    return storageSlotKeys.stream()
        .map(
            storageSlotKey ->
                trieKeyAdapter.getStorageKeyTrieIndex(storageSlotKey.getSlotKey().orElseThrow()))
        .map(Bytes32::wrap)
        .toList();
  }
}
