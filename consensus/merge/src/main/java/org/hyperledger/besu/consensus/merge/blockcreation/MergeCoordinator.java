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

import static org.hyperledger.besu.consensus.merge.TransitionUtils.isTerminalProofOfWorkBlock;
import static org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult.Status.INVALID;
import static org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator.ForkchoiceResult.Status.INVALID_PAYLOAD_ATTRIBUTES;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeCoordinator implements MergeMiningCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(MergeCoordinator.class);

  final AtomicLong targetGasLimit;
  final MiningParameters miningParameters;
  final MergeBlockCreatorFactory mergeBlockCreator;
  final AtomicReference<Bytes> extraData = new AtomicReference<>(Bytes.fromHexString("0x"));
  final AtomicReference<BlockHeader> latestDescendsFromTerminal = new AtomicReference<>();
  private final MergeContext mergeContext;
  private final ProtocolContext protocolContext;
  private final BackwardSyncContext backwardSyncContext;
  private final ProtocolSchedule protocolSchedule;

  public MergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final MiningParameters miningParams,
      final BackwardSyncContext backwardSyncContext) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    this.miningParameters = miningParams;
    this.backwardSyncContext = backwardSyncContext;
    this.targetGasLimit =
        miningParameters
            .getTargetGasLimit()
            // TODO: revisit default target gas limit
            .orElse(new AtomicLong(30000000L));

    this.mergeBlockCreator =
        (parentHeader, address) ->
            new MergeBlockCreator(
                address.or(miningParameters::getCoinbase).orElse(Address.ZERO),
                () -> Optional.of(targetGasLimit.longValue()),
                parent -> extraData.get(),
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                this.miningParameters.getMinTransactionGasPrice(),
                address.or(miningParameters::getCoinbase).orElse(Address.ZERO),
                this.miningParameters.getMinBlockOccupancyRatio(),
                parentHeader);
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
    return miningParameters.getMinTransactionGasPrice();
  }

  @Override
  public void setExtraData(final Bytes extraData) {
    this.extraData.set(extraData);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return miningParameters.getCoinbase();
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
      this.targetGasLimit.set(newTargetGasLimit);
    } else {
      throw new IllegalArgumentException("Specified target gas limit is invalid");
    }
  }

  @Override
  public PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 random,
      final Address feeRecipient) {

    final PayloadIdentifier payloadIdentifier =
        PayloadIdentifier.forPayloadParams(parentHeader.getBlockHash(), timestamp);
    final MergeBlockCreator mergeBlockCreator =
        this.mergeBlockCreator.forParams(parentHeader, Optional.ofNullable(feeRecipient));

    // put the empty block in first
    final Block emptyBlock =
        mergeBlockCreator.createBlock(Optional.of(Collections.emptyList()), random, timestamp);

    Result result = validateBlock(emptyBlock);
    if (result.blockProcessingOutputs.isPresent()) {
      mergeContext.putPayloadById(payloadIdentifier, emptyBlock);
    } else {
      LOG.warn(
          "failed to execute empty block proposal {}, reason {}",
          emptyBlock.getHash(),
          result.errorMessage);
    }

    // start working on a full block and update the payload value and candidate when it's ready
    CompletableFuture.supplyAsync(
            () -> mergeBlockCreator.createBlock(Optional.empty(), random, timestamp))
        .orTimeout(12, TimeUnit.SECONDS)
        .whenComplete(
            (bestBlock, throwable) -> {
              if (throwable != null) {
                LOG.warn("something went wrong creating block", throwable);
              } else {
                final var resultBest = validateBlock(bestBlock);
                if (resultBest.blockProcessingOutputs.isPresent()) {
                  mergeContext.putPayloadById(payloadIdentifier, bestBlock);
                } else {
                  LOG.warn(
                      "failed to execute block proposal {}, reason {}",
                      bestBlock.getHash(),
                      resultBest.errorMessage);
                }
              }
            });

    return payloadIdentifier;
  }

  @Override
  public Optional<BlockHeader> getOrSyncHeaderByHash(final Hash blockHash) {
    final var chain = protocolContext.getBlockchain();
    final var optHeader = chain.getBlockHeader(blockHash);

    if (optHeader.isPresent()) {
      debugLambda(LOG, "BlockHeader {} is already present", () -> optHeader.get().toLogString());
    } else {
      debugLambda(LOG, "appending block hash {} to backward sync", blockHash::toHexString);
      backwardSyncContext.syncBackwardsUntil(blockHash);
    }
    return optHeader;
  }

  @Override
  public Optional<BlockHeader> getOrSyncHeaderByHash(
      final Hash blockHash, final Hash finalizedBlockHash) {
    final var chain = protocolContext.getBlockchain();
    final var optHeader = chain.getBlockHeader(blockHash);

    if (optHeader.isPresent()) {
      debugLambda(LOG, "BlockHeader {} is already present", () -> optHeader.get().toLogString());
    } else {
      debugLambda(LOG, "appending block hash {} to backward sync", blockHash::toHexString);
      backwardSyncContext.updateHeads(blockHash, finalizedBlockHash);
      backwardSyncContext.syncBackwardsUntil(blockHash);
    }
    return optHeader;
  }

  @Override
  public Result validateBlock(final Block block) {

    final var chain = protocolContext.getBlockchain();
    chain
        .getBlockHeader(block.getHeader().getParentHash())
        .ifPresentOrElse(
            blockHeader ->
                debugLambda(LOG, "Parent of block {} is already present", block::toLogString),
            () -> backwardSyncContext.syncBackwardsUntil(block));

    final var validationResult =
        protocolSchedule
            .getByBlockNumber(block.getHeader().getNumber())
            .getBlockValidator()
            .validateAndProcessBlock(
                protocolContext, block, HeaderValidationMode.FULL, HeaderValidationMode.NONE);

    return validationResult;
  }

  @Override
  public Result executeBlock(final Block block) {
    return executeBlockWithoutSaving(block, true);
  }

  @Override
  public Result executeBlockWithoutSaving(final Block block) {
    return executeBlockWithoutSaving(block, false);
  }

  public Result executeBlockWithoutSaving(final Block block, final boolean shouldSave) {

    final var chain = protocolContext.getBlockchain();
    chain
        .getBlockHeader(block.getHeader().getParentHash())
        .ifPresentOrElse(
            blockHeader ->
                debugLambda(LOG, "Parent of block {} is already present", block::toLogString),
            () -> backwardSyncContext.syncBackwardsUntil(block));

    final var validationResult =
        protocolSchedule
            .getByBlockNumber(block.getHeader().getNumber())
            .getBlockValidator()
            .validateAndProcessBlock(
                protocolContext,
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE,
                shouldSave);

    validationResult.blockProcessingOutputs.ifPresentOrElse(
        result -> {
          if (shouldSave) {
            chain.appendBlock(block, result.receipts);
          }
        },
        () ->
            protocolSchedule
                .getByBlockNumber(chain.getChainHeadBlockNumber())
                .getBadBlocksManager()
                .addBadBlock(block));

    return validationResult;
  }

  @Override
  public ForkchoiceResult updateForkChoice(
      final BlockHeader newHead,
      final Hash finalizedBlockHash,
      final Hash safeBlockHash,
      final Optional<PayloadAttributes> maybePayloadAttributes) {
    MutableBlockchain blockchain = protocolContext.getBlockchain();
    Optional<BlockHeader> currentFinalized = mergeContext.getFinalized();
    final Optional<BlockHeader> newFinalized = blockchain.getBlockHeader(finalizedBlockHash);

    final Optional<Hash> latestValid = getLatestValidAncestor(newHead);

    if (currentFinalized.isPresent()
        && newFinalized.isPresent()
        && !isDescendantOf(currentFinalized.get(), newFinalized.get())) {
      return ForkchoiceResult.withFailure(
          INVALID,
          String.format(
              "new finalized block %s is not a descendant of current finalized block %s",
              finalizedBlockHash, currentFinalized.get().getBlockHash()),
          latestValid);
    }

    // ensure new head is descendant of finalized
    Optional<String> descendantError =
        newFinalized
            .map(Optional::of)
            .orElse(currentFinalized)
            .filter(finalized -> !isDescendantOf(finalized, newHead))
            .map(
                finalized ->
                    String.format(
                        "new head block %s is not a descendant of current finalized block %s",
                        newHead.getBlockHash(), finalized.getBlockHash()));

    if (descendantError.isPresent()) {
      return ForkchoiceResult.withFailure(INVALID, descendantError.get(), latestValid);
    }

    final Optional<BlockHeader> parentOfNewHead =
        blockchain.getBlockHeader(newHead.getParentHash());

    if (parentOfNewHead.isPresent()
        && parentOfNewHead.get().getTimestamp() >= newHead.getTimestamp()) {
      return ForkchoiceResult.withFailure(
          INVALID, "new head timestamp not greater than parent", latestValid);
    }

    setNewHead(blockchain, newHead, newHead.getParentHash());

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

    if (maybePayloadAttributes.isPresent()
        && !isPayloadAttributesValid(maybePayloadAttributes.get(), newHead)) {
      return ForkchoiceResult.withFailure(INVALID_PAYLOAD_ATTRIBUTES, null, Optional.empty());
    }

    return ForkchoiceResult.withResult(newFinalized, Optional.of(newHead));
  }

  private boolean setNewHead(
      final MutableBlockchain blockchain, final BlockHeader newHead, final Hash parentHash) {

    if (newHead.getHash().equals(blockchain.getChainHeadHash())) {
      debugLambda(LOG, "Nothing to do new head {} is already chain head", newHead::toLogString);
      return true;
    }

    if (parentHash.equals(blockchain.getChainHeadHash())) {
      debugLambda(
          LOG,
          "Forwarding chain head to the block {} saved from a previous newPayload invocation",
          newHead::toLogString);
      return blockchain.forwardToBlock(newHead);
    }

    debugLambda(LOG, "New head {} is a chain reorg, rewind chain head to it", newHead::toLogString);
    return blockchain.rewindToBlock(newHead.getHash());
  }

  @Override
  public boolean latestValidAncestorDescendsFromTerminal(final BlockHeader blockHeader) {
    if (blockHeader.getNumber() <= 1L) {
      // parent is a genesis block, check for merge-at-genesis
      var blockchain = protocolContext.getBlockchain();

      return blockchain
          .getTotalDifficultyByHash(blockHeader.getBlockHash())
          .map(Optional::of)
          .orElse(blockchain.getTotalDifficultyByHash(blockHeader.getParentHash()))
          .filter(
              currDiff -> currDiff.greaterOrEqualThan(mergeContext.getTerminalTotalDifficulty()))
          .isPresent();
    }

    Optional<Hash> validAncestorHash = this.getLatestValidAncestor(blockHeader);
    if (validAncestorHash.isPresent()) {
      final Optional<BlockHeader> maybeFinalized = mergeContext.getFinalized();
      if (maybeFinalized.isPresent()) {
        return isDescendantOf(maybeFinalized.get(), blockHeader);
      } else {
        Optional<BlockHeader> terminalBlockHeader = mergeContext.getTerminalPoWBlock();
        if (terminalBlockHeader.isPresent()) {
          return isDescendantOf(terminalBlockHeader.get(), blockHeader);
        } else {
          if (isTerminalProofOfWorkBlock(blockHeader, protocolContext)
              || ancestorIsValidTerminalProofOfWork(blockHeader)) {
            return true;
          } else {
            LOG.warn("Couldn't find terminal block, no blocks will be valid");
            return false;
          }
        }
      }
    } else {
      return false;
    }
  }

  // package visibility for testing
  boolean ancestorIsValidTerminalProofOfWork(final BlockHeader blockheader) {
    // this should only happen very close to the transition from PoW to PoS, prior to a finalized
    // block.  For example, after a full sync of an already-merged chain which does not have
    // terminal block info in the genesis config.

    // check a 'cached' block which was determined to descend from terminal to short circuit
    // in the case of a long period of non-finality
    if (Optional.ofNullable(latestDescendsFromTerminal.get())
        .map(latestDescendant -> isDescendantOf(latestDescendant, blockheader))
        .orElse(Boolean.FALSE)) {
      latestDescendsFromTerminal.set(blockheader);
      return true;
    }

    var blockchain = protocolContext.getBlockchain();
    Optional<BlockHeader> parent = blockchain.getBlockHeader(blockheader.getParentHash());
    do {
      LOG.debug(
          "checking ancestor {} is valid terminal PoW for {}",
          parent.map(BlockHeader::toLogString).orElse("empty"),
          blockheader.toLogString());

      if (parent.isPresent()) {
        if (!parent.get().getDifficulty().equals(Difficulty.ZERO)) {
          break;
        }
        parent = blockchain.getBlockHeader(parent.get().getParentHash());
      }

    } while (parent.isPresent());

    boolean resp =
        parent.filter(header -> isTerminalProofOfWorkBlock(header, protocolContext)).isPresent();
    LOG.warn(
        "checking ancestor {} is valid terminal PoW for {}\n {}",
        parent.map(BlockHeader::toLogString).orElse("empty"),
        blockheader.toLogString(),
        resp);
    if (resp) {
      latestDescendsFromTerminal.set(blockheader);
    }
    return resp;
  }

  @Override
  public Optional<Hash> getLatestValidAncestor(final Hash blockHash) {
    final var chain = protocolContext.getBlockchain();
    final var chainHeadNum = chain.getChainHeadBlockNumber();
    return findValidAncestor(
        chain, blockHash, protocolSchedule.getByBlockNumber(chainHeadNum).getBadBlocksManager());
  }

  @Override
  public Optional<Hash> getLatestValidAncestor(final BlockHeader blockHeader) {
    final var chain = protocolContext.getBlockchain();
    final var self = chain.getBlockHeader(blockHeader.getHash());

    if (self.isEmpty()) {
      final var badBlocks =
          protocolSchedule.getByBlockNumber(blockHeader.getNumber()).getBadBlocksManager();
      return findValidAncestor(chain, blockHeader.getParentHash(), badBlocks);
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
    return miningParameters.isMiningEnabled();
  }

  private Optional<Hash> findValidAncestor(
      final Blockchain chain, final Hash parentHash, final BadBlockManager badBlocks) {

    // check chain first
    return chain
        .getBlockHeader(parentHash)
        .map(BlockHeader::getHash)
        .map(Optional::of)
        .orElseGet(
            () ->
                badBlocks
                    .getBadBlock(parentHash)
                    .map(
                        badParent ->
                            findValidAncestor(
                                chain, badParent.getHeader().getParentHash(), badBlocks))
                    .orElse(Optional.empty()));
  }

  @Override
  public boolean isDescendantOf(final BlockHeader ancestorBlock, final BlockHeader newBlock) {
    LOG.debug(
        "checking if block {}:{} is ancestor of {}:{}",
        ancestorBlock.getNumber(),
        ancestorBlock.getBlockHash(),
        newBlock.getNumber(),
        newBlock.getBlockHash());

    if (ancestorBlock.getBlockHash().equals(newBlock.getHash())) {
      return true;
    } else if (ancestorBlock.getNumber() < newBlock.getNumber()) {
      return protocolContext
          .getBlockchain()
          .getBlockHeader(newBlock.getParentHash())
          .map(parent -> isDescendantOf(ancestorBlock, parent))
          .orElse(Boolean.FALSE);
    }
    // neither matching nor is the ancestor block height lower than newBlock
    return false;
  }

  private boolean isPayloadAttributesValid(
      final PayloadAttributes payloadAttributes, final BlockHeader headBlockHeader) {
    return payloadAttributes.getTimestamp() > headBlockHeader.getTimestamp();
  }

  @FunctionalInterface
  interface MergeBlockCreatorFactory {
    MergeBlockCreator forParams(BlockHeader header, Optional<Address> feeRecipient);
  }
}
