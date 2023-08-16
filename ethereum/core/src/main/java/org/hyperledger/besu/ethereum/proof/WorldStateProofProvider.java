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
 */
package org.hyperledger.besu.ethereum.proof;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager.InnerNode;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.Proof;
import org.hyperledger.besu.ethereum.trie.patricia.RemoveVisitor;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

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

/**
 * The WorldStateProofProvider class is responsible for providing proofs for world state entries. It
 * interacts with the underlying storage and trie data structures to generate proofs.
 */
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
      final Hash accountHash = accountAddress.addressHash();
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
    final MerkleTrie<Bytes32, Bytes> storageTrie =
        newAccountStorageTrie(accountHash, account.getStorageRoot());
    final NavigableMap<UInt256, Proof<Bytes>> storageProofs = new TreeMap<>();
    accountStorageKeys.forEach(
        key -> storageProofs.put(key, storageTrie.getValueWithProof(Hash.hash(key))));
    return storageProofs;
  }

  /**
   * Retrieves the proof-related nodes for an account in the specified world state.
   *
   * @param worldStateRoot The root hash of the world state.
   * @param accountHash The hash of the account.
   * @return A list of proof-related nodes for the account.
   */
  public List<Bytes> getAccountProofRelatedNodes(
      final Hash worldStateRoot, final Bytes32 accountHash) {
    final Proof<Bytes> accountProof =
        newAccountStateTrie(worldStateRoot).getValueWithProof(accountHash);
    return accountProof.getProofRelatedNodes();
  }

  /**
   * Retrieves the proof-related nodes for a storage slot in the specified account storage trie.
   *
   * @param storageRoot The root hash of the account storage trie.
   * @param accountHash The hash of the account.
   * @param slotHash The hash of the storage slot.
   * @return A list of proof-related nodes for the storage slot.
   */
  public List<Bytes> getStorageProofRelatedNodes(
      final Bytes32 storageRoot, final Bytes32 accountHash, final Bytes32 slotHash) {
    final Proof<Bytes> storageProof =
        newAccountStorageTrie(Hash.wrap(accountHash), storageRoot).getValueWithProof(slotHash);
    return storageProof.getProofRelatedNodes();
  }

  private MerkleTrie<Bytes, Bytes> newAccountStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, rootHash, b -> b, b -> b);
  }

  private MerkleTrie<Bytes32, Bytes> newAccountStorageTrie(
      final Hash accountHash, final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
        rootHash,
        b -> b,
        b -> b);
  }

  /**
   * Checks if a range proof is valid for a given range of keys.
   *
   * @param startKeyHash The hash of the starting key in the range.
   * @param endKeyHash The hash of the ending key in the range.
   * @param rootHash The root hash of the Merkle Trie.
   * @param proofs The list of proofs for the keys in the range.
   * @param keys The TreeMap of key-value pairs representing the range.
   * @return {@code true} if the range proof is valid, {@code false} otherwise.
   */
  public boolean isValidRangeProof(
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final Bytes32 rootHash,
      final List<Bytes> proofs,
      final SortedMap<Bytes32, Bytes> keys) {

    // check if it's monotonic increasing
    if (!Ordering.natural().isOrdered(keys.keySet())) {
      return false;
    }

    // when proof is empty we need to have all the keys to reconstruct the trie
    if (proofs.isEmpty()) {
      final MerkleTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(Function.identity());
      // add the received keys in the trie
      for (Map.Entry<Bytes32, Bytes> key : keys.entrySet()) {
        trie.put(key.getKey(), key.getValue());
      }

      return rootHash.equals(trie.getRootHash());
    }

    // reconstruct a part of the trie with the proof
    final Map<Bytes32, Bytes> proofsEntries = new HashMap<>();
    for (Bytes proof : proofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }

    if (keys.isEmpty()) {
      final MerkleTrie<Bytes, Bytes> trie =
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
        // check if there is not missing element
        // a missing node will throw an exception while it is loading
        // @see org.hyperledger.besu.ethereum.trie.StoredNode#load()
        trie.entriesFrom(startKeyHash, Integer.MAX_VALUE);
      } catch (MerkleTrieException e) {
        return false;
      }
      return true;
    }

    // search inner nodes in the range created by the proofs and remove
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            startKeyHash,
            keys.lastKey(),
            true);
    final MerkleTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, rootHash);
    // filling out innerNodes of the InnerNodeDiscoveryManager by walking through the trie
    trie.visitAll(node -> {});
    final List<InnerNode> innerNodes = snapStoredNodeFactory.getInnerNodes();
    for (InnerNode innerNode : innerNodes) {
      trie.removePath(
          Bytes.concatenate(innerNode.location(), innerNode.path()), new RemoveVisitor<>(false));
    }

    // add the received keys in the trie to reconstruct the trie
    for (Map.Entry<Bytes32, Bytes> account : keys.entrySet()) {
      trie.put(account.getKey(), account.getValue());
    }

    // check if the generated root hash is valid
    return rootHash.equals(trie.getRootHash());
  }
}
