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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** Acts as both a Hasher and PreImageStorage for Bonsai storage format. */
public interface CachingPreImageStorage {

  enum HashSource {
    ACCOUNT_ADDRESS((byte) 0x00),
    SLOT_KEY((byte) 0x01);
    byte internal_type;

    HashSource(final byte type) {
      internal_type = type;
    }

    byte getTypeSuffix() {
      return internal_type;
    }

    static HashSource wrap(final byte val) {
      if (val == 0x00) {
        return ACCOUNT_ADDRESS;
      } else if (val == 0x01) {
        return SLOT_KEY;
      } else {
        throw new NoSuchElementException(String.format("Value %x is not a preimage source", val));
      }
    }
  }

  record HashKey(Hash hashValue, HashSource source) {}

  /**
   * If this value is not already present, save in preImage store and return the hash value.
   *
   * @param value value to hash
   * @return Hash of value
   */
  Hash hashAndSaveAddressPreImage(final Bytes value);

  Hash hashAndSaveSlotKeyPreImage(final UInt256 keyUInt);

  Stream<Address> streamAddressPreImages(final Bytes32 startKeyHash, final int limit);

  Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey);

  Optional<Address> getAccountTrieKeyPreimage(final Bytes32 trieKey);

  Optional<Bytes> getRawHashPreImage(final Hash hash);

  /**
   * This method indicates whether this Pre-Image store is "complete", meaning it has all of the
   * hash preimages for all entries in the state trie.
   *
   * @return boolean indicating whether the pre-image store is complete or not
   */
  default boolean canSupportStreaming() {
    return false;
  }

  /**
   * A caching PreImageProxy suitable for ReferenceTestWorldState which saves hashes in an unbounded
   * BiMap.
   */
  class UnboundedPreImageStorage implements CachingPreImageStorage {
    BiMap<HashKey, Bytes> preImageCache = HashBiMap.create();

    @Override
    public Hash hashAndSaveAddressPreImage(final Bytes value) {
      return preImageCache
          .inverse()
          .computeIfAbsent(
              value, v -> new HashKey(Address.wrap(v).addressHash(), HashSource.ACCOUNT_ADDRESS))
          .hashValue();
    }

    @Override
    public Hash hashAndSaveSlotKeyPreImage(final UInt256 value) {
      return preImageCache
          .inverse()
          .computeIfAbsent(value, v -> new HashKey(Hash.hash(v), HashSource.SLOT_KEY))
          .hashValue();
    }

    @Override
    public Stream<Address> streamAddressPreImages(final Bytes32 startKeyHash, final int limit) {
      final Hash startHash = Hash.wrap(startKeyHash);
      return preImageCache.entrySet().stream()
          .filter(entry -> entry.getKey().source() == HashSource.ACCOUNT_ADDRESS)
          .filter(entry -> entry.getKey().hashValue().compareTo(startHash) >= 0)
          .map(e -> Address.wrap(e.getValue()))
          .limit(limit);
    }

    @Override
    public Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.ofNullable(
              preImageCache.get(new HashKey(Hash.wrap(trieKey), HashSource.SLOT_KEY)))
          .map(UInt256::fromBytes);
    }

    @Override
    public Optional<Address> getAccountTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.ofNullable(
              preImageCache.get(new HashKey(Hash.wrap(trieKey), HashSource.ACCOUNT_ADDRESS)))
          .map(Address::wrap);
    }

    @Override
    public Optional<Bytes> getRawHashPreImage(final Hash hash) {
      return Stream.of(
              preImageCache.get(new HashKey(hash, HashSource.ACCOUNT_ADDRESS)),
              preImageCache.get(new HashKey(hash, HashSource.SLOT_KEY)))
          .filter(Objects::nonNull)
          .findAny();
    }

    @Override
    public boolean canSupportStreaming() {
      return true;
    }
  }

  class CachingOnlyPreImageStorage implements CachingPreImageStorage {

    // TODO: config max size perhaps
    private static final Cache<Bytes, Hash> preimageCache =
        Caffeine.newBuilder().maximumSize(10000).build();

    @Override
    public Hash hashAndSaveAddressPreImage(final Bytes value) {
      // defer to the static Address hash map used by the evm
      return preimageCache.get(value, v -> Address.wrap(value).addressHash());
    }

    @Override
    public Hash hashAndSaveSlotKeyPreImage(final UInt256 keyUInt) {
      return preimageCache.get(keyUInt, Hash::hash);
    }

    @Override
    public Stream<Address> streamAddressPreImages(final Bytes32 startKeyHash, final int limit) {
      // not configured for preimage streaming
      return Stream.empty();
    }

    @Override
    public Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
      return getRawHashPreImage(Hash.wrap(trieKey)).map(UInt256::fromBytes);
    }

    @Override
    public Optional<Address> getAccountTrieKeyPreimage(final Bytes32 trieKey) {
      return getRawHashPreImage(Hash.wrap(trieKey)).map(Address::wrap);
    }

    @Override
    public Optional<Bytes> getRawHashPreImage(final Hash hash) {
      return preimageCache.asMap().entrySet().stream()
          .filter(e -> e.getValue().equals(hash))
          .findFirst()
          .map(Map.Entry::getKey);
    }
  }
}
