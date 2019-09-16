/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.List;

/** Processes a block. */
public interface BlockProcessor {

  /** A block processing result. */
  interface Result {

    /**
     * The receipts generated for the transactions in a block
     *
     * <p>This is only valid when {@code BlockProcessor#isSuccessful} returns {@code true}.
     *
     * @return the receipts generated for the transactions the a block
     */
    List<TransactionReceipt> getReceipts();

    /**
     * Returns whether the block was successfully processed.
     *
     * @return {@code true} if the block was processed successfully; otherwise {@code false}
     */
    boolean isSuccessful();
  }

  /**
   * Processes the block.
   *
   * @param blockchain the blockchain to append the block to
   * @param worldState the world state to apply changes to
   * @param block the block to process
   * @return the block processing result
   */
  default Result processBlock(
      final Blockchain blockchain, final MutableWorldState worldState, final Block block) {
    return processBlock(
        blockchain,
        worldState,
        block.getHeader(),
        block.getBody().getTransactions(),
        block.getBody().getOmmers());
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
  Result processBlock(
      Blockchain blockchain,
      MutableWorldState worldState,
      BlockHeader blockHeader,
      List<Transaction> transactions,
      List<BlockHeader> ommers);
}
