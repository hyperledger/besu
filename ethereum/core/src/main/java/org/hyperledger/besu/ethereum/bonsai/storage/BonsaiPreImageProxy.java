/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.storage;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;

import java.util.Optional;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** Acts as both a Hasher and PreImageStorage for Bonsai storage format. */
public interface BonsaiPreImageProxy extends WorldStatePreimageStorage {
  /**
   * If this value is not already present, save in preImage store and return the hash value.
   *
   * @param value value to hash
   * @return Hash of value
   */
  Hash hashAndSavePreImage(Bytes value);

  /** PreImageProxy which does not store or cache preImages and only implements hashing. */
  class NoOpPreImageProxy implements BonsaiPreImageProxy {

    @Override
    public Hash hashAndSavePreImage(final Bytes value) {
      return Hash.hash(value);
    }

    @Override
    public Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.empty();
    }

    @Override
    public Optional<Address> getAccountTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.empty();
    }

    @Override
    public Updater updater() {
      throw new UnsupportedOperationException("NoOpPreImageProxy does not implement an updater");
    }
  }

  /**
   * A caching PreImageProxy suitable for ReferenceTestWorldState which saves hashes in an unbounded
   * BiMap.
   */
  class BonsaiReferenceTestPreImageProxy extends NoOpPreImageProxy {
    BiMap<Hash, Bytes> preImageCache = HashBiMap.create();

    @Override
    public Hash hashAndSavePreImage(final Bytes value) {
      return preImageCache.inverse().computeIfAbsent(value, Hash::hash);
    }

    @Override
    public Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.ofNullable(preImageCache.get(trieKey)).map(UInt256::fromBytes);
    }

    @Override
    public Optional<Address> getAccountTrieKeyPreimage(final Bytes32 trieKey) {
      return Optional.ofNullable(preImageCache.get(trieKey)).map(Address::wrap);
    }
  }
}
