package org.hyperledger.besu.consensus.qbft.blockcreation;

import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** Supports contract based voters and validators in extra data */
public class QbftBlockCreatorFactory extends BftBlockCreatorFactory {
  private final boolean extraDataWithRoundInformationOnly;

  public QbftBlockCreatorFactory(
      final GasLimitCalculator gasLimitCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningParameters miningParams,
      final Address localAddress,
      final Address miningBeneficiary,
      final BftExtraDataCodec bftExtraDataCodec,
      final boolean extraDataWithRoundInformationOnly) {
    super(
        gasLimitCalculator,
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        miningParams,
        localAddress,
        miningBeneficiary,
        bftExtraDataCodec);
    this.extraDataWithRoundInformationOnly = extraDataWithRoundInformationOnly;
  }

  @Override
  public Bytes createExtraData(final int round, final BlockHeader parentHeader) {
    if (extraDataWithRoundInformationOnly) {
      // vote and validators come from contract instead of block
      final BftExtraData extraData =
          new BftExtraData(
              ConsensusHelpers.zeroLeftPad(vanityData, BftExtraDataCodec.EXTRA_VANITY_LENGTH),
              Collections.emptyList(),
              Optional.empty(),
              round,
              Collections.emptyList());
      return getBftExtraDataCodec().encode(extraData);
    } else {
      return super.createExtraData(round, parentHeader);
    }
  }
}
