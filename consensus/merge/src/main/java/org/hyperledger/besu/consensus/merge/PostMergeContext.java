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
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
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

  private final AtomicReference<SyncState> syncState;
  private final AtomicReference<Difficulty> terminalTotalDifficulty;
  // transition miners are created disabled by default, so reflect that default:
  private final AtomicBoolean isPostMerge = new AtomicBoolean(true);
  private final AtomicBoolean isAtGenesis = new AtomicBoolean(true);
  private final Subscribers<NewMergeStateCallback> newMergeStateCallbackSubscribers =
      Subscribers.create();

  private final Map<PayloadIdentifier, Block> blocksInProgressById = new ConcurrentHashMap<>();

  // latest finalized block
  // TODO: persist this to storage https://github.com/hyperledger/besu/issues/2913
  AtomicReference<BlockHeader> lastFinalized = new AtomicReference<>();

  private PostMergeContext() {
    this.terminalTotalDifficulty = new AtomicReference<>(Difficulty.ZERO);
    this.syncState = new AtomicReference<>();
  }

  public static synchronized PostMergeContext get() {
    // TODO: get rid of singleton if possible: https://github.com/hyperledger/besu/issues/2898
    if (singleton == null) {
      singleton = new PostMergeContext();
    }
    return singleton;
  }

  @Override
  public <C extends ConsensusContext> C as(final Class<C> klass) {
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
    final boolean atGenesis = isAtGenesis.getAndSet(Boolean.FALSE);

    // if we are past TTD, set it:
    if (newState)
      Optional.ofNullable(syncState.get())
          .ifPresent(ss -> ss.setStoppedAtTerminalDifficulty(newState));

    if (oldState != newState || atGenesis) {
      newMergeStateCallbackSubscribers.forEach(
          newMergeStateCallback -> newMergeStateCallback.onNewIsPostMergeState(newState));
    }
  }

  @Override
  public boolean isPostMerge() {
    return isPostMerge.get();
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
        && !syncState.get().isStoppedAtTerminalDifficulty().orElse(Boolean.FALSE);
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
  public void setFinalized(final BlockHeader blockHeader) {
    lastFinalized.set(blockHeader);
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
