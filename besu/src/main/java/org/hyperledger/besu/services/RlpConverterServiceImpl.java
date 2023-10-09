package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;

import org.apache.tuweni.bytes.Bytes;

public class RlpConverterServiceImpl implements RlpConverterService {

  private final BlockHeaderFunctions blockHeaderFunctions;

  public RlpConverterServiceImpl(final ProtocolSchedule protocolSchedule) {
    this.blockHeaderFunctions = ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
  }

  @Override
  public BlockHeader buildHeaderFromRlp(final Bytes rlp) {
    return org.hyperledger.besu.ethereum.core.BlockHeader.readFrom(
        RLP.input(rlp), blockHeaderFunctions);
  }

  @Override
  public BlockBody buildBodyFromRlp(final Bytes rlp) {
    return org.hyperledger.besu.ethereum.core.BlockBody.readWrappedBodyFrom(
        RLP.input(rlp), blockHeaderFunctions);
  }

  @Override
  public TransactionReceipt buildReceiptFromRlp(final Bytes rlp) {
    return org.hyperledger.besu.ethereum.core.TransactionReceipt.readFrom(RLP.input(rlp));
  }

  @Override
  public Bytes buildRlpFromHeader(final BlockHeader blockHeader) {
    return RLP.encode(
        org.hyperledger.besu.ethereum.core.BlockHeader.convertPluginBlockHeader(
                blockHeader, blockHeaderFunctions)
            ::writeTo);
  }

  @Override
  public Bytes buildRlpFromBody(final BlockBody blockBody) {
    return RLP.encode(
        rlpOutput ->
            ((org.hyperledger.besu.ethereum.core.BlockBody) blockBody)
                .writeWrappedBodyTo(rlpOutput));
  }

  @Override
  public Bytes buildRlpFromReceipt(final TransactionReceipt receipt) {
    return RLP.encode(
        rlpOutput ->
            ((org.hyperledger.besu.ethereum.core.TransactionReceipt) receipt).writeTo(rlpOutput));
  }
}
