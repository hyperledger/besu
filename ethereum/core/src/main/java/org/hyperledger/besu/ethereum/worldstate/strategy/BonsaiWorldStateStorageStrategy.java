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
 *
 */
package org.hyperledger.besu.ethereum.worldstate.strategy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface BonsaiWorldStateStorageStrategy extends WorldStateStorageStrategy {

  boolean isWorldStateAvailable(Bytes32 rootHash, Hash blockHash);

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

  FlatDbMode getFlatDbMode();

  /**
   * Streams flat accounts within a specified range.
   *
   * @param startKeyHash The start key hash of the range.
   * @param endKeyHash The end key hash of the range.
   * @param max The maximum number of entries to stream.
   * @return A map of flat accounts. (Empty map in this default implementation)
   */
  default Map<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return Collections.emptyMap();
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
  default Map<Bytes32, Bytes> streamFlatStorages(
      final Hash accountHash, final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return Collections.emptyMap();
  }

  @Override
  void clear();

  void clearTrieLog();

  void clearFlatDatabase();

  void upgradeToFullFlatDbMode();

  interface Updater extends WorldStateStorageStrategy.Updater {

    WorldStateStorageStrategy.Updater putCode(Hash accountHash, Bytes32 codeHash, Bytes code);

    WorldStateStorageStrategy.Updater saveWorldState(Bytes blockHash, Bytes32 nodeHash, Bytes node);

    WorldStateStorageStrategy.Updater putAccountStateTrieNode(
        Bytes location, Bytes32 nodeHash, Bytes node);

    WorldStateStorageStrategy.Updater removeAccountStateTrieNode(Bytes location);

    WorldStateStorageStrategy.Updater putAccountStorageTrieNode(
        Hash accountHash, Bytes location, Bytes32 nodeHash, Bytes node);

    @Override
    void commit();

    void rollback();
  }
}
