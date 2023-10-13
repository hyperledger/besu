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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * The Bft block creator factory.
 *
 * @param <T> the type parameter
 */
public class BftBlockCreatorFactory<T extends BftConfigOptions> {
  /** The Forks schedule. */
  protected final ForksSchedule<T> forksSchedule;

  protected final MiningParameters miningParameters;
  private final TransactionPool transactionPool;
  /** The Protocol context. */
  protected final ProtocolContext protocolContext;
  /** The Protocol schedule. */
  protected final ProtocolSchedule protocolSchedule;
  /** The Bft extra data codec. */
  protected final BftExtraDataCodec bftExtraDataCodec;

  private final Address localAddress;

  /**
   * Instantiates a new Bft block creator factory.
   *
   * @param transactionPool the pending transactions
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param forksSchedule the forks schedule
   * @param miningParams the mining params
   * @param localAddress the local address
   * @param bftExtraDataCodec the bft extra data codec
   */
  public BftBlockCreatorFactory(
      final TransactionPool transactionPool,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final ForksSchedule<T> forksSchedule,
      final MiningParameters miningParams,
      final Address localAddress,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.transactionPool = transactionPool;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.forksSchedule = forksSchedule;
    this.localAddress = localAddress;
    this.miningParameters = miningParams;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  /**
   * Create block creator.
   *
   * @param parentHeader the parent header
   * @param round the round
   * @return the block creator
   */
  public BlockCreator create(final BlockHeader parentHeader, final int round) {
    return new BftBlockCreator(
        miningParameters,
        forksSchedule,
        localAddress,
        ph -> createExtraData(round, ph),
        transactionPool,
        protocolContext,
        protocolSchedule,
        parentHeader,
        bftExtraDataCodec);
  }

  /**
   * Sets extra data.
   *
   * @param extraData the extra data
   */
  public void setExtraData(final Bytes extraData) {

    miningParameters.setExtraData(extraData.copy());
  }

  /**
   * Sets min transaction gas price.
   *
   * @param minTransactionGasPrice the min transaction gas price
   */
  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    miningParameters.setMinTransactionGasPrice(minTransactionGasPrice);
  }

  /**
   * Gets min transaction gas price.
   *
   * @return the min transaction gas price
   */
  public Wei getMinTransactionGasPrice() {
    return miningParameters.getMinTransactionGasPrice();
  }

  /**
   * Create extra data bytes.
   *
   * @param round the round
   * @param parentHeader the parent header
   * @return the bytes
   */
  public Bytes createExtraData(final int round, final BlockHeader parentHeader) {
    final BftContext bftContext = protocolContext.getConsensusContext(BftContext.class);
    final ValidatorProvider validatorProvider = bftContext.getValidatorProvider();
    Optional<VoteProvider> voteProviderAfterBlock =
        validatorProvider.getVoteProviderAfterBlock(parentHeader);
    checkState(voteProviderAfterBlock.isPresent(), "Bft requires a vote provider");
    final Optional<ValidatorVote> proposal =
        voteProviderAfterBlock.get().getVoteAfterBlock(parentHeader, localAddress);

    final List<Address> validators =
        new ArrayList<>(validatorProvider.getValidatorsAfterBlock(parentHeader));

    final BftExtraData extraData =
        new BftExtraData(
            ConsensusHelpers.zeroLeftPad(
                miningParameters.getExtraData(), BftExtraDataCodec.EXTRA_VANITY_LENGTH),
            Collections.emptyList(),
            toVote(proposal),
            round,
            validators);

    return bftExtraDataCodec.encode(extraData);
  }

  /**
   * Change target gas limit.
   *
   * @param newTargetGasLimit the new target gas limit
   */
  public void changeTargetGasLimit(final Long newTargetGasLimit) {
    if (AbstractGasLimitSpecification.isValidTargetGasLimit(newTargetGasLimit)) {
      miningParameters.setTargetGasLimit(newTargetGasLimit);
    } else {
      throw new UnsupportedOperationException("Specified target gas limit is invalid");
    }
  }

  /**
   * Gets local address.
   *
   * @return the local address
   */
  public Address getLocalAddress() {
    return localAddress;
  }

  private static Optional<Vote> toVote(final Optional<ValidatorVote> input) {
    return input
        .map(v -> Optional.of(new Vote(v.getRecipient(), v.getVotePolarity())))
        .orElse(Optional.empty());
  }
}
