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

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardsSyncContext;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MergeCoordinator implements MergeMiningCoordinator {
  private static final Logger LOG = LogManager.getLogger();

  final AtomicLong targetGasLimit;
  final MiningParameters miningParameters;
  final MergeBlockCreatorFactory mergeBlockCreator;
  final AtomicReference<Bytes> extraData = new AtomicReference<>(Bytes.fromHexString("0x"));
  private final MergeContext mergeContext;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;
  private final BackwardsSyncContext backwardsSyncContext;
  private final ProtocolSchedule protocolSchedule;

  public MergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final MiningParameters miningParams,
      final BlockValidator blockValidator,
      final BackwardsSyncContext backwardsSyncContext) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.blockValidator = blockValidator;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    this.miningParameters = miningParams;
    this.backwardsSyncContext = backwardsSyncContext;
    this.targetGasLimit =
        miningParameters
            .getTargetGasLimit()
            // TODO: revisit default target gas limit
            .orElse(new AtomicLong(30000000L));

    this.mergeBlockCreator =
        (parentHeader, random, address) ->
            new MergeBlockCreator(
                address.or(miningParameters::getCoinbase).orElse(Address.ZERO),
                () -> Optional.of(targetGasLimit.longValue()),
                parent -> extraData.get(),
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                this.miningParameters.getMinTransactionGasPrice(),
                this.miningParameters.getCoinbase().orElse(Address.ZERO),
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
        this.mergeBlockCreator.forParams(parentHeader, random, Optional.ofNullable(feeRecipient));

    // put the empty block in first
    final Block emptyBlock =
        mergeBlockCreator.createBlock(Optional.of(Collections.emptyList()), random, timestamp);
    executeBlock(emptyBlock);
    mergeContext.putPayloadById(payloadIdentifier, emptyBlock);

    // start working on a full block and update the payload value and candidate when it's ready
    CompletableFuture.supplyAsync(
            () -> mergeBlockCreator.createBlock(Optional.empty(), random, timestamp))
        .orTimeout(12, TimeUnit.SECONDS)
        .whenComplete(
            (bestBlock, throwable) -> {
              if (throwable != null) {
                LOG.warn("something went wrong creating block", throwable);
              } else {
                executeBlock(bestBlock);
                mergeContext.putPayloadById(payloadIdentifier, bestBlock);
              }
            });

    return payloadIdentifier;
  }

  @Override
  public boolean executeBlock(final Block block) {
    // TODO: if we are missing the parentHash, attempt backwards sync
    // https://github.com/hyperledger/besu/issues/2912

    protocolContext
        .getBlockchain()
        .getBlockHeader(block.getHeader().getParentHash())
        .ifPresentOrElse(
            blockHeader ->
                LOG.debug(
                    "Parent of block {} is already present",
                    block.getHash().toString().substring(0, 20)),
            () -> backwardsSyncContext.syncBackwardsUntil(block));

    // TODO: End Jiri

    final var chain = protocolContext.getBlockchain();
    var optResult =
        blockValidator.validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.FULL, HeaderValidationMode.NONE);

    optResult.ifPresent(
        result -> {
          result.worldState.persist(block.getHeader());
          chain.appendBlock(block, result.receipts);
        });

    if (!optResult.isPresent()) {
      protocolSchedule
          .getByBlockNumber(chain.getChainHeadBlockNumber())
          .getBadBlocksManager()
          .addBadBlock(block);
    }

    return optResult.isPresent();
  }

  @Override
  public void updateForkChoice(final Hash headBlockHash, final Hash finalizedBlockHash) {
    MutableBlockchain blockchain = protocolContext.getBlockchain();
    Optional<BlockHeader> currentFinalized = mergeContext.getFinalized();

    final Optional<BlockHeader> newFinalized = blockchain.getBlockHeader(finalizedBlockHash);
    if (newFinalized.isEmpty() && !finalizedBlockHash.equals(Hash.ZERO)) {
      // we should only fail to find when it's the special value 0x000..000
      throw new IllegalStateException(
          String.format(
              "should've been able to find block hash %s but couldn't", finalizedBlockHash));
    }

    if (currentFinalized.isPresent()
        && newFinalized.isPresent()
        && !isDescendantOf(currentFinalized.get(), newFinalized.get())) {
      throw new IllegalStateException(
          String.format(
              "new finalized block %s is not a descendant of current finalized block %s",
              finalizedBlockHash, currentFinalized.get().getBlockHash()));
    }

    // ensure we have headBlock:
    BlockHeader newHead =
        blockchain
            .getBlockHeader(headBlockHash)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format("not able to find new head block %s", headBlockHash)));

    // ensure new head is descendant of finalized
    newFinalized
        .map(Optional::of)
        .orElse(currentFinalized)
        .ifPresent(
            finalized -> {
              if (!isDescendantOf(finalized, newHead)) {
                throw new IllegalStateException(
                    String.format(
                        "new head block %s is not a descendant of current finalized block %s",
                        newHead.getBlockHash(), finalized.getBlockHash()));
              }
              ;
            });

    // set the new head
    blockchain.rewindToBlock(newHead.getHash());

    // set the new finalized block if it present
    newFinalized.ifPresent(mergeContext::setFinalized);
  }

  @Override
  public Optional<Hash> getLatestValidAncestor(final Block block) {
    final var chain = protocolContext.getBlockchain();
    final var self = chain.getBlockHeader(block.getHash());

    if (self.isEmpty()) {
      final var badBlocks =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber()).getBadBlocksManager();
      return findValidAncestor(chain, block.getHeader().getParentHash(), badBlocks);
    }
    return self.map(BlockHeader::getHash);
  }

  private Optional<Hash> findValidAncestor(
      final Blockchain chain, final Hash parentHash, final BadBlockManager badBlocks) {

    // check chain first
    final var parent =
        chain
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

    if (parent.isEmpty()) {
      // TODO: start a backward sync for parentHash if parent is not available
      // https://github.com/hyperledger/besu/issues/2912
    }

    return parent;
  }

  private boolean isDescendantOf(final BlockHeader ancestorBlock, final BlockHeader newBlock) {
    LOG.debug(
        "checking if finalized block {}:{} is ancestor of {}:{}",
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

  @FunctionalInterface
  interface MergeBlockCreatorFactory {
    MergeBlockCreator forParams(BlockHeader header, Bytes32 random, Optional<Address> feeRecipient);
  }
}
