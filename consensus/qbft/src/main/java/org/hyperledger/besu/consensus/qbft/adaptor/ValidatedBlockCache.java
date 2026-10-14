/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.bft.SizeLimitedMap;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outputs of blocks created or validated by this node, to not execute them again for validation or
 * import.
 *
 * <p>Block creation and proposal validation both save the block trie log. On commit, the importer
 * only needs the receipts, and moves the head world state with the trie log.
 */
public class ValidatedBlockCache {

  /** Blocks of current height, plus few from round changes. */
  private static final int MAX_ENTRIES = 4;

  /**
   * Outputs needed by the import.
   *
   * @param blockNumber the block number
   * @param body the executed block body
   * @param receipts the receipts produced by processing the block
   * @param blockAccessList the block access list produced by processing the block, if any
   */
  public record ValidatedBlock(
      long blockNumber,
      BlockBody body,
      List<TransactionReceipt> receipts,
      Optional<BlockAccessList> blockAccessList) {

    /**
     * Check the body is the one executed. Same object and not equals, to not compare all the
     * transactions. The proposal and the committed block share the same body object.
     *
     * @param blockBody the body of the block to validate or import
     * @return true if it is the executed body
     */
    @SuppressWarnings("ReferenceEquality")
    public boolean isExecutedBody(final BlockBody blockBody) {
      return body == blockBody;
    }
  }

  private final Map<Hash, ValidatedBlock> entries = new SizeLimitedMap<>(MAX_ENTRIES);

  /**
   * Hash to block number of the proposals the current QBFT round claimed. Kept apart from {@link
   * #entries}: outputs outlive the round that produced them, a claim does not.
   */
  private final Map<Hash, Long> claimed = new SizeLimitedMap<>(MAX_ENTRIES);

  /** Creates an empty cache. */
  public ValidatedBlockCache() {}

  /**
   * Record that the current QBFT round is validating this proposal, or has accepted it and will
   * import it if the round commits. Until {@link #release}, {@link #isOwnedByConsensus} returns
   * true, so a {@code NewBlock} announcement for that block is not imported by the ETH layer at the
   * same time. Claims of lower heights are dropped, their blocks are already decided.
   *
   * @param blockHash the proposal hash
   * @param blockNumber the block number
   */
  public synchronized void claim(final Hash blockHash, final long blockNumber) {
    claimed.values().removeIf(number -> number < blockNumber);
    claimed.put(blockHash, blockNumber);
  }

  /**
   * Release a claim: the proposal was rejected, its round ended, or its import was attempted.
   *
   * @param blockHash the proposal hash
   */
  public synchronized void release(final Hash blockHash) {
    claimed.remove(blockHash);
  }

  /**
   * Whether the current QBFT round claimed this block and will import it itself if it commits.
   *
   * @param blockHash the block hash
   * @return true if consensus owns the import of this block
   */
  public synchronized boolean isOwnedByConsensus(final Hash blockHash) {
    return claimed.containsKey(blockHash);
  }

  /**
   * Add outputs of a created or validated block.
   *
   * @param blockHash the block hash
   * @param validatedBlock the block outputs
   */
  public synchronized void put(final Hash blockHash, final ValidatedBlock validatedBlock) {
    entries.put(blockHash, validatedBlock);
  }

  /**
   * Get outputs of a block, without removing them.
   *
   * @param blockHash the block hash
   * @return outputs if the block was created or validated
   */
  public synchronized Optional<ValidatedBlock> get(final Hash blockHash) {
    return Optional.ofNullable(entries.get(blockHash));
  }

  /**
   * Removes and returns the outputs recorded for a block, dropping any entry at or below its
   * height, as no other block can be imported at those heights afterwards.
   *
   * @param blockHash the hash of the block being imported
   * @param blockNumber the number of the block being imported
   * @return outputs if the block was created or validated
   */
  public synchronized Optional<ValidatedBlock> take(final Hash blockHash, final long blockNumber) {
    final ValidatedBlock validatedBlock = entries.remove(blockHash);
    entries.values().removeIf(entry -> entry.blockNumber() <= blockNumber);
    return Optional.ofNullable(validatedBlock);
  }
}
