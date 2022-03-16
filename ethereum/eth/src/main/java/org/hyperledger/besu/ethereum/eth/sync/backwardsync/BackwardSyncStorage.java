/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.List;
import java.util.Optional;

public interface BackwardSyncStorage {
  /**
   * The Ancestors list should keep a pointer to the first one in it and return it when necessary
   */
  Optional<BlockHeader> getFirstAncestorHeader();
  /** Returns at most first N nodes from the Ancestors list */
  List<BlockHeader> getFirstNAncestorHeaders(int size);
  /** Returns all ancestors */
  List<BlockHeader> getAllAncestors();

  /** Adds a known header to the begining of the ancestors chain */
  void prependAncestorsHeader(BlockHeader blockHeader);

  /**
   * prepends another chain before the current chain. The historical chain has to end just at the
   * point where the current chain begins
   */
  void prependChain(BackwardSyncStorage historicalBackwardChain);

  /** This is the last known block of the chain */
  Block getPivot();

  /** removes the first ancesstor header from the chain */
  void dropFirstHeader();

  /** appends a block to the end of the chain, this block becomes a new pivot block */
  void appendExpectedBlock(Block newPivot);

  /**
   * successors we store fully, this method retrieves all of them so that they can be imported into
   * the blockchain at once
   */
  List<Block> getSuccessors();

  /**
   * As consensus layer gives as blocks we might be able to trust them even when not imported in the
   * blockchain
   */
  boolean isTrusted(Hash hash);

  /** Returns a trusted block for a hash */
  Block getTrustedBlock(Hash hash);

  void clear();

  void commit();

  Optional<BlockHeader> getHeaderOnHeight(long height);
}
