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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
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
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MergeCoordinator implements MiningCoordinator {
  private static final Logger LOG = LogManager.getLogger();

  final AtomicLong targetGasLimit;
  final MiningParameters miningParameters;
  final BiFunction<BlockHeader, Bytes32, MergeBlockCreator> mergeBlockCreator;
  final AtomicReference<Bytes> extraData = new AtomicReference<>(Bytes.fromHexString("0x"));
  private final MergeContext mergeContext;
  private final BlockValidator blockValidator;
  private final ProtocolContext protocolContext;

  public MergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final MiningParameters miningParams,
      final BlockValidator blockValidator) {
    this.protocolContext = protocolContext;
    this.blockValidator = blockValidator;
    this.mergeContext = protocolContext.getConsensusContext(MergeContext.class);
    this.miningParameters = miningParams;
    this.targetGasLimit =
        miningParameters
            .getTargetGasLimit()
            // TODO: revisit default target gas limit
            .orElse(new AtomicLong(30000000L));

    this.mergeBlockCreator =
        (parentHeader, random) ->
            new MergeBlockCreator(
                this.miningParameters.getCoinbase().orElse(Address.ZERO),
                () -> Optional.of(targetGasLimit.longValue()),
                parent -> extraData.get(),
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                this.miningParameters.getMinTransactionGasPrice(),
                this.miningParameters.getCoinbase().orElse(Address.ZERO),
                random,
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

  public PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 random,
      final Address feeRecipient) {
    // todo feeRecipient
    final PayloadIdentifier payloadIdentifier = PayloadIdentifier.random();
    final MergeBlockCreator mergeBlockCreator = this.mergeBlockCreator.apply(parentHeader, random);

    // put the empty block in first
    final Block emptyBlock =
        mergeBlockCreator.createBlock(Optional.of(Collections.emptyList()), random, timestamp);
    executePayload(emptyBlock);
    mergeContext.putPayloadById(payloadIdentifier, emptyBlock);

    // start working on a full block and update the associated value when it's ready
    CompletableFuture.supplyAsync(
            () -> mergeBlockCreator.createBlock(Optional.empty(), random, timestamp))
        .orTimeout(12, TimeUnit.SECONDS)
        .whenComplete(
            (bestBlock, throwable) -> {
              if (throwable != null) {
                LOG.warn("timed out attempting to create block", throwable);
              } else {
                executePayload(bestBlock);
                mergeContext.replacePayloadById(payloadIdentifier, bestBlock);
              }
            });

    return payloadIdentifier;
  }

  public boolean executePayload(final Block block) {
    return blockValidator
        .validateAndProcessBlock(
            protocolContext, block, HeaderValidationMode.FULL, HeaderValidationMode.NONE)
        .isPresent();
  }
}
