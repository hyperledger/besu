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
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;

public class PostMergeContext implements MergeContext {
  static final int MAX_BLOCKS_IN_PROGRESS = 12;

  private static final AtomicReference<PostMergeContext> singleton = new AtomicReference<>();

  private final AtomicReference<SyncState> syncState;
  private final AtomicReference<Difficulty> terminalTotalDifficulty;
  // initial postMerge state is indeterminate until it is set:
  private final AtomicReference<Optional<Boolean>> isPostMerge =
      new AtomicReference<>(Optional.empty());
  private final Subscribers<MergeStateHandler> newMergeStateCallbackSubscribers =
      Subscribers.create();
  private final Subscribers<ForkchoiceMessageListener> newForkchoiceMessageCallbackSubscribers =
      Subscribers.create();

  private final EvictingQueue<PayloadTuple> blocksInProgress =
      EvictingQueue.create(MAX_BLOCKS_IN_PROGRESS);

  // latest finalized block
  private final AtomicReference<BlockHeader> lastFinalized = new AtomicReference<>();
  private final AtomicReference<BlockHeader> lastSafeBlock = new AtomicReference<>();
  private final AtomicReference<Optional<BlockHeader>> terminalPoWBlock =
      new AtomicReference<>(Optional.empty());

  @VisibleForTesting
  PostMergeContext() {
    this(Difficulty.ZERO);
  }

  @VisibleForTesting
  PostMergeContext(final Difficulty difficulty) {
    this.terminalTotalDifficulty = new AtomicReference<>(difficulty);
    this.syncState = new AtomicReference<>();
  }

  public static PostMergeContext get() {
    if (singleton.get() == null) {
      singleton.compareAndSet(null, new PostMergeContext());
    }
    return singleton.get();
  }

  @Override
  public <C extends ConsensusContext> C as(final Class<C> klass) {
    return klass.cast(this);
  }

  @Override
  public PostMergeContext setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty) {
    if (newTerminalTotalDifficulty == null) {
      throw new IllegalStateException("cannot set null terminal total difficulty");
    }
    terminalTotalDifficulty.set(newTerminalTotalDifficulty);
    return this;
  }

  @Override
  public void setIsPostMerge(final Difficulty totalDifficulty) {
    if (isPostMerge.get().orElse(Boolean.FALSE)) {
      // if we have finalized, we never switch back to a pre-merge once we have transitioned
      // post-TTD.
      return;
    }
    final boolean newState = terminalTotalDifficulty.get().lessOrEqualThan(totalDifficulty);
    final Optional<Boolean> oldState = isPostMerge.getAndSet(Optional.of(newState));

    // if we are past TTD, set it:
    if (newState)
      Optional.ofNullable(syncState.get())
          .ifPresent(ss -> ss.setReachedTerminalDifficulty(newState));

    if (oldState.isEmpty() || oldState.get() != newState) {
      newMergeStateCallbackSubscribers.forEach(
          newMergeStateCallback ->
              newMergeStateCallback.mergeStateChanged(
                  newState, oldState, Optional.of(totalDifficulty)));
    }
  }

  @Override
  public boolean isPostMerge() {
    return isPostMerge.get().orElse(Boolean.FALSE);
  }

  @Override
  public PostMergeContext setSyncState(final SyncState syncState) {
    this.syncState.set(syncState);
    return this;
  }

  @Override
  public boolean isSyncing() {
    return Optional.ofNullable(syncState.get()).map(s -> !s.isInSync()).orElse(Boolean.TRUE)
        // this is necessary for when we do not have a sync target yet, like at startup.
        // not being stopped at ttd implies we are syncing.
        && !syncState.get().hasReachedTerminalDifficulty().orElse(Boolean.FALSE);
  }

  @Override
  public void observeNewIsPostMergeState(final MergeStateHandler mergeStateHandler) {
    newMergeStateCallbackSubscribers.subscribe(mergeStateHandler);
  }

  @Override
  public long addNewForkchoiceMessageListener(
      final ForkchoiceMessageListener forkchoiceMessageListener) {
    return newForkchoiceMessageCallbackSubscribers.subscribe(forkchoiceMessageListener);
  }

  @Override
  public void removeNewForkchoiceMessageListener(final long subscriberId) {
    newForkchoiceMessageCallbackSubscribers.unsubscribe(subscriberId);
  }

  @Override
  public void fireNewUnverifiedForkchoiceMessageEvent(
      final Hash headBlockHash,
      final Optional<Hash> maybeFinalizedBlockHash,
      final Hash safeBlockHash) {
    newForkchoiceMessageCallbackSubscribers.forEach(
        cb -> cb.onNewForkchoiceMessage(headBlockHash, maybeFinalizedBlockHash, safeBlockHash));
  }

  @Override
  public Difficulty getTerminalTotalDifficulty() {
    return terminalTotalDifficulty.get();
  }

  @Override
  public void setFinalized(final BlockHeader blockHeader) {
    lastFinalized.set(blockHeader);
  }

  @Override
  public Optional<BlockHeader> getFinalized() {
    return Optional.ofNullable(lastFinalized.get());
  }

  @Override
  public void setSafeBlock(final BlockHeader blockHeader) {
    lastSafeBlock.set(blockHeader);
  }

  @Override
  public Optional<BlockHeader> getSafeBlock() {
    return Optional.ofNullable(lastSafeBlock.get());
  }

  @Override
  public Optional<BlockHeader> getTerminalPoWBlock() {
    return terminalPoWBlock.get();
  }

  @Override
  public void setTerminalPoWBlock(final Optional<BlockHeader> hashAndNumber) {
    terminalPoWBlock.set(hashAndNumber);
  }

  @Override
  public boolean validateCandidateHead(final BlockHeader candidateHeader) {
    return Optional.ofNullable(lastFinalized.get())
        .map(finalized -> candidateHeader.getNumber() >= finalized.getNumber())
        .orElse(Boolean.TRUE);
  }

  @Override
  public void putPayloadById(final PayloadIdentifier payloadId, final Block block) {
    var priorsById = retrieveTuplesById(payloadId).collect(Collectors.toUnmodifiableList());
    blocksInProgress.add(new PayloadTuple(payloadId, block));
    priorsById.stream().forEach(blocksInProgress::remove);
  }

  @Override
  public Optional<Block> retrieveBlockById(final PayloadIdentifier payloadId) {
    return retrieveTuplesById(payloadId).map(tuple -> tuple.block).findFirst();
  }

  private Stream<PayloadTuple> retrieveTuplesById(final PayloadIdentifier payloadId) {
    return blocksInProgress.stream().filter(z -> z.payloadIdentifier.equals(payloadId));
  }

  private static class PayloadTuple {
    final PayloadIdentifier payloadIdentifier;
    final Block block;

    PayloadTuple(final PayloadIdentifier payloadIdentifier, final Block block) {
      this.payloadIdentifier = payloadIdentifier;
      this.block = block;
    }
  }
}
