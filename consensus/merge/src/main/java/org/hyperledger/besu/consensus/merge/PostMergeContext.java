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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockValueCalculator;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.util.Subscribers;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.EvictingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Post merge context. */
public class PostMergeContext implements MergeContext {
  private static final Logger LOG = LoggerFactory.getLogger(PostMergeContext.class);
  /** The Max blocks in progress. */
  static final int MAX_BLOCKS_IN_PROGRESS = 12;

  private static final AtomicReference<PostMergeContext> singleton = new AtomicReference<>();

  private static final Comparator<BlockWithReceipts> compareByGasUsedDesc =
      Comparator.comparingLong(
              (BlockWithReceipts blockWithReceipts) ->
                  blockWithReceipts.getBlock().getHeader().getGasUsed())
          .reversed();

  private final AtomicReference<SyncState> syncState;
  private final AtomicReference<Difficulty> terminalTotalDifficulty;
  // initial postMerge state is indeterminate until it is set:
  private final AtomicReference<Optional<Boolean>> isPostMerge =
      new AtomicReference<>(Optional.empty());
  private final Subscribers<MergeStateHandler> newMergeStateCallbackSubscribers =
      Subscribers.create();
  private final Subscribers<UnverifiedForkchoiceListener>
      newUnverifiedForkchoiceCallbackSubscribers = Subscribers.create();

  private final EvictingQueue<PayloadWrapper> blocksInProgress =
      EvictingQueue.create(MAX_BLOCKS_IN_PROGRESS);

  // latest finalized block
  private final AtomicReference<BlockHeader> lastFinalized = new AtomicReference<>();
  private final AtomicReference<BlockHeader> lastSafeBlock = new AtomicReference<>();
  private final AtomicReference<Optional<BlockHeader>> terminalPoWBlock =
      new AtomicReference<>(Optional.empty());
  private final BlockValueCalculator blockValueCalculator = new BlockValueCalculator();
  private boolean isCheckpointPostMergeSync;
  private boolean isPostMergeAtGenesis;

  // TODO: cleanup - isChainPruningEnabled will not be required after
  // https://github.com/hyperledger/besu/pull/4703 is merged.
  private boolean isChainPruningEnabled = false;

  /** Instantiates a new Post merge context. */
  @VisibleForTesting
  PostMergeContext() {
    this(Difficulty.ZERO);
  }

  /**
   * Instantiates a new Post merge context.
   *
   * @param difficulty the difficulty
   */
  @VisibleForTesting
  PostMergeContext(final Difficulty difficulty) {
    this.terminalTotalDifficulty = new AtomicReference<>(difficulty);
    this.syncState = new AtomicReference<>();
    this.isCheckpointPostMergeSync = false;
  }

  /**
   * Get post merge context.
   *
   * @return the post merge context
   */
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
        && Optional.ofNullable(syncState.get())
            .map(s -> !(s.hasReachedTerminalDifficulty().orElse(Boolean.FALSE)))
            .orElse(Boolean.TRUE);
  }

  @Override
  public void observeNewIsPostMergeState(final MergeStateHandler mergeStateHandler) {
    newMergeStateCallbackSubscribers.subscribe(mergeStateHandler);
  }

  @Override
  public long addNewUnverifiedForkchoiceListener(
      final UnverifiedForkchoiceListener unverifiedForkchoiceListener) {
    return newUnverifiedForkchoiceCallbackSubscribers.subscribe(unverifiedForkchoiceListener);
  }

  @Override
  public void removeNewUnverifiedForkchoiceListener(final long subscriberId) {
    newUnverifiedForkchoiceCallbackSubscribers.unsubscribe(subscriberId);
  }

  @Override
  public void fireNewUnverifiedForkchoiceEvent(
      final Hash headBlockHash, final Hash safeBlockHash, final Hash finalizedBlockHash) {
    final ForkchoiceEvent event =
        new ForkchoiceEvent(headBlockHash, safeBlockHash, finalizedBlockHash);
    newUnverifiedForkchoiceCallbackSubscribers.forEach(cb -> cb.onNewUnverifiedForkchoice(event));
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
  public void putPayloadById(final PayloadWrapper payloadWrapper) {
    synchronized (blocksInProgress) {
      final Optional<BlockWithReceipts> maybeCurrBestBlock =
          retrieveBlockById(payloadWrapper.payloadIdentifier());

      maybeCurrBestBlock.ifPresentOrElse(
          currBestBlock -> {
            if (compareByGasUsedDesc.compare(payloadWrapper.blockWithReceipts(), currBestBlock)
                < 0) {
              LOG.atDebug()
                  .setMessage("New proposal for payloadId {} {} is better than the previous one {}")
                  .addArgument(payloadWrapper.payloadIdentifier())
                  .addArgument(
                      () -> logBlockProposal(payloadWrapper.blockWithReceipts().getBlock()))
                  .addArgument(() -> logBlockProposal(currBestBlock.getBlock()))
                  .log();
              blocksInProgress.removeAll(
                  retrievePayloadsById(payloadWrapper.payloadIdentifier())
                      .collect(Collectors.toUnmodifiableList()));
              blocksInProgress.add(
                  new PayloadWrapper(
                      payloadWrapper.payloadIdentifier(), payloadWrapper.blockWithReceipts()));
              logCurrentBestBlock(payloadWrapper.blockWithReceipts());
            }
          },
          () ->
              blocksInProgress.add(
                  new PayloadWrapper(
                      payloadWrapper.payloadIdentifier(), payloadWrapper.blockWithReceipts())));
    }
  }

  private void logCurrentBestBlock(final BlockWithReceipts blockWithReceipts) {
    if (LOG.isDebugEnabled()) {
      final Block block = blockWithReceipts.getBlock();
      final float gasUsedPerc =
          100.0f * block.getHeader().getGasUsed() / block.getHeader().getGasLimit();
      final int txsNum = block.getBody().getTransactions().size();
      final Wei reward = blockValueCalculator.calculateBlockValue(blockWithReceipts);

      LOG.debug(
          "Current best proposal for block {}: txs {}, gas used {}%, reward {}",
          blockWithReceipts.getNumber(),
          txsNum,
          String.format("%1.2f", gasUsedPerc),
          reward.toHumanReadableString());
    }
  }

  @Override
  public Optional<BlockWithReceipts> retrieveBlockById(final PayloadIdentifier payloadId) {
    synchronized (blocksInProgress) {
      return retrievePayloadsById(payloadId)
          .map(payloadWrapper -> payloadWrapper.blockWithReceipts())
          .sorted(compareByGasUsedDesc)
          .findFirst();
    }
  }

  private Stream<PayloadWrapper> retrievePayloadsById(final PayloadIdentifier payloadId) {
    return blocksInProgress.stream().filter(z -> z.payloadIdentifier().equals(payloadId));
  }

  private String logBlockProposal(final Block block) {
    return "block "
        + block.toLogString()
        + " gas used "
        + block.getHeader().getGasUsed()
        + " transactions "
        + block.getBody().getTransactions().size();
  }

  @Override
  public void setIsChainPruningEnabled(final boolean isChainPruningEnabled) {
    this.isChainPruningEnabled = isChainPruningEnabled;
  }

  @Override
  public boolean isChainPruningEnabled() {
    return isChainPruningEnabled;
  }

  /**
   * Sets checkpoint post merge sync.
   *
   * @param isCheckpointPostMergeSync the is checkpoint post merge sync
   * @return the checkpoint post merge sync
   */
  public PostMergeContext setCheckpointPostMergeSync(final boolean isCheckpointPostMergeSync) {
    this.isCheckpointPostMergeSync = isCheckpointPostMergeSync;
    return this;
  }

  @Override
  public boolean isCheckpointPostMergeSync() {
    return this.isCheckpointPostMergeSync;
  }

  @Override
  public boolean isPostMergeAtGenesis() {
    return this.isPostMergeAtGenesis;
  }

  /**
   * Sets whether it is post merge at genesis
   *
   * @param isPostMergeAtGenesis the is post merge at genesis state
   * @return the post merge context
   */
  public PostMergeContext setPostMergeAtGenesis(final boolean isPostMergeAtGenesis) {
    this.isPostMergeAtGenesis = isPostMergeAtGenesis;
    return this;
  }
}
