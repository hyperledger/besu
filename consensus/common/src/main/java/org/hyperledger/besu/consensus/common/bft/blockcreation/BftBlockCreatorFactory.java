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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tuweni.bytes.Bytes;

public class BftBlockCreatorFactory {

  protected final AbstractPendingTransactionsSorter pendingTransactions;
  protected final ProtocolContext protocolContext;
  protected final ProtocolSchedule protocolSchedule;
  protected final BftExtraDataCodec bftExtraDataCodec;
  protected final Address localAddress;
  protected final Address miningBeneficiary;

  protected volatile Bytes vanityData;
  protected volatile Wei minTransactionGasPrice;
  protected volatile Double minBlockOccupancyRatio;
  protected volatile Optional<AtomicLong> targetGasLimit;

  public BftBlockCreatorFactory(
      final AbstractPendingTransactionsSorter pendingTransactions,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningParameters miningParams,
      final Address localAddress,
      final Address miningBeneficiary,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.pendingTransactions = pendingTransactions;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.localAddress = localAddress;
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.minBlockOccupancyRatio = miningParams.getMinBlockOccupancyRatio();
    this.vanityData = miningParams.getExtraData();
    this.miningBeneficiary = miningBeneficiary;
    this.bftExtraDataCodec = bftExtraDataCodec;
    this.targetGasLimit = miningParams.getTargetGasLimit();
  }

  public BlockCreator create(final BlockHeader parentHeader, final int round) {
    return new BftBlockCreator(
        localAddress,
        () -> targetGasLimit.map(AtomicLong::longValue),
        ph -> createExtraData(round, ph),
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        minTransactionGasPrice,
        minBlockOccupancyRatio,
        parentHeader,
        miningBeneficiary,
        bftExtraDataCodec);
  }

  public void setExtraData(final Bytes extraData) {
    this.vanityData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public Bytes createExtraData(final int round, final BlockHeader parentHeader) {
    final BftContext bftContext = protocolContext.getConsensusState(BftContext.class);
    final ValidatorProvider validatorProvider = bftContext.getValidatorProvider();
    checkState(validatorProvider.getVoteProvider().isPresent(), "Bft requires a vote provider");
    final Optional<ValidatorVote> proposal =
        validatorProvider.getVoteProvider().get().getVoteAfterBlock(parentHeader, localAddress);

    final List<Address> validators =
        new ArrayList<>(validatorProvider.getValidatorsAfterBlock(parentHeader));

    final BftExtraData extraData =
        new BftExtraData(
            ConsensusHelpers.zeroLeftPad(vanityData, BftExtraDataCodec.EXTRA_VANITY_LENGTH),
            Collections.emptyList(),
            toVote(proposal),
            round,
            validators);

    return bftExtraDataCodec.encode(extraData);
  }

  public void changeTargetGasLimit(final Long newTargetGasLimit) {
    if (AbstractGasLimitSpecification.isValidTargetGasLimit(newTargetGasLimit)) {
      this.targetGasLimit.ifPresentOrElse(
          existing -> existing.set(newTargetGasLimit),
          () -> this.targetGasLimit = Optional.of(new AtomicLong(newTargetGasLimit)));
    } else {
      throw new UnsupportedOperationException("Specified target gas limit is invalid");
    }
  }

  public Address getLocalAddress() {
    return localAddress;
  }

  private static Optional<Vote> toVote(final Optional<ValidatorVote> input) {
    return input
        .map(v -> Optional.of(new Vote(v.getRecipient(), v.getVotePolarity())))
        .orElse(Optional.empty());
  }
}
