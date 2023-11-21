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

import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface ForestWorldStateStorageStrategy extends WorldStateStorageStrategy {

  boolean isWorldStateAvailable(Bytes32 rootHash);

  Optional<Bytes> getCode(Bytes32 codeHash);

  Optional<Bytes> getAccountStateTrieNode(Bytes32 nodeHash);

  Optional<Bytes> getAccountStorageTrieNode(Bytes32 nodeHash);

  Optional<Bytes> getNodeData(Bytes32 hash);

  default boolean contains(final Bytes32 hash) {
    // we don't have location info
    return getNodeData(hash).isPresent();
  }

  @Override
  void clear();

  long prune(Predicate<byte[]> inUseCheck);

  long addNodeAddedListener(NodesAddedListener listener);

  void removeNodeAddedListener(long id);

  interface Updater extends WorldStateStorageStrategy.Updater {

    WorldStateStorageStrategy.Updater putCode(Bytes32 codeHash, Bytes code);

    default WorldStateStorageStrategy.Updater putCode(final Bytes code) {
      // Skip the hash calculation for empty code
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(codeHash, code);
    }

    WorldStateStorageStrategy.Updater saveWorldState(Bytes32 nodeHash, Bytes node);

    WorldStateStorageStrategy.Updater putAccountStateTrieNode(Bytes32 nodeHash, Bytes node);

    WorldStateStorageStrategy.Updater removeAccountStateTrieNode(Bytes32 nodeHash);

    WorldStateStorageStrategy.Updater putAccountStorageTrieNode(Bytes32 nodeHash, Bytes node);

    @Override
    void commit();

    void rollback();
  }
}
