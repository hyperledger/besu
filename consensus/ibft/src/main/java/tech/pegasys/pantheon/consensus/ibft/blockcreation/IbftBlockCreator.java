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

import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.SealableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Function;

// This class is responsible for creating a block without committer seals (basically it was just
// too hard to coordinate with the state machine).
public class IbftBlockCreator extends AbstractBlockCreator<IbftContext> {

  public IbftBlockCreator(
      final Address localAddress,
      final ExtraDataCalculator extraDataCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<IbftContext> protocolContext,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final Function<Long, Long> gasLimitCalculator,
      final Wei minTransactionGasPrice,
      final BlockHeader parentHeader) {
    super(
        localAddress,
        extraDataCalculator,
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        gasLimitCalculator,
        minTransactionGasPrice,
        localAddress,
        parentHeader);
  }

  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {

    final IbftExtraData extraData = IbftExtraData.decode(sealableBlockHeader.getExtraData());

    final BlockHeaderBuilder builder =
        BlockHeaderBuilder.create()
            .populateFrom(sealableBlockHeader)
            .mixHash(IbftHelpers.EXPECTED_MIX_HASH)
            .nonce(0L)
            .blockHashFunction(
                blockHeader ->
                    IbftBlockHashing.calculateDataHashForCommittedSeal(blockHeader, extraData));

    return builder.buildBlockHeader();
  }
}
