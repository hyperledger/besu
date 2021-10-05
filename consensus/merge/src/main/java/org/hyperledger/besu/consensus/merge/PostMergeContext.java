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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.MergeBlockProcessor.CandidateBlock;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.util.Subscribers;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PostMergeContext implements MergeContext {
  private static final Logger LOG = LogManager.getLogger();
  private static PostMergeContext singleton;

  private final AtomicReference<Difficulty> terminalTotalDifficulty;
  // transition miners are created disabled by default, so reflect that default:
  private final AtomicBoolean isPostMerge = new AtomicBoolean(true);
  private final Subscribers<NewMergeStateCallback> newMergeStateCallbackSubscribers =
      Subscribers.create();

  private final Map<PayloadIdentifier, Block> blocksInProgressById = new ConcurrentHashMap<>();

  // current candidate block from consensus engine
  AtomicReference<CandidateBlock> candidateBlock = new AtomicReference<>();

  // latest finalized block
  // TODO: persist this to storage https://github.com/ConsenSys/protocol-misc/issues/478
  AtomicReference<BlockHeader> lastFinalized = new AtomicReference<>();

  private PostMergeContext() {
    this.terminalTotalDifficulty = new AtomicReference<>(Difficulty.ZERO);
  }

  public static synchronized PostMergeContext get() {
    // TODO: get rid of singleton
    if (singleton == null) {
      singleton = new PostMergeContext();
    }
    return singleton;
  }

  @Override
  public <C extends ConsensusContext> C get(final Class<C> klass) {
    return klass.cast(this);
  }

  @Override
  public PostMergeContext setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty) {
    terminalTotalDifficulty.set(newTerminalTotalDifficulty);
    return this;
  }

  @Override
  public void setIsPostMerge(final Difficulty totalDifficulty) {
    if (Optional.ofNullable(lastFinalized.get()).isPresent()) {
      // we check this condition because if we've received a finalized block, we never want to
      // switch back to a pre-merge
      return;
    }
    final boolean newState = terminalTotalDifficulty.get().lessOrEqualThan(totalDifficulty);
    final boolean oldState = isPostMerge.getAndSet(newState);
    if (oldState != newState) {
      newMergeStateCallbackSubscribers.forEach(
          newMergeStateCallback -> newMergeStateCallback.onNewIsPostMergeState(newState));
    }
  }

  @Override
  public boolean isPostMerge() {
    return isPostMerge.get();
  }

  @Override
  public void observeNewIsPostMergeState(final NewMergeStateCallback newMergeStateCallback) {
    newMergeStateCallbackSubscribers.subscribe(newMergeStateCallback);
  }

  @Override
  public Difficulty getTerminalTotalDifficulty() {
    return terminalTotalDifficulty.get();
  }

  @Override
  public void updateForkChoice(final Hash headBlockHash, final Hash finalizedBlockHash) {
    // only empty if we haven't ever finalized yet
    Optional<BlockHeader> maybeNewFinalized =
        candidateBlock.get().updateForkChoice(headBlockHash, finalizedBlockHash);
    Optional.ofNullable(lastFinalized.get())
        .ifPresentOrElse(
            last -> {
              final BlockHeader newFinalized =
                  maybeNewFinalized.orElseThrow(
                      () ->
                          new IllegalStateException(
                              "we have a last finalized so we should always have a valid new finalized"));
              if (last.getNumber() <= newFinalized.getNumber()) {
                lastFinalized.set(newFinalized);
              } else {
                LOG.error(
                    "Attempted to set finalized head {}:{} earlier tha existing finalized head {}:{}",
                    newFinalized.getHash(),
                    newFinalized.getNumber(),
                    last.getHash(),
                    last.getNumber());
              }
            },
            // waiting for first finalized block, set it if we got it:
            () -> maybeNewFinalized.ifPresent(lastFinalized::set));
  }

  @Override
  public Optional<BlockHeader> getFinalized() {
    return Optional.ofNullable(lastFinalized.get());
  }

  @Override
  public boolean validateCandidateHead(final BlockHeader candidateHeader) {
    return Optional.ofNullable(lastFinalized.get())
        .map(finalized -> candidateHeader.getNumber() >= finalized.getNumber())
        .orElse(Boolean.TRUE);
  }

  @Override
  public void setCandidateBlock(final CandidateBlock candidate) {
    this.candidateBlock.set(candidate);
  }

  @Override
  public boolean setConsensusValidated(final Hash candidateHash) {
    return Optional.ofNullable(candidateBlock.get())
        .filter(candidate -> candidate.getBlockHash().equals(candidateHash))
        .map(
            candidate -> {
              candidate.setConsensusValidated();
              return true;
            })
        .orElse(false);
  }

  @Override
  public void putPayloadById(final PayloadIdentifier payloadId, final Block block) {
    blocksInProgressById.put(payloadId, block);
  }

  @Override
  public void replacePayloadById(final PayloadIdentifier payloadId, final Block block) {
    if (blocksInProgressById.replace(payloadId, block) == null) {
      LOG.warn("");
    }
  }

  @Override
  public Optional<Block> retrieveBlockById(final PayloadIdentifier payloadId) {
    return Optional.ofNullable(blocksInProgressById.remove(payloadId));
  }
}
