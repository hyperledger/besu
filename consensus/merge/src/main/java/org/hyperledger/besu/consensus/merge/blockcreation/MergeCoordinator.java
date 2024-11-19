/*
 * Copyright contributors to Hyperledger Besu.
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

import static java.util.stream.Collectors.joining;
import static org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult.Status.INVALID;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.PayloadWrapper;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.chain.BadBlockCause;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BadChainListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Merge coordinator. */
public class MergeCoordinator implements MergeMiningCoordinator, BadChainListener {
  private static final Logger LOG = LoggerFactory.getLogger(MergeCoordinator.class);

  /**
   * On PoS you do not need to compete with other nodes for block production, since you have an
   * allocated slot for that, so in this case make sense to always try to fill the block, if there
   * are enough pending transactions, until the remaining gas is less than the minimum needed for
   * the smaller transaction. So for PoS the min-block-occupancy-ratio option is set to always try
   * to fill 100% of the block.
   */
  private static final double TRY_FILL_BLOCK = 1.0;

  private static final long DEFAULT_TARGET_GAS_LIMIT = 30000000L;

  /** The Mining parameters. */
  protected final MiningConfiguration miningConfiguration;

  /** The Merge block creator factory. */
  protected final MergeBlockCreatorFactory mergeBlockCreatorFactory;

  /** The Merge context. */
  protected final MergeContext mergeContext;

  /** The Protocol context. */
  protected final ProtocolContext protocolContext;

  /** The Block builder executor. */
  protected final EthScheduler ethScheduler;

  /** The Backward sync context. */
  protected final BackwardSyncContext backwardSyncContext;

  /** The Protocol schedule. */
  protected final ProtocolSchedule protocolSchedule;

  private final Map<PayloadIdentifier, BlockCreationTask> blockCreationTasks =
      new ConcurrentHashMap<>();

  /**
   * Instantiates a new Merge coordinator.
   *
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param ethScheduler the block builder executor
   * @param transactionPool the pending transactions
   * @param miningParams the mining params
   * @param backwardSyncContext the backward sync context
   * @param depositContractAddress the address of the deposit contract
   */
  public MergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthScheduler ethScheduler,
      final TransactionPool transactionPool,
      final MiningConfiguration miningParams,
      final BackwardSyncContext backwardSyncContext,
      final Optional<Address> depositContractAddress) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethScheduler = ethScheduler;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    this.backwardSyncContext = backwardSyncContext;

    if (miningParams.getCoinbase().isEmpty()) {
      miningParams.setCoinbase(Address.ZERO);
    }
    if (miningParams.getTargetGasLimit().isEmpty()) {
      miningParams.setTargetGasLimit(DEFAULT_TARGET_GAS_LIMIT);
    }
    miningParams.setMinBlockOccupancyRatio(TRY_FILL_BLOCK);

    this.miningConfiguration = miningParams;

    this.mergeBlockCreatorFactory =
        (parentHeader, address) -> {
          address.ifPresent(miningParams::setCoinbase);
          return new MergeBlockCreator(
              miningConfiguration,
              parent -> miningConfiguration.getExtraData(),
              transactionPool,
              protocolContext,
              protocolSchedule,
              parentHeader,
              ethScheduler);
        };

    this.backwardSyncContext.subscribeBadChainListener(this);
  }

  /**
   * Instantiates a new Merge coordinator.
   *
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param ethScheduler the block builder executor
   * @param miningParams the mining params
   * @param backwardSyncContext the backward sync context
   * @param mergeBlockCreatorFactory the merge block creator factory
   */
  public MergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final EthScheduler ethScheduler,
      final MiningConfiguration miningParams,
      final BackwardSyncContext backwardSyncContext,
      final MergeBlockCreatorFactory mergeBlockCreatorFactory) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethScheduler = ethScheduler;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    this.backwardSyncContext = backwardSyncContext;
    if (miningParams.getTargetGasLimit().isEmpty()) {
      miningParams.setTargetGasLimit(DEFAULT_TARGET_GAS_LIMIT);
    }
    miningParams.setMinBlockOccupancyRatio(TRY_FILL_BLOCK);
    this.miningConfiguration = miningParams;

    this.mergeBlockCreatorFactory = mergeBlockCreatorFactory;

    this.backwardSyncContext.subscribeBadChainListener(this);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void awaitStop() throws InterruptedException {}

  @Override
  public boolean enable() {
    return false;
  }

  @Override
  public boolean disable() {
    return true;
  }

  @Override
  public boolean isMining() {
    return true;
  }

  @Override
  public Wei getMinTransactionGasPrice() {
    return miningConfiguration.getMinTransactionGasPrice();
  }

  @Override
  public Wei getMinPriorityFeePerGas() {
    return miningConfiguration.getMinPriorityFeePerGas();
  }

  @Override
  public Optional<Address> getCoinbase() {
    return miningConfiguration.getCoinbase();
  }

  @Override
  public Optional<Block> createBlock(
      final BlockHeader parentHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    throw new UnsupportedOperationException("random is required");
  }

  @Override
  public Optional<Block> createBlock(final BlockHeader parentHeader, final long timestamp) {
    throw new UnsupportedOperationException("random is required");
  }

  @Override
  public void changeTargetGasLimit(final Long newTargetGasLimit) {
    if (AbstractGasLimitSpecification.isValidTargetGasLimit(newTargetGasLimit)) {
      this.miningConfiguration.setTargetGasLimit(newTargetGasLimit);
    } else {
      throw new IllegalArgumentException("Specified target gas limit is invalid");
    }
  }

  @Override
  public PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 prevRandao,
      final Address feeRecipient,
      final Optional<List<Withdrawal>> withdrawals,
      final Optional<Bytes32> parentBeaconBlockRoot) {

    // we assume that preparePayload is always called sequentially, since the RPC Engine calls
    // are sequential, if this assumption changes then more synchronization should be added to
    // shared data structures

    final PayloadIdentifier payloadIdentifier =
        PayloadIdentifier.forPayloadParams(
            parentHeader.getBlockHash(),
            timestamp,
            prevRandao,
            feeRecipient,
            withdrawals,
            parentBeaconBlockRoot);

    if (blockCreationTasks.containsKey(payloadIdentifier)) {
      LOG.debug(
          "Block proposal for the same payload id {} already present, nothing to do",
          payloadIdentifier);
      return payloadIdentifier;
    }
    // it's a new payloadId so...
    cancelAnyExistingBlockCreationTasks(payloadIdentifier);

    final MergeBlockCreator mergeBlockCreator =
        this.mergeBlockCreatorFactory.forParams(parentHeader, Optional.ofNullable(feeRecipient));

    blockCreationTasks.put(payloadIdentifier, new BlockCreationTask(mergeBlockCreator));

    // put the empty block in first
    final Block emptyBlock =
        mergeBlockCreator
            .createBlock(
                Optional.of(Collections.emptyList()),
                prevRandao,
                timestamp,
                withdrawals,
                parentBeaconBlockRoot,
                parentHeader)
            .getBlock();

    BlockProcessingResult result = validateProposedBlock(emptyBlock);
    if (result.isSuccessful()) {
      mergeContext.putPayloadById(
          new PayloadWrapper(
              payloadIdentifier,
              new BlockWithReceipts(emptyBlock, result.getReceipts()),
              result.getRequests()));
      LOG.info(
          "Start building proposals for block {} identified by {}",
          emptyBlock.getHeader().getNumber(),
          payloadIdentifier);
    } else {
      LOG.warn(
          "failed to validate empty block proposal {}, reason {}",
          emptyBlock.getHash(),
          result.errorMessage);
      if (result.causedBy().isPresent()) {
        LOG.warn("caused by", result.causedBy().get());
      }
    }

    tryToBuildBetterBlock(
        timestamp,
        prevRandao,
        payloadIdentifier,
        mergeBlockCreator,
        withdrawals,
        parentBeaconBlockRoot,
        parentHeader);

    return payloadIdentifier;
  }

  private void cancelAnyExistingBlockCreationTasks(final PayloadIdentifier payloadIdentifier) {
    if (blockCreationTasks.size() > 0) {
      String existingPayloadIdsBeingBuilt =
          blockCreationTasks.keySet().stream()
              .map(PayloadIdentifier::toHexString)
              .collect(joining(","));
      LOG.warn(
          "New payloadId {} received so cancelling block creation tasks for the following payloadIds: {}",
          payloadIdentifier,
          existingPayloadIdsBeingBuilt);

      blockCreationTasks.keySet().forEach(this::cleanupBlockCreationTask);
    }
  }

  private void cleanupBlockCreationTask(final PayloadIdentifier payloadIdentifier) {
    blockCreationTasks.computeIfPresent(
        payloadIdentifier,
        (pid, blockCreationTask) -> {
          blockCreationTask.cancel();
          return null;
        });
  }

  @Override
  public void finalizeProposalById(final PayloadIdentifier payloadId) {
    LOG.debug("Finalizing block proposal for payload id {}", payloadId);
    cleanupBlockCreationTask(payloadId);
  }

  private void tryToBuildBetterBlock(
      final Long timestamp,
      final Bytes32 random,
      final PayloadIdentifier payloadIdentifier,
      final MergeBlockCreator mergeBlockCreator,
      final Optional<List<Withdrawal>> withdrawals,
      final Optional<Bytes32> parentBeaconBlockRoot,
      final BlockHeader parentHeader) {

    final Supplier<BlockCreationResult> blockCreator =
        () ->
            mergeBlockCreator.createBlock(
                Optional.empty(),
                random,
                timestamp,
                withdrawals,
                parentBeaconBlockRoot,
                parentHeader);

    LOG.debug(
        "Block creation started for payload id {}, remaining time is {}ms",
        payloadIdentifier,
        miningConfiguration.getUnstable().getPosBlockCreationMaxTime());

    ethScheduler
        .scheduleBlockCreationTask(
            () -> retryBlockCreationUntilUseful(payloadIdentifier, blockCreator))
        .orTimeout(
            miningConfiguration.getUnstable().getPosBlockCreationMaxTime(), TimeUnit.MILLISECONDS)
        .whenComplete(
            (unused, throwable) -> {
              if (throwable != null) {
                LOG.atDebug()
                    .setMessage("Exception building block for payload id {}, reason {}")
                    .addArgument(payloadIdentifier)
                    .addArgument(() -> logException(throwable))
                    .log();
              }
              cleanupBlockCreationTask(payloadIdentifier);
            });
  }

  private Void retryBlockCreationUntilUseful(
      final PayloadIdentifier payloadIdentifier, final Supplier<BlockCreationResult> blockCreator) {

    long lastStartAt;

    while (!isBlockCreationCancelled(payloadIdentifier)) {
      try {
        lastStartAt = System.currentTimeMillis();
        recoverableBlockCreation(payloadIdentifier, blockCreator, lastStartAt);
        final long lastDuration = System.currentTimeMillis() - lastStartAt;
        final long waitBeforeRepetition =
            Math.max(
                100,
                miningConfiguration.getUnstable().getPosBlockCreationRepetitionMinDuration()
                    - lastDuration);
        LOG.debug("Waiting {}ms before repeating block creation", waitBeforeRepetition);
        Thread.sleep(waitBeforeRepetition);
      } catch (final CancellationException | InterruptedException ce) {
        LOG.atDebug()
            .setMessage("Block creation for payload id {} has been cancelled, reason {}")
            .addArgument(payloadIdentifier)
            .addArgument(() -> logException(ce))
            .log();
        return null;
      } catch (final Throwable e) {
        LOG.warn(
            "Something went wrong creating block for payload id {}, error {}",
            payloadIdentifier,
            logException(e));
        return null;
      }
    }
    return null;
  }

  private void recoverableBlockCreation(
      final PayloadIdentifier payloadIdentifier,
      final Supplier<BlockCreationResult> blockCreator,
      final long startedAt) {

    try {
      evaluateNewBlock(blockCreator.get().getBlock(), payloadIdentifier, startedAt);
    } catch (final Throwable throwable) {
      if (canRetryBlockCreation(throwable) && !isBlockCreationCancelled(payloadIdentifier)) {
        LOG.atDebug()
            .setMessage("Retrying block creation for payload id {} after recoverable error {}")
            .addArgument(payloadIdentifier)
            .addArgument(() -> logException(throwable))
            .log();
        recoverableBlockCreation(payloadIdentifier, blockCreator, startedAt);
      } else {
        throw throwable;
      }
    }
  }

  private void evaluateNewBlock(
      final Block bestBlock, final PayloadIdentifier payloadIdentifier, final long startedAt) {

    if (isBlockCreationCancelled(payloadIdentifier)) return;

    final var resultBest = validateProposedBlock(bestBlock);
    if (resultBest.isSuccessful()) {

      if (isBlockCreationCancelled(payloadIdentifier)) return;

      mergeContext.putPayloadById(
          new PayloadWrapper(
              payloadIdentifier,
              new BlockWithReceipts(bestBlock, resultBest.getReceipts()),
              resultBest.getRequests()));
      LOG.atDebug()
          .setMessage(
              "Successfully built block {} for proposal identified by {}, with {} transactions, in {}ms")
          .addArgument(bestBlock::toLogString)
          .addArgument(payloadIdentifier)
          .addArgument(bestBlock.getBody().getTransactions()::size)
          .addArgument(() -> System.currentTimeMillis() - startedAt)
          .log();
    } else {
      LOG.warn(
          "Block {} built for proposal identified by {}, is not valid reason {}",
          bestBlock.getHash(),
          payloadIdentifier.toString(),
          resultBest.errorMessage);
      if (resultBest.causedBy().isPresent()) {
        LOG.warn("caused by", resultBest.cause.get());
      }
    }
  }

  private boolean canRetryBlockCreation(final Throwable throwable) {
    if (throwable instanceof StorageException) {
      return true;
    } else if (throwable instanceof MerkleTrieException) {
      return true;
    }
    return false;
  }

  @Override
  public Optional<BlockHeader> getOrSyncHeadByHash(final Hash headHash, final Hash finalizedHash) {
    final var chain = protocolContext.getBlockchain();
    final var maybeHeadHeader = chain.getBlockHeader(headHash);

    if (maybeHeadHeader.isPresent()) {
      LOG.atDebug()
          .setMessage("BlockHeader {} is already present in blockchain")
          .addArgument(maybeHeadHeader.get()::toLogString)
          .log();
    } else {
      backwardSyncContext.maybeUpdateTargetHeight(headHash);
      backwardSyncContext
          .syncBackwardsUntil(headHash)
          .thenRun(() -> updateFinalized(finalizedHash));
    }
    return maybeHeadHeader;
  }

  private void updateFinalized(final Hash finalizedHash) {
    if (mergeContext
        .getFinalized()
        .map(BlockHeader::getHash)
        .map(finalizedHash::equals)
        .orElse(Boolean.FALSE)) {
      LOG.atDebug()
          .setMessage("Finalized block already set to {}, nothing to do")
          .addArgument(finalizedHash)
          .log();
      return;
    }

    protocolContext
        .getBlockchain()
        .getBlockHeader(finalizedHash)
        .ifPresentOrElse(
            finalizedHeader -> {
              LOG.atDebug()
                  .setMessage("Setting finalized block header to {}")
                  .addArgument(finalizedHeader::toLogString)
                  .log();
              mergeContext.setFinalized(finalizedHeader);
            },
            () ->
                LOG.warn(
                    "Internal error, backward sync completed but failed to import finalized block {}",
                    finalizedHash));
  }

  @Override
  public BlockProcessingResult validateBlock(final Block block) {
    final var validationResult =
        protocolSchedule
            .getByBlockHeader(block.getHeader())
            .getBlockValidator()
            .validateAndProcessBlock(
                protocolContext,
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE,
                false);

    return validationResult;
  }

  private BlockProcessingResult validateProposedBlock(final Block block) {
    final var validationResult =
        protocolSchedule
            .getByBlockHeader(block.getHeader())
            .getBlockValidator()
            .validateAndProcessBlock(
                protocolContext,
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE,
                false,
                false);

    return validationResult;
  }

  @Override
  public BlockProcessingResult rememberBlock(final Block block) {
    LOG.atDebug().setMessage("Remember block {}").addArgument(block::toLogString).log();
    final var chain = protocolContext.getBlockchain();
    final var validationResult = validateBlock(block);
    validationResult
        .getYield()
        .ifPresentOrElse(
            result -> chain.storeBlock(block, result.getReceipts()),
            () -> LOG.debug("empty yield in blockProcessingResult"));
    return validationResult;
  }

  @Override
  public ForkchoiceResult updateForkChoice(
      final BlockHeader newHead, final Hash finalizedBlockHash, final Hash safeBlockHash) {
    MutableBlockchain blockchain = protocolContext.getBlockchain();
    final Optional<BlockHeader> newFinalized = blockchain.getBlockHeader(finalizedBlockHash);

    if (newHead.getNumber() < blockchain.getChainHeadBlockNumber()
        && isDescendantOf(newHead, blockchain.getChainHeadHeader())) {
      LOG.atDebug()
          .setMessage("Ignoring update to old head {}")
          .addArgument(newHead::toLogString)
          .log();
      return ForkchoiceResult.withIgnoreUpdateToOldHead(newHead);
    }

    final Optional<Hash> latestValid = getLatestValidAncestor(newHead);

    Optional<BlockHeader> parentOfNewHead = blockchain.getBlockHeader(newHead.getParentHash());
    if (parentOfNewHead.isPresent()
        && Long.compareUnsigned(newHead.getTimestamp(), parentOfNewHead.get().getTimestamp())
            <= 0) {
      return ForkchoiceResult.withFailure(
          INVALID, "new head timestamp not greater than parent", latestValid);
    }

    setNewHead(blockchain, newHead);

    // set and persist the new finalized block if it is present
    newFinalized.ifPresent(
        blockHeader -> {
          blockchain.setFinalized(blockHeader.getHash());
          mergeContext.setFinalized(blockHeader);
        });

    blockchain
        .getBlockHeader(safeBlockHash)
        .ifPresent(
            newSafeBlock -> {
              blockchain.setSafeBlock(safeBlockHash);
              mergeContext.setSafeBlock(newSafeBlock);
            });

    return ForkchoiceResult.withResult(newFinalized, Optional.of(newHead));
  }

  private boolean setNewHead(final MutableBlockchain blockchain, final BlockHeader newHead) {

    if (newHead.getHash().equals(blockchain.getChainHeadHash())) {
      LOG.atDebug()
          .setMessage("Nothing to do new head {} is already chain head")
          .addArgument(newHead::toLogString)
          .log();
      return true;
    }

    if (moveWorldStateTo(newHead)) {
      if (newHead.getParentHash().equals(blockchain.getChainHeadHash())) {
        LOG.atDebug()
            .setMessage(
                "Forwarding chain head to the block {} saved from a previous newPayload invocation")
            .addArgument(newHead::toLogString)
            .log();
        return blockchain.forwardToBlock(newHead);
      } else {
        LOG.atDebug()
            .setMessage("New head {} is a chain reorg, rewind chain head to it")
            .addArgument(newHead::toLogString)
            .log();
        return blockchain.rewindToBlock(newHead.getHash());
      }
    }
    LOG.atDebug()
        .setMessage("Failed to move the worldstate forward to hash {}, not moving chain head")
        .addArgument(newHead::toLogString)
        .log();
    return false;
  }

  private boolean moveWorldStateTo(final BlockHeader newHead) {
    Optional<MutableWorldState> newWorldState =
        protocolContext
            .getWorldStateArchive()
            .getMutable(newHead.getStateRoot(), newHead.getHash());

    newWorldState.ifPresentOrElse(
        mutableWorldState ->
            LOG.atDebug()
                .setMessage(
                    "World state for state root hash {} and block hash {} persisted successfully")
                .addArgument(mutableWorldState::rootHash)
                .addArgument(newHead::getHash)
                .log(),
        () ->
            LOG.error(
                "Could not persist world for root hash {} and block hash {}",
                newHead.getStateRoot(),
                newHead.getHash()));
    return newWorldState.isPresent();
  }

  @Override
  public Optional<Hash> getLatestValidAncestor(final Hash blockHash) {
    final var chain = protocolContext.getBlockchain();
    return findValidAncestor(chain, blockHash);
  }

  @Override
  public Optional<Hash> getLatestValidAncestor(final BlockHeader blockHeader) {
    final var chain = protocolContext.getBlockchain();
    final var self = chain.getBlockHeader(blockHeader.getHash());

    if (self.isEmpty()) {
      return findValidAncestor(chain, blockHeader.getParentHash());
    }
    return self.map(BlockHeader::getHash);
  }

  @Override
  public boolean isBackwardSyncing() {
    return backwardSyncContext.isSyncing();
  }

  @Override
  public CompletableFuture<Void> appendNewPayloadToSync(final Block newPayload) {
    return backwardSyncContext.syncBackwardsUntil(newPayload);
  }

  @Override
  public boolean isMiningBeforeMerge() {
    return miningConfiguration.isMiningEnabled();
  }

  private Optional<Hash> findValidAncestor(final Blockchain chain, final Hash parentHash) {

    // check chain first
    return chain
        .getBlockHeader(parentHash)
        .map(
            header -> {
              // if block is PoW, return ZERO hash
              if (header.getDifficulty().greaterThan(Difficulty.ZERO)) {
                return Hash.ZERO;
              } else {
                return header.getHash();
              }
            })
        .map(Optional::of)
        .orElseGet(
            () ->
                protocolContext
                    .getBadBlockManager()
                    .getBadBlock(parentHash)
                    .map(
                        badParent ->
                            findValidAncestor(chain, badParent.getHeader().getParentHash()))
                    .orElse(Optional.empty()));
  }

  @Override
  public boolean isDescendantOf(final BlockHeader ancestorBlock, final BlockHeader newBlock) {
    LOG.atDebug()
        .setMessage("checking if block {} is ancestor of {}")
        .addArgument(ancestorBlock::toLogString)
        .addArgument(newBlock::toLogString)
        .log();

    // start with self, because descending from yourself is valid
    Optional<BlockHeader> parentOf = Optional.of(newBlock);

    while (parentOf.isPresent()
        && !parentOf.get().getBlockHash().equals(ancestorBlock.getBlockHash())
        && parentOf.get().getNumber()
            >= ancestorBlock.getNumber()) { // if on a fork, don't go further back than ancestor
      parentOf = protocolContext.getBlockchain().getBlockHeader(parentOf.get().getParentHash());
    }

    if (parentOf.isPresent()
        && ancestorBlock.getBlockHash().equals(parentOf.get().getBlockHash())) {
      return true;
    } else {
      LOG.atDebug()
          .setMessage("looped all the way back, did not find ancestor {} of child {}")
          .addArgument(ancestorBlock::toLogString)
          .addArgument(newBlock::toLogString)
          .log();
      return false;
    }
  }

  @Override
  public void onBadChain(
      final Block badBlock,
      final List<Block> badBlockDescendants,
      final List<BlockHeader> badBlockHeaderDescendants) {
    LOG.trace("Mark descendents of bad block {} as bad", badBlock.getHash());
    final BadBlockManager badBlockManager = protocolContext.getBadBlockManager();

    final Optional<BlockHeader> parentHeader =
        protocolContext.getBlockchain().getBlockHeader(badBlock.getHeader().getParentHash());
    final Optional<Hash> maybeLatestValidHash =
        parentHeader.isPresent() && isPoSHeader(parentHeader.get())
            ? Optional.of(parentHeader.get().getHash())
            : Optional.empty();

    // Bad block has already been marked, but we need to mark the bad block's descendants
    badBlockDescendants.forEach(
        block -> {
          LOG.trace("Add descendant block {} to bad blocks", block.getHash());
          badBlockManager.addBadBlock(block, BadBlockCause.fromBadAncestorBlock(badBlock));
          maybeLatestValidHash.ifPresent(
              latestValidHash ->
                  badBlockManager.addLatestValidHash(block.getHash(), latestValidHash));
        });

    badBlockHeaderDescendants.forEach(
        header -> {
          LOG.trace("Add descendant header {} to bad blocks", header.getHash());
          badBlockManager.addBadHeader(header, BadBlockCause.fromBadAncestorBlock(badBlock));
          maybeLatestValidHash.ifPresent(
              latestValidHash ->
                  badBlockManager.addLatestValidHash(header.getHash(), latestValidHash));
        });
  }

  /**
   * returns the instance of ethScheduler
   *
   * @return get the Eth scheduler
   */
  @Override
  public EthScheduler getEthScheduler() {
    return ethScheduler;
  }

  /** The interface Merge block creator factory. */
  @FunctionalInterface
  protected interface MergeBlockCreatorFactory {
    /**
     * Create merge block creator for block header and fee recipient.
     *
     * @param header the header
     * @param feeRecipient the fee recipient
     * @return the merge block creator
     */
    MergeBlockCreator forParams(BlockHeader header, Optional<Address> feeRecipient);
  }

  @Override
  public boolean isBadBlock(final Hash blockHash) {
    final BadBlockManager badBlockManager = protocolContext.getBadBlockManager();
    return badBlockManager.isBadBlock(blockHash);
  }

  @Override
  public Optional<Hash> getLatestValidHashOfBadBlock(Hash blockHash) {
    return protocolContext.getBadBlockManager().getLatestValidHash(blockHash);
  }

  private boolean isPoSHeader(final BlockHeader header) {
    return header.getDifficulty().equals(Difficulty.ZERO);
  }

  private String logException(final Throwable throwable) {
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  @VisibleForTesting
  boolean isBlockCreationCancelled(final PayloadIdentifier payloadId) {
    final BlockCreationTask job = blockCreationTasks.get(payloadId);
    if (job == null) {
      return true;
    }
    return job.cancelled.get();
  }

  private static class BlockCreationTask {
    /** The Block creator. */
    final MergeBlockCreator blockCreator;

    /** The Cancelled. */
    final AtomicBoolean cancelled;

    /**
     * Instantiates a new Block creation task.
     *
     * @param blockCreator the block creator
     */
    public BlockCreationTask(final MergeBlockCreator blockCreator) {
      this.blockCreator = blockCreator;
      this.cancelled = new AtomicBoolean(false);
    }

    /** Cancel. */
    public void cancel() {
      cancelled.set(true);
      blockCreator.cancel();
    }
  }
}
