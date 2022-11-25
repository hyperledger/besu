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
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;

public class OptimizedMerkleTrieLoader {

  private final Cache<Bytes, Bytes> foundNodes =
      CacheBuilder.newBuilder().maximumSize(100_000).build();

    public void preLoadAccount(final BonsaiWorldStateKeyValueStorage worldStateStorage, final Hash worldStateRootHash, final Address account) {
    CompletableFuture.runAsync(
            () -> {
                final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
                        new StoredMerklePatriciaTrie<>(
                                (location, hash) -> {
                                    Optional<Bytes> node = worldStateStorage.getAccountStateTrieNode(location, hash);
                                    node.ifPresent(bytes -> foundNodes.put(Hash.hash(bytes),bytes));
                                    return node;
                                },
                                worldStateRootHash,
                                Function.identity(),
                                Function.identity());
                accountTrie.get(Hash.hash(account));
            });
  }

  public Optional<Bytes> getAccountStateTrieNode(final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      Optional<Bytes> node = Optional.ofNullable(foundNodes.getIfPresent(nodeHash));
      if(node.isPresent()){
        return node;
      } else {
        return worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash);
      }
    }
  }


}
