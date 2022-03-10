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
package org.hyperledger.besu.consensus.qbft.blockcreation;

import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** Supports contract based voters and validators in extra data */
public class QbftBlockCreatorFactory extends BftBlockCreatorFactory<QbftConfigOptions> {
  public QbftBlockCreatorFactory(
      final AbstractPendingTransactionsSorter pendingTransactions,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final ForksSchedule<QbftConfigOptions> forksSchedule,
      final MiningParameters miningParams,
      final Address localAddress,
      final BftExtraDataCodec bftExtraDataCodec) {
    super(
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        forksSchedule,
        miningParams,
        localAddress,
        bftExtraDataCodec);
  }

  @Override
  public BlockCreator create(final BlockHeader parentHeader, final int round) {
    final BlockCreator blockCreator = super.create(parentHeader, round);
    final QbftContext qbftContext = protocolContext.getConsensusContext(QbftContext.class);
    if (qbftContext.getPkiBlockCreationConfiguration().isEmpty()) {
      return blockCreator;
    } else {
      return new PkiQbftBlockCreator(
          blockCreator, qbftContext.getPkiBlockCreationConfiguration().get(), bftExtraDataCodec);
    }
  }

  @Override
  public Bytes createExtraData(final int round, final BlockHeader parentHeader) {
    if (forksSchedule.getFork(parentHeader.getNumber() + 1L).getValue().isValidatorContractMode()) {
      // vote and validators will come from contract instead of block
      final BftExtraData extraData =
          new BftExtraData(
              ConsensusHelpers.zeroLeftPad(vanityData, BftExtraDataCodec.EXTRA_VANITY_LENGTH),
              Collections.emptyList(),
              Optional.empty(),
              round,
              Collections.emptyList());
      return bftExtraDataCodec.encode(extraData);
    }

    return super.createExtraData(round, parentHeader);
  }
}
