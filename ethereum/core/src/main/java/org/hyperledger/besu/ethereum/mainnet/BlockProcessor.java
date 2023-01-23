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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;

import java.util.List;
import java.util.Optional;

/** Processes a block. */
public interface BlockProcessor {

  /** A block processing result. */
  interface Result {

    /**
     * The receipts generated for the transactions in a block
     *
     * <p>This is only valid when {@code BlockProcessor#isSuccessful} returns {@code true}.
     *
     * @return the receipts generated for the transactions in a block
     */
    List<TransactionReceipt> getReceipts();

    /**
     * The private receipts generated for the private transactions in a block when in
     * goQuorumCompatibilityMode
     *
     * <p>This is only valid when {@code BlockProcessor#isSuccessful} returns {@code true}.
     *
     * @return the receipts generated for the private transactions in a block
     */
    List<TransactionReceipt> getPrivateReceipts();

    /**
     * Returns whether the block was successfully processed.
     *
     * @return {@code true} if the block was processed successfully; otherwise {@code false}
     */
    boolean isSuccessful();

    default boolean isFailed() {
      return !isSuccessful();
    }
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param block the block to process
   * @return the block processing result
   */
  default BlockProcessingResult processBlock(
      final Blockchain blockchain, final MutableWorldState worldState, final Block block) {
    return processBlock(
        blockchain,
        worldState,
        block.getHeader(),
        block.getBody().getTransactions(),
        block.getBody().getOmmers(),
        block.getBody().getWithdrawals(),
        null);
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param blockHeader the block header for the block
   * @param transactions the transactions in the block
   * @param ommers the block ommers
   * @return the block processing result
   */
  default BlockProcessingResult processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    return processBlock(
        blockchain, worldState, blockHeader, transactions, ommers, Optional.empty(), null);
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param blockHeader the block header for the block
   * @param transactions the transactions in the block
   * @param ommers the block ommers
   * @param withdrawals the withdrawals for the block
   * @param privateMetadataUpdater the updater used to update the private metadata for the block
   * @return the block processing result
   */
  BlockProcessingResult processBlock(
      Blockchain blockchain,
      MutableWorldState worldState,
      BlockHeader blockHeader,
      List<Transaction> transactions,
      List<BlockHeader> ommers,
      Optional<List<Withdrawal>> withdrawals,
      PrivateMetadataUpdater privateMetadataUpdater);

  /**
   * Processes the block when running Besu in GoQuorum-compatible mode
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply public transactions to
   * @param privateWorldState the private world state to apply private transaction to
   * @param block the block to process
   * @return the block processing result
   */
  default BlockProcessingResult processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final MutableWorldState privateWorldState,
      final Block block) {
    /*
     This method should never be executed. All GoQuorum processing must happen in the GoQuorumBlockProcessor.
    */
    throw new IllegalStateException("Tried to process GoQuorum block on AbstractBlockProcessor");
  }

  /**
   * Get ommer reward in ${@link Wei}
   *
   * @param blockReward reward of the block
   * @param blockNumber number of the block
   * @param ommerBlockNumber number of the block ommer
   * @return ommer reward
   */
  default Wei getOmmerReward(
      final Wei blockReward, final long blockNumber, final long ommerBlockNumber) {
    final long distance = blockNumber - ommerBlockNumber;
    return blockReward.subtract(blockReward.multiply(distance).divide(8));
  }

  /**
   * Get coinbase reward in ${@link Wei}
   *
   * @param blockReward reward of the block
   * @param blockNumber number of the block
   * @param numberOfOmmers number of ommers for this block
   * @return coinbase reward
   */
  default Wei getCoinbaseReward(
      final Wei blockReward, final long blockNumber, final int numberOfOmmers) {
    return blockReward.add(blockReward.multiply(numberOfOmmers).divide(32));
  }
}
