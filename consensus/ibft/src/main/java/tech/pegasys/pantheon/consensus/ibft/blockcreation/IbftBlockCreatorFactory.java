/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.blockcreation;

import tech.pegasys.pantheon.consensus.common.ConsensusHelpers;
import tech.pegasys.pantheon.consensus.common.ValidatorVote;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.Vote;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class IbftBlockCreatorFactory {

  private final Function<Long, Long> gasLimitCalculator;
  private final PendingTransactions pendingTransactions;
  protected final ProtocolContext<IbftContext> protocolContext;
  protected final ProtocolSchedule<IbftContext> protocolSchedule;
  private final Address localAddress;

  private volatile BytesValue vanityData;
  private volatile Wei minTransactionGasPrice;

  public IbftBlockCreatorFactory(
      final Function<Long, Long> gasLimitCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<IbftContext> protocolContext,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final MiningParameters miningParams,
      final Address localAddress) {
    this.gasLimitCalculator = gasLimitCalculator;
    this.pendingTransactions = pendingTransactions;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.localAddress = localAddress;
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.vanityData = miningParams.getExtraData();
  }

  public IbftBlockCreator create(final BlockHeader parentHeader, final int round) {
    return new IbftBlockCreator(
        localAddress,
        ph -> createExtraData(round, ph),
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        gasLimitCalculator,
        minTransactionGasPrice,
        parentHeader);
  }

  public void setExtraData(final BytesValue extraData) {
    this.vanityData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice.copy();
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public BytesValue createExtraData(final int round, final BlockHeader parentHeader) {
    final VoteTally voteTally =
        protocolContext
            .getConsensusState()
            .getVoteTallyCache()
            .getVoteTallyAfterBlock(parentHeader);

    final Optional<ValidatorVote> proposal =
        protocolContext.getConsensusState().getVoteProposer().getVote(localAddress, voteTally);

    final List<Address> validators = new ArrayList<>(voteTally.getValidators());

    final IbftExtraData extraData =
        new IbftExtraData(
            ConsensusHelpers.zeroLeftPad(vanityData, IbftExtraData.EXTRA_VANITY_LENGTH),
            Collections.emptyList(),
            toVote(proposal),
            round,
            validators);

    return extraData.encode();
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
