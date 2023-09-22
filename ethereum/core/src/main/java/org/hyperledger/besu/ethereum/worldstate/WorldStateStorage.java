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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface WorldStateStorage {

  Optional<Bytes> getCode(Bytes32 codeHash, Hash accountHash);

  Optional<Bytes> getAccountStateTrieNode(Bytes location, Bytes32 nodeHash);

  Optional<Bytes> getAccountStorageTrieNode(Hash accountHash, Bytes location, Bytes32 nodeHash);

  /**
   * This method allows obtaining a TrieNode in an unsafe manner, without verifying the consistency
   * of the obtained node. Checks such as node hash verification are not performed here.
   *
   * @param key of the trie node
   * @return value of the trie node
   */
  Optional<Bytes> getTrieNodeUnsafe(Bytes key);

  Optional<Bytes> getNodeData(Bytes location, Bytes32 hash);

  FlatDbMode getFlatDbMode();

  boolean isWorldStateAvailable(Bytes32 rootHash, Hash blockHash);

  default boolean contains(final Bytes32 hash) {
    // we don't have location info
    return getNodeData(null, hash).isPresent();
  }

  /**
   * Streams flat accounts within a specified range.
   *
   * @param startKeyHash The start key hash of the range.
   * @param endKeyHash The end key hash of the range.
   * @param max The maximum number of entries to stream.
   * @return A map of flat accounts. (Empty map in this default implementation)
   */
  default NavigableMap<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return new TreeMap<>();
  }

  /**
   * Streams flat accounts within a specified range.
   *
   * @param startKeyHash The start key hash of the range.
   * @param endKeyHash The end key hash of the range.
   * @param takeWhile Function to limit the number of entries to stream.
   * @return A map of flat accounts. (Empty map in this default implementation)
   */
  default NavigableMap<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final Predicate<Pair<Bytes32, Bytes>> takeWhile) {
    return new TreeMap<>();
  }

  /**
   * Streams flat storages within a specified range.
   *
   * @param accountHash The account hash.
   * @param startKeyHash The start key hash of the range.
   * @param endKeyHash The end key hash of the range.
   * @param max The maximum number of entries to stream.
   * @return A map of flat storages. (Empty map in this default implementation)
   */
  default NavigableMap<Bytes32, Bytes> streamFlatStorages(
      final Hash accountHash, final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return new TreeMap<>();
  }

  DataStorageFormat getDataStorageFormat();

  void clear();

  void clearTrieLog();

  void clearFlatDatabase();

  Updater updater();

  long prune(Predicate<byte[]> inUseCheck);

  long addNodeAddedListener(NodesAddedListener listener);

  void removeNodeAddedListener(long id);

  interface Updater {

    Updater putCode(Hash accountHash, Bytes32 nodeHash, Bytes code);

    default Updater putCode(final Hash accountHash, final Bytes code) {
      // Skip the hash calculation for empty code
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(accountHash, codeHash, code);
    }

    Updater saveWorldState(Bytes blockHash, Bytes32 nodeHash, Bytes node);

    Updater putAccountStateTrieNode(Bytes location, Bytes32 nodeHash, Bytes node);

    Updater removeAccountStateTrieNode(Bytes location, Bytes32 nodeHash);

    Updater putAccountStorageTrieNode(
        Hash accountHash, Bytes location, Bytes32 nodeHash, Bytes node);

    void commit();

    void rollback();
  }

  interface NodesAddedListener {
    void onNodesAdded(Collection<Bytes32> nodeHash);
  }
}
