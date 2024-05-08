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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.List;
import java.util.Optional;

/** The interface Block creator. */
public interface BlockCreator {
  /** The type Block creation result. */
  class BlockCreationResult {
    private final Block block;
    private final TransactionSelectionResults transactionSelectionResults;
    private final BlockCreationTiming blockCreationTiming;

    /**
     * Instantiates a new Block creation result.
     *
     * @param block the block
     * @param transactionSelectionResults the transaction selection results
     * @param timings the timings
     */
    public BlockCreationResult(
        final Block block,
        final TransactionSelectionResults transactionSelectionResults,
        final BlockCreationTiming timings) {
      this.block = block;
      this.transactionSelectionResults = transactionSelectionResults;
      this.blockCreationTiming = timings;
    }

    /**
     * Gets block.
     *
     * @return the block
     */
    public Block getBlock() {
      return block;
    }

    /**
     * Gets transaction selection results.
     *
     * @return the transaction selection results
     */
    public TransactionSelectionResults getTransactionSelectionResults() {
      return transactionSelectionResults;
    }

    /**
     * Gets block creation timings.
     *
     * @return the block creation timings
     */
    public BlockCreationTiming getBlockCreationTimings() {
      return blockCreationTiming;
    }
  }

  /**
   * Create block block creation result.
   *
   * @param timestamp the timestamp
   * @return the block creation result
   */
  BlockCreationResult createBlock(final long timestamp);

  /**
   * Create empty withdrawals block block creation result.
   *
   * @param timestamp the timestamp
   * @return the block creation result
   */
  BlockCreationResult createEmptyWithdrawalsBlock(final long timestamp);

  /**
   * Create block block creation result.
   *
   * @param transactions the transactions
   * @param ommers the ommers
   * @param timestamp the timestamp
   * @return the block creation result
   */
  BlockCreationResult createBlock(
      final List<Transaction> transactions, final List<BlockHeader> ommers, final long timestamp);

  /**
   * Create block block creation result.
   *
   * @param maybeTransactions the maybe transactions
   * @param maybeOmmers the maybe ommers
   * @param timestamp the timestamp
   * @return the block creation result
   */
  BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp);
}
