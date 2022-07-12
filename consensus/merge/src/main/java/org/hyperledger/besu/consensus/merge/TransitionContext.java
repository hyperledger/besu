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
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;

public class TransitionContext implements MergeContext {
  final ConsensusContext preMergeContext;
  final MergeContext postMergeContext;

  public TransitionContext(
      final ConsensusContext preMergeContext, final MergeContext postMergeContext) {
    this.preMergeContext = preMergeContext;
    this.postMergeContext = postMergeContext;
  }

  @Override
  public <C extends ConsensusContext> C as(final Class<C> klass) {
    if (klass.isInstance(postMergeContext)) {
      return klass.cast(postMergeContext);
    }
    return klass.cast(preMergeContext);
  }

  @Override
  public MergeContext setSyncState(final SyncState syncState) {
    return postMergeContext.setSyncState(syncState);
  }

  @Override
  public MergeContext setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty) {
    return postMergeContext.setTerminalTotalDifficulty(newTerminalTotalDifficulty);
  }

  @Override
  public void setIsPostMerge(final Difficulty totalDifficulty) {
    postMergeContext.setIsPostMerge(totalDifficulty);
  }

  @Override
  public boolean isPostMerge() {
    return postMergeContext.isPostMerge();
  }

  @Override
  public boolean isSyncing() {
    return postMergeContext.isSyncing();
  }

  @Override
  public void observeNewIsPostMergeState(final MergeStateHandler mergeStateHandler) {
    postMergeContext.observeNewIsPostMergeState(mergeStateHandler);
  }

  @Override
  public long addNewForkchoiceMessageListener(
      final ForkchoiceMessageListener forkchoiceMessageListener) {
    return postMergeContext.addNewForkchoiceMessageListener(forkchoiceMessageListener);
  }

  @Override
  public void removeNewForkchoiceMessageListener(final long subscriberId) {
    postMergeContext.removeNewForkchoiceMessageListener(subscriberId);
  }

  @Override
  public void fireNewUnverifiedForkchoiceMessageEvent(
      final Hash headBlockHash,
      final Optional<Hash> maybeFinalizedBlockHash,
      final Hash safeBlockHash) {
    postMergeContext.fireNewUnverifiedForkchoiceMessageEvent(
        headBlockHash, maybeFinalizedBlockHash, safeBlockHash);
  }

  @Override
  public Difficulty getTerminalTotalDifficulty() {
    return postMergeContext.getTerminalTotalDifficulty();
  }

  @Override
  public void setFinalized(final BlockHeader blockHeader) {
    postMergeContext.setFinalized(blockHeader);
  }

  @Override
  public Optional<BlockHeader> getFinalized() {
    return postMergeContext.getFinalized();
  }

  @Override
  public void setSafeBlock(final BlockHeader blockHeader) {
    postMergeContext.setSafeBlock(blockHeader);
  }

  @Override
  public Optional<BlockHeader> getSafeBlock() {
    return postMergeContext.getSafeBlock();
  }

  @Override
  public Optional<BlockHeader> getTerminalPoWBlock() {
    return this.postMergeContext.getTerminalPoWBlock();
  }

  @Override
  public void setTerminalPoWBlock(final Optional<BlockHeader> hashAndNumber) {
    this.postMergeContext.setTerminalPoWBlock(hashAndNumber);
  }

  @Override
  public boolean validateCandidateHead(final BlockHeader candidateHeader) {
    return postMergeContext.validateCandidateHead(candidateHeader);
  }

  @Override
  public void putPayloadById(final PayloadIdentifier payloadId, final Block block) {
    postMergeContext.putPayloadById(payloadId, block);
  }

  @Override
  public Optional<Block> retrieveBlockById(final PayloadIdentifier payloadId) {
    return postMergeContext.retrieveBlockById(payloadId);
  }
}
