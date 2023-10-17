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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

/** The Bft block creator. */
// This class is responsible for creating a block without committer seals (basically it was just
// too hard to coordinate with the state machine).
public class BftBlockCreator extends AbstractBlockCreator {

  private final BftExtraDataCodec bftExtraDataCodec;

  /**
   * Instantiates a new Bft block creator.
   *
   * @param miningParameters the mining parameters
   * @param forksSchedule the forks schedule
   * @param localAddress the local address
   * @param extraDataCalculator the extra data calculator
   * @param transactionPool the pending transactions
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param parentHeader the parent header
   * @param bftExtraDataCodec the bft extra data codec
   */
  public BftBlockCreator(
      final MiningParameters miningParameters,
      final ForksSchedule<? extends BftConfigOptions> forksSchedule,
      final Address localAddress,
      final ExtraDataCalculator extraDataCalculator,
      final TransactionPool transactionPool,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final BlockHeader parentHeader,
      final BftExtraDataCodec bftExtraDataCodec,
      final EthScheduler ethScheduler) {
    super(
        miningParameters.setCoinbase(localAddress),
        miningBeneficiaryCalculator(localAddress, forksSchedule),
        extraDataCalculator,
        transactionPool,
        protocolContext,
        protocolSchedule,
        parentHeader,
        Optional.empty(),
        ethScheduler);
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  private static MiningBeneficiaryCalculator miningBeneficiaryCalculator(
      final Address localAddress, final ForksSchedule<? extends BftConfigOptions> forksSchedule) {
    return blockNum ->
        forksSchedule.getFork(blockNum).getValue().getMiningBeneficiary().orElse(localAddress);
  }

  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
    final BlockHeaderBuilder builder =
        BlockHeaderBuilder.create()
            .populateFrom(sealableBlockHeader)
            .mixHash(BftHelpers.EXPECTED_MIX_HASH)
            .nonce(0L)
            .blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));

    return builder.buildBlockHeader();
  }
}
