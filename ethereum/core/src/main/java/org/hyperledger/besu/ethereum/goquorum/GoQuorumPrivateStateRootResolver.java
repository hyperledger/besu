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
package org.hyperledger.besu.ethereum.goquorum;

import org.hyperledger.besu.ethereum.core.Hash;

import java.util.HashMap;
import java.util.Map;

public class GoQuorumPrivateStateRootResolver {

  // TODO-goquorum we don't want to do this in memory :)
  private static final Map<Hash, Hash> privateStateRootHashMap = new HashMap<>();

  /**
   * Finds the private state root hash corresponding to a particular public state root hash. If a
   * private state root hash isn't found, we return the empty trie hash.
   *
   * @param publicStateRootHash the public state root hash
   * @return the associated private state root hash, or the empty trie hash
   */
  public Hash getFromPublicStateRootHash(final Hash publicStateRootHash) {
    return privateStateRootHashMap.getOrDefault(publicStateRootHash, Hash.EMPTY_TRIE_HASH);
  }

  /** Update the mapping between the public and private state root hash */
  public void updatePrivateStateRootHash(
      final Hash publicStateRootHash, final Hash privateStateRootHash) {
    privateStateRootHashMap.put(publicStateRootHash, privateStateRootHash);
  }
}
