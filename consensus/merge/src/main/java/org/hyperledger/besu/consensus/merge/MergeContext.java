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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;

/** The interface Merge context. */
public interface MergeContext extends ConsensusContext {

  /**
   * Sets sync state.
   *
   * @param syncState the sync state
   * @return the sync state
   */
  MergeContext setSyncState(SyncState syncState);

  /**
   * Sets terminal total difficulty.
   *
   * @param newTerminalTotalDifficulty the new terminal total difficulty
   * @return the terminal total difficulty
   */
  MergeContext setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty);

  /**
   * Sets is post merge.
   *
   * @param totalDifficulty the total difficulty
   */
  void setIsPostMerge(final Difficulty totalDifficulty);

  /**
   * Is post merge.
   *
   * @return the boolean
   */
  boolean isPostMerge();

  /**
   * Is syncing.
   *
   * @return the boolean
   */
  boolean isSyncing();

  /**
   * Observe new is post merge state.
   *
   * @param mergeStateHandler the merge state handler
   */
  void observeNewIsPostMergeState(final MergeStateHandler mergeStateHandler);

  /**
   * Add new unverified forkchoice listener.
   *
   * @param unverifiedForkchoiceListener the unverified forkchoice listener
   * @return the long
   */
  long addNewUnverifiedForkchoiceListener(
      final UnverifiedForkchoiceListener unverifiedForkchoiceListener);

  /**
   * Remove new unverified forkchoice listener.
   *
   * @param subscriberId the subscriber id
   */
  void removeNewUnverifiedForkchoiceListener(final long subscriberId);

  /**
   * Fire new unverified forkchoice event.
   *
   * @param headBlockHash the head block hash
   * @param safeBlockHash the safe block hash
   * @param finalizedBlockHash the finalized block hash
   */
  void fireNewUnverifiedForkchoiceEvent(
      final Hash headBlockHash, final Hash safeBlockHash, final Hash finalizedBlockHash);

  /**
   * Gets terminal total difficulty.
   *
   * @return the terminal total difficulty
   */
  Difficulty getTerminalTotalDifficulty();

  /**
   * Sets finalized.
   *
   * @param blockHeader the block header
   */
  void setFinalized(final BlockHeader blockHeader);

  /**
   * Gets finalized.
   *
   * @return the finalized
   */
  Optional<BlockHeader> getFinalized();

  /**
   * Sets safe block.
   *
   * @param blockHeader the block header
   */
  void setSafeBlock(final BlockHeader blockHeader);

  /**
   * Gets safe block.
   *
   * @return the safe block
   */
  Optional<BlockHeader> getSafeBlock();

  /**
   * Gets terminal PoW block.
   *
   * @return the terminal po w block
   */
  Optional<BlockHeader> getTerminalPoWBlock();

  /**
   * Sets terminal PoW block.
   *
   * @param hashAndNumber the hash and number
   */
  void setTerminalPoWBlock(Optional<BlockHeader> hashAndNumber);

  /**
   * Validate candidate head.
   *
   * @param candidateHeader the candidate header
   * @return the boolean
   */
  boolean validateCandidateHead(final BlockHeader candidateHeader);

  /**
   * Put payload by Identifier.
   *
   * @param payloadId the payload identifier
   * @param blockWithReceipts the block with receipts
   */
  void putPayloadById(final PayloadIdentifier payloadId, final BlockWithReceipts blockWithReceipts);

  /**
   * Retrieve block by id.
   *
   * @param payloadId the payload identifier
   * @return the optional block with receipts
   */
  Optional<BlockWithReceipts> retrieveBlockById(final PayloadIdentifier payloadId);

  /**
   * Sets is chain pruning enabled.
   *
   * @param isChainPruningEnabled the is chain pruning enabled
   */
  default void setIsChainPruningEnabled(final boolean isChainPruningEnabled) {}

  /**
   * Is chain pruning enabled.
   *
   * @return the boolean
   */
  default boolean isChainPruningEnabled() {
    return false;
  }

  /**
   * Is checkpoint post merge sync.
   *
   * @return the boolean
   */
  boolean isCheckpointPostMergeSync();

  /**
   * Is configured for a post-merge from genesis.
   *
   * @return the boolean
   */
  boolean isPostMergeAtGenesis();
}
