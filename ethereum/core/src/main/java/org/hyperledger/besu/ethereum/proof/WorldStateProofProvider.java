/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.proof;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.RemoveVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import com.google.common.collect.Ordering;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class WorldStateProofProvider {

  private final WorldStateStorage worldStateStorage;

  public WorldStateProofProvider(final WorldStateStorage worldStateStorage) {
    this.worldStateStorage = worldStateStorage;
  }

  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {

    if (!worldStateStorage.isWorldStateAvailable(worldStateRoot, null)) {
      return Optional.empty();
    } else {
      final Hash accountHash = Hash.hash(accountAddress);
      final Proof<Bytes> accountProof =
          newAccountStateTrie(worldStateRoot).getValueWithProof(accountHash);

      return accountProof
          .getValue()
          .map(RLP::input)
          .map(StateTrieAccountValue::readFrom)
          .map(
              account -> {
                final SortedMap<UInt256, Proof<Bytes>> storageProofs =
                    getStorageProofs(accountHash, account, accountStorageKeys);
                return new WorldStateProof(account, accountProof, storageProofs);
              });
    }
  }

  private SortedMap<UInt256, Proof<Bytes>> getStorageProofs(
      final Hash accountHash,
      final StateTrieAccountValue account,
      final List<UInt256> accountStorageKeys) {
    final MerklePatriciaTrie<Bytes32, Bytes> storageTrie =
        newAccountStorageTrie(accountHash, account.getStorageRoot());
    final NavigableMap<UInt256, Proof<Bytes>> storageProofs = new TreeMap<>();
    accountStorageKeys.forEach(
        key -> storageProofs.put(key, storageTrie.getValueWithProof(Hash.hash(key))));
    return storageProofs;
  }

  private MerklePatriciaTrie<Bytes32, Bytes> newAccountStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, rootHash, b -> b, b -> b);
  }

  private MerklePatriciaTrie<Bytes32, Bytes> newAccountStorageTrie(
      final Hash accountHash, final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
        rootHash,
        b -> b,
        b -> b);
  }

  public boolean isValidRangeProof(
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Bytes32 rootHash,
      final Optional<List<Bytes>> proofs,
      final TreeMap<Bytes32, Bytes> keys) {

    // check if it's monotonic increasing
    if (!Ordering.natural().isOrdered(keys.keySet())) {
      return false;
    }

    // when proof is empty we need to have all of the keys to reconstruct the trie
    if (proofs.isEmpty()) {
      final Map<Bytes32, Bytes> db = new HashMap<>();
      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(
              new StoredNodeFactory<>(
                  (location, hash) -> Optional.ofNullable(db.get(hash)),
                  Function.identity(),
                  Function.identity()),
              Hash.hash(RLP.NULL));
      // add the received keys in the trie
      for (Map.Entry<Bytes32, Bytes> key : keys.entrySet()) {
        trie.put(key.getKey(), key.getValue());
      }
      return rootHash.equals(trie.getRootHash());
    }

    // reconstruct a part of the trie with the proof
    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : proofs.orElseThrow()) {
      proofsEntries.put(Hash.hash(proof), proof);
    }

    if (keys.isEmpty()) {
      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(
              new InnerNodeDiscoveryManager<>(
                  (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
                  Function.identity(),
                  Function.identity(),
                  startKeyHash,
                  endKeyHash,
                  false),
              rootHash);
      try {
        // check is there is not missing element
        trie.entriesFrom(startKeyHash, Integer.MAX_VALUE);
      } catch (MerkleTrieException e) {
        return false;
      }
      return true;
    }

    // search inner nodes in the range create by the proofs and remove
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            startKeyHash,
            keys.lastKey(),
            true);
    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, rootHash);
    trie.visitAll(node -> {});
    final Bytes[] innerNodes = snapStoredNodeFactory.getInnerNodes().toArray(new Bytes[0]);
    for (Bytes innerNode : innerNodes) {
      trie.removePath(innerNode, new RemoveVisitor<>(false));
    }

    assert !rootHash.equals(trie.getRootHash());

    // add the received keys in the trie to reconstruct the trie
    for (Map.Entry<Bytes32, Bytes> account : keys.entrySet()) {
      trie.put(account.getKey(), account.getValue());
    }
    // check if the generated root hash is valid
    return rootHash.equals(trie.getRootHash());
  }
}
