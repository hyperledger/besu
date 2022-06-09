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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public interface WorldStateArchive {
  Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  Optional<WorldState> get(Hash rootHash, Hash blockHash);

  boolean isWorldStateAvailable(Hash rootHash, Hash blockHash);

  @Deprecated
  Optional<MutableWorldState> getMutable(long blockNumber, boolean isPersistingState);

  Optional<MutableWorldState> getMutable(Hash rootHash, Hash blockHash, boolean isPersistingState);

  Optional<MutableWorldState> getMutable(Hash rootHash, Hash blockHash);

  MutableWorldState getMutable();

  void setArchiveStateUnSafe(BlockHeader blockHeader);

  Optional<Bytes> getNodeData(Hash hash);

  Optional<WorldStateProof> getAccountProof(
      Hash worldStateRoot, Address accountAddress, List<UInt256> accountStorageKeys);
}
