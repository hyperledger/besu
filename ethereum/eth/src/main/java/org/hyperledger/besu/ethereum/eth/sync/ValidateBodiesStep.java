package org.hyperledger.besu.ethereum.eth.sync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ValidateBodiesStep
    implements Function<List<BlockWithReceipts>, List<BlockWithReceipts>> {

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;

  public ValidateBodiesStep(
      final ProtocolContext protocolContext, final ProtocolSchedule protocolSchedule) {
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  @SuppressWarnings("MixedMutabilityReturnType")
  public List<BlockWithReceipts> apply(final List<BlockWithReceipts> blocksWithReceipts) {
    List<BlockWithReceipts> validatedBlocks = new ArrayList<>();
    for (BlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      if (isBlockBodyValid(blockWithReceipts)) {
        validatedBlocks.add(blockWithReceipts);
      } else {
        return Collections.emptyList();
      }
    }

    return validatedBlocks;
  }

  private boolean isBlockBodyValid(final BlockWithReceipts blockWithReceipts) {
    final BlockBodyValidator validator =
        protocolSchedule.getByBlockHeader(blockWithReceipts.getHeader()).getBlockBodyValidator();
    return (validator.validateBodyLight(
        protocolContext,
        blockWithReceipts.getBlock(),
        blockWithReceipts.getReceipts(),
        HeaderValidationMode.NONE));
  }
}
