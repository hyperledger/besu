/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class OptimizedMerkleTrieLoader {

  private final Cache<Bytes, Optional<Bytes>> foundNodes =
      CacheBuilder.newBuilder().maximumSize(100_000).build();

  private final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage;

  private final Set<Bytes> nodePaths;
  private final Set<Bytes> empty;

  public OptimizedMerkleTrieLoader(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Set<Address> locations) {
    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    this.nodePaths = Collections.synchronizedSet(new HashSet<>());
    this.empty = Collections.synchronizedSet(new HashSet<>());
    load(locations);
  }

  public void load(final Set<Address> locations) {
    locations.parallelStream()
        .map(CompactEncoding::bytesToPath)
        .forEach(
            bytes -> {
              for (int i = 0; i < bytes.size(); i++) {
                nodePaths.add(bytes.slice(0, i));
              }
            });
    nodePaths.parallelStream()
        .forEach(
            key -> {
              if (empty.stream()
                  .noneMatch(emptyKey -> key.commonPrefixLength(emptyKey) == emptyKey.size())) {
                worldStateKeyValueStorage
                    .trieBranchStorage
                    .get(key.toArrayUnsafe())
                    .map(Bytes::wrap)
                    .ifPresentOrElse(
                        node -> foundNodes.put(Hash.hash(node), Optional.of(node)),
                        () -> empty.add(key));
              }
            });
  }

  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      try {
        return foundNodes.get(
            nodeHash, () -> worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash));
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      try {
        return foundNodes.get(
            nodeHash,
            () ->
                worldStateKeyValueStorage.getAccountStorageTrieNode(
                    accountHash, location, nodeHash));
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
