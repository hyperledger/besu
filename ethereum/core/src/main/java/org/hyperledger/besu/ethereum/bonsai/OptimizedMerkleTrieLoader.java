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

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

public class OptimizedMerkleTrieLoader {

  private final Cache<Bytes, Bytes> accountsNodes =
      CacheBuilder.newBuilder().maximumSize(100_000).build();
    private final Cache<Bytes, Bytes> storageNodes =
            CacheBuilder.newBuilder().maximumSize(200_000).build();

    private final AtomicInteger accountNodeFound  = new AtomicInteger(0);
    private final AtomicInteger storageNodeFound  = new AtomicInteger(0);

    public void preLoadAccount(final BonsaiWorldStateKeyValueStorage worldStateStorage, final Hash worldStateRootHash, final Address account) {
    CompletableFuture.runAsync(
            () -> {
                final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
                        new StoredMerklePatriciaTrie<>(
                                (location, hash) -> {
                                    Optional<Bytes> node = worldStateStorage.getAccountStateTrieNode(location, hash);
                                    node.ifPresent(bytes -> accountsNodes.put(Hash.hash(bytes),bytes));
                                    return node;
                                },
                                worldStateRootHash,
                                Function.identity(),
                                Function.identity());
                accountTrie.get(Hash.hash(account));
            });
  }

    public void preLoadStorage(final BonsaiWorldStateKeyValueStorage worldStateStorage, final Hash worldStateRootHash, final Address account, final Hash slotHash) {
        CompletableFuture.runAsync(
                () -> {
                    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
                            new StoredMerklePatriciaTrie<>(
                                    (location, hash) -> {
                                        Optional<Bytes> node = worldStateStorage.getAccountStateTrieNode(location, hash);
                                        node.ifPresent(bytes -> accountsNodes.put(Hash.hash(bytes),bytes));
                                        return node;
                                    },
                                    worldStateRootHash,
                                    Function.identity(),
                                    Function.identity());
                    accountTrie.get(Hash.hash(account)).ifPresent(value -> {
                        StateTrieAccountValue stateTrieAccountValue = StateTrieAccountValue.readFrom(RLP.input(value));
                        final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
                                new StoredMerklePatriciaTrie<>(
                                        (location, hash) -> {
                                            Optional<Bytes> node = worldStateStorage.getAccountStorageTrieNode(Hash.hash(account), location, hash);
                                            node.ifPresent(bytes -> storageNodes.put(Hash.hash(bytes),bytes));
                                            return node;
                                        },
                                        stateTrieAccountValue.getStorageRoot(),
                                        Function.identity(),
                                        Function.identity());
                        storageTrie.get(Hash.hash(slotHash));
                    });

                });
    }


  public Optional<Bytes> getAccountStateTrieNode(final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      Optional<Bytes> node = Optional.ofNullable(accountsNodes.getIfPresent(nodeHash));
      if(node.isPresent()){
          if(accountNodeFound.incrementAndGet()%1000==0){
              System.out.println("Found "+accountNodeFound.get()+" account nodes with cache size "+accountsNodes.size());
          }
        return node;
      } else {
        return worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash);
      }
    }
  }

    public Optional<Bytes> getAccountStorageTrieNode(
            final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage, final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
        if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
            return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
        } else {
            Optional<Bytes> node = Optional.ofNullable(storageNodes.getIfPresent(nodeHash));
            if(node.isPresent()){
                if(storageNodeFound.incrementAndGet()%1000==0){
                    System.out.println("Found "+storageNodeFound.get()+" storage nodes with cache size "+storageNodes.size());
                }
                return node;
            } else {
                return worldStateKeyValueStorage.getAccountStorageTrieNode(accountHash,location, nodeHash);
            }
        }
    }

}
