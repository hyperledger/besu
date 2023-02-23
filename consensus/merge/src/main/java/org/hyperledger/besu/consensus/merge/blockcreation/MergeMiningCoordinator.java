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
package org.hyperledger.besu.consensus.merge.blockcreation;

import static org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult.Status.IGNORE_UPDATE_TO_OLD_HEAD;
import static org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult.Status.VALID;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes32;

/** The interface Merge mining coordinator. */
public interface MergeMiningCoordinator extends MiningCoordinator {
  /**
   * Prepare payload identifier.
   *
   * @param parentHeader the parent header
   * @param timestamp the timestamp
   * @param prevRandao the prev randao
   * @param feeRecipient the fee recipient
   * @param withdrawals the optional list of withdrawals
   * @return the payload identifier
   */
  PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 prevRandao,
      final Address feeRecipient,
      final Optional<List<Withdrawal>> withdrawals);

  @Override
  default boolean isCompatibleWithEngineApi() {
    return true;
  }

  /**
   * Remember block.
   *
   * @param block the block
   * @return the block processing result
   */
  BlockProcessingResult rememberBlock(final Block block);

  /**
   * Validate block.
   *
   * @param block the block
   * @return the block processing result
   */
  BlockProcessingResult validateBlock(final Block block);

  /**
   * Update fork choice.
   *
   * @param newHead the new head
   * @param finalizedBlockHash the finalized block hash
   * @param safeBlockHash the safe block hash
   * @return the forkchoice result
   */
  ForkchoiceResult updateForkChoice(
      final BlockHeader newHead, final Hash finalizedBlockHash, final Hash safeBlockHash);

  /**
   * Gets latest valid ancestor.
   *
   * @param blockHash the block hash
   * @return the latest valid ancestor
   */
  Optional<Hash> getLatestValidAncestor(Hash blockHash);

  /**
   * Gets latest valid ancestor.
   *
   * @param blockheader the blockheader
   * @return the latest valid ancestor
   */
  Optional<Hash> getLatestValidAncestor(BlockHeader blockheader);

  /**
   * Check if latest valid ancestor descends from terminal.
   *
   * @param blockHeader the block header
   * @return the boolean
   */
  boolean latestValidAncestorDescendsFromTerminal(final BlockHeader blockHeader);

  /**
   * Is descendant of.
   *
   * @param ancestorBlock the ancestor block
   * @param newBlock the new block
   * @return the boolean
   */
  boolean isDescendantOf(final BlockHeader ancestorBlock, final BlockHeader newBlock);

  /**
   * Is backward syncing.
   *
   * @return the boolean
   */
  boolean isBackwardSyncing();

  /**
   * Append new payload to sync.
   *
   * @param newPayload the new payload
   * @return the completable future
   */
  CompletableFuture<Void> appendNewPayloadToSync(Block newPayload);

  /**
   * Gets or sync head by hash.
   *
   * @param headHash the head hash
   * @param finalizedHash the finalized hash
   * @return the or sync head by hash
   */
  Optional<BlockHeader> getOrSyncHeadByHash(Hash headHash, Hash finalizedHash);

  /**
   * Is mining before merge enabled.
   *
   * @return the boolean
   */
  boolean isMiningBeforeMerge();

  /**
   * Add bad block.
   *
   * @param block the block
   * @param maybeCause the maybe cause
   */
  void addBadBlock(final Block block, Optional<Throwable> maybeCause);

  /**
   * Is bad block.
   *
   * @param blockHash the block hash
   * @return the boolean
   */
  boolean isBadBlock(Hash blockHash);

  /**
   * Gets latest valid hash of bad block.
   *
   * @param blockHash the block hash
   * @return the latest valid hash of bad block
   */
  Optional<Hash> getLatestValidHashOfBadBlock(final Hash blockHash);

  /**
   * Finalize proposal by id.
   *
   * @param payloadId the payload id
   */
  void finalizeProposalById(final PayloadIdentifier payloadId);

  /** The type Forkchoice result. */
  class ForkchoiceResult {
    /** The enum Status. */
    public enum Status {
      /** Valid status. */
      VALID,
      /** Invalid status. */
      INVALID,
      /** Invalid payload attributes status. */
      INVALID_PAYLOAD_ATTRIBUTES,
      /** Ignore update to old head status. */
      IGNORE_UPDATE_TO_OLD_HEAD
    }

    private final Status status;
    private final Optional<String> errorMessage;
    private final Optional<BlockHeader> newFinalized;
    private final Optional<BlockHeader> newHead;
    private final Optional<Hash> latestValid;

    private ForkchoiceResult(
        final Status status,
        final Optional<String> errorMessage,
        final Optional<BlockHeader> newFinalized,
        final Optional<BlockHeader> newHead,
        final Optional<Hash> latestValid) {
      this.status = status;
      this.errorMessage = errorMessage;
      this.newFinalized = newFinalized;
      this.newHead = newHead;
      this.latestValid = latestValid;
    }

    /**
     * Create forkchoice result with failure.
     *
     * @param status the status
     * @param errorMessage the error message
     * @param latestValid the latest valid
     * @return the forkchoice result
     */
    public static ForkchoiceResult withFailure(
        final Status status, final String errorMessage, final Optional<Hash> latestValid) {
      return new ForkchoiceResult(
          status,
          Optional.ofNullable(errorMessage),
          Optional.empty(),
          Optional.empty(),
          latestValid);
    }

    /**
     * Create forkchoice result with ignore update to old head.
     *
     * @param oldHead the old head
     * @return the forkchoice result
     */
    public static ForkchoiceResult withIgnoreUpdateToOldHead(final BlockHeader oldHead) {
      return new ForkchoiceResult(
          IGNORE_UPDATE_TO_OLD_HEAD,
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.of(oldHead.getHash()));
    }

    /**
     * Create forkchoice result with result.
     *
     * @param newFinalized the new finalized
     * @param newHead the new head
     * @return the forkchoice result
     */
    public static ForkchoiceResult withResult(
        final Optional<BlockHeader> newFinalized, final Optional<BlockHeader> newHead) {
      return new ForkchoiceResult(VALID, Optional.empty(), newFinalized, newHead, Optional.empty());
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Gets error message.
     *
     * @return the error message
     */
    public Optional<String> getErrorMessage() {
      return errorMessage;
    }

    /**
     * Gets new finalized.
     *
     * @return the new finalized
     */
    public Optional<BlockHeader> getNewFinalized() {
      return newFinalized;
    }

    /**
     * Gets new head.
     *
     * @return the new head
     */
    public Optional<BlockHeader> getNewHead() {
      return newHead;
    }

    /**
     * Gets latest valid.
     *
     * @return the latest valid
     */
    public Optional<Hash> getLatestValid() {
      return latestValid;
    }

    /**
     * Is valid.
     *
     * @return the boolean
     */
    public boolean shouldNotProceedToPayloadBuildProcess() {
      return status != VALID;
    }
  }
}
