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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache.preloader;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.StemHasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.builder.StemHasherBuilder;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.cache.InMemoryCacheStrategy;

import java.util.List;
import java.util.Map;

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
 * @see org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyFactory
 * @see org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher
 */
public class StemPreloader {
  private static final int HASHER_CACHE_SIZE = 10_000;
  private static final int STEM_CACHE_SIZE = 10_000;
  private static final int ADDRESS_COMMITMENT_CACHE_SIZE = 10_000;

  private final Cache<Address, StemHasher> stemHasherByAddress =
      CacheBuilder.newBuilder().maximumSize(HASHER_CACHE_SIZE).build();

  public StemPreloader() {}

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
  public Map<Bytes32, Bytes> preloadStems(final Address address, final List<Bytes32> keys) {
    return getStemHasherByAddress(address).manyStems(address, keys);
  }

  public Bytes preloadAccountStem(final Address address) {
    return getStemHasherByAddress(address).computeStem(address, UInt256.ZERO);
  }

  public Bytes preloadSlotStems(final Address address, final StorageSlotKey storageSlotKey) {
    final Bytes32 storageKeyTrieIndex =
        TrieKeyUtils.getStorageKeyTrieIndex(storageSlotKey.getSlotKey().orElseThrow());
    return getStemHasherByAddress(address).computeStem(address, storageKeyTrieIndex);
  }

  public Map<Bytes32, Bytes> preloadCodeChunckStems(final Address address, final Bytes codeUpdate) {
    return getStemHasherByAddress(address)
        .manyStems(address, TrieKeyUtils.getCodeChunkKeyTrieIndexes(codeUpdate));
  }

  /**
   * Retrieves the cache that maps account addresses to their corresponding stem hasher.
   *
   * @return the stem hasher linked to the address
   */
  @VisibleForTesting
  public StemHasher getStemHasherByAddress(final Address address) {
    StemHasher ifPresent = stemHasherByAddress.getIfPresent(address);
    if (ifPresent != null) {
      return ifPresent;
    }
    final StemHasher stemHasher =
        StemHasherBuilder.builder()
            .withStemCache(new InMemoryCacheStrategy<>(STEM_CACHE_SIZE))
            .withAddressCommitmentCache(new InMemoryCacheStrategy<>(ADDRESS_COMMITMENT_CACHE_SIZE))
            .build();
    stemHasherByAddress.put(address, stemHasher);
    return stemHasher;
  }

  /**
   * Resets the counter of missed trie stems. This method can be used to clear the count of trie
   * stems that were not found in the cache and had to be generated, typically used in conjunction
   * with performance monitoring or testing.
   */
  public void reset() {
    stemHasherByAddress.invalidateAll();
  }
}
