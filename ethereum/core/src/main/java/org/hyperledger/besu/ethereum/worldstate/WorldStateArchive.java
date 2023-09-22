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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface WorldStateArchive extends Closeable {
  Hash EMPTY_ROOT_HASH = Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH);

  Optional<WorldState> get(Hash rootHash, Hash blockHash);

  boolean isWorldStateAvailable(Hash rootHash, Hash blockHash);

  Optional<MutableWorldState> getMutable(BlockHeader blockHeader, boolean isPersistingState);

  Optional<MutableWorldState> getMutable(Hash rootHash, Hash blockHash);

  MutableWorldState getMutable();

  /**
   * Resetting the archive cache and adding the new pivot as the only entry
   *
   * @param blockHeader new pivot block header
   */
  void resetArchiveStateTo(BlockHeader blockHeader);

  Optional<Bytes> getNodeData(Hash hash);

  /**
   * Retrieves an account proof based on the provided parameters.
   *
   * @param blockHeader The header of the block for which to retrieve the account proof.
   * @param accountAddress The address of the account for which to retrieve the proof.
   * @param accountStorageKeys The storage keys of the account for which to retrieve the proof.
   * @param mapper A function to map the retrieved WorldStateProof to a desired type.
   * @return An Optional containing the mapped result if the account proof is successfully retrieved
   *     and mapped, or an empty Optional otherwise.
   */
  <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper);
}
