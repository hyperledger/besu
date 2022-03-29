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
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface WorldStateStorage {

  Optional<Bytes> getCode(Bytes32 codeHash, Hash accountHash);

  Optional<Bytes> getAccountTrieNodeData(Bytes location, Bytes32 hash);

  Optional<Bytes> getAccountStateTrieNode(Bytes location, Bytes32 nodeHash);

  Optional<Bytes> getAccountStorageTrieNode(Hash accountHash, Bytes location, Bytes32 nodeHash);

  Optional<Bytes> getNodeData(Bytes location, Bytes32 hash);

  boolean isWorldStateAvailable(Bytes32 rootHash, Hash blockHash);

  default boolean contains(final Bytes32 hash) {
    // we don't have location info
    return getNodeData(null, hash).isPresent();
  }

  void clear();

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
