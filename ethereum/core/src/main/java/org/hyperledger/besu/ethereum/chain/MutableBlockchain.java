/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;
import java.util.Optional;

public interface MutableBlockchain extends Blockchain {

  /**
   * Adds a block to the blockchain.
   *
   * <p>Block must be connected to the existing blockchain (its parent must already be stored),
   * otherwise an {@link IllegalArgumentException} is thrown. Blocks representing forks are allowed
   * as long as they are connected.
   *
   * @param block The block to append.
   * @param receipts The list of receipts associated with this block's transactions.
   */
  void appendBlock(Block block, List<TransactionReceipt> receipts);

  /**
   * Adds a block to the blockchain, without updating the chain state.
   *
   * <p>Block must be connected to the existing blockchain (its parent must already be stored),
   * otherwise an {@link IllegalArgumentException} is thrown. Blocks representing forks are allowed
   * as long as they are connected.
   *
   * @param block The block to append.
   * @param receipts The list of receipts associated with this block's transactions.
   */
  void storeBlock(Block block, List<TransactionReceipt> receipts);

  void unsafeImportBlock(
      final Block block,
      final List<TransactionReceipt> receipts,
      final Optional<Difficulty> maybeTotalDifficulty);

  void unsafeSetChainHead(final BlockHeader blockHeader, final Difficulty totalDifficulty);

  Difficulty calculateTotalDifficulty(final BlockHeader blockHeader);

  /**
   * Rolls back the canonical chainhead to the specified block number.
   *
   * @param blockNumber The block number to roll back to.
   * @return {@code true} on success, {@code false} if the canonical chain height is less than
   *     {@code blockNumber}
   */
  boolean rewindToBlock(final long blockNumber);

  /**
   * Rolls back the canonical chainhead to the specified block hash.
   *
   * @param blockHash The block hash to roll back to.
   * @return {@code true} on success, {@code false} if the canonical chain height is less than
   *     {@code blockNumber}
   */
  boolean rewindToBlock(final Hash blockHash);

  /**
   * Forward the canonical chainhead to the specified block hash. The block hash must be a child of
   * the current chainhead, that is already stored
   *
   * @param blockHeader The block header to forward to.
   * @return {@code true} on success, {@code false} if the block is not a child of the current head
   *     {@code blockNumber}
   */
  boolean forwardToBlock(final BlockHeader blockHeader);

  /**
   * Set the hash of the last finalized block.
   *
   * @param blockHash The hash of the last finalized block.
   */
  void setFinalized(final Hash blockHash);

  /**
   * Set the hash of the last safe block.
   *
   * @param blockHash The hash of the last safe block.
   */
  void setSafeBlock(final Hash blockHash);
}
