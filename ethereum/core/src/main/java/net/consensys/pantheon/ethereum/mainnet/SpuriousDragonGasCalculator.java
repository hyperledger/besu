package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

public class SpuriousDragonGasCalculator extends TangerineWhistleGasCalculator {

  private static final Gas EXP_OPERATION_BYTE_GAS_COST = Gas.of(50L);

  @Override
  public Gas callOperationGasCost(
      final MessageFrame frame,
      final Gas stipend,
      final UInt256 inputDataOffset,
      final UInt256 inputDataLength,
      final UInt256 outputDataOffset,
      final UInt256 outputDataLength,
      final Wei transferValue,
      final Account recipient) {
    final Gas inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final Gas outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final Gas memoryExpansionCost = inputDataMemoryExpansionCost.max(outputDataMemoryExpansionCost);

    Gas cost = callOperationBaseGasCost().plus(memoryExpansionCost);

    if (!transferValue.isZero()) {
      cost = cost.plus(callValueTransferGasCost());
    }

    if ((recipient == null || recipient.isEmpty()) && !transferValue.isZero()) {
      cost = cost.plus(newAccountGasCost());
    }

    return cost;
  }

  @Override
  protected Gas expOperationByteGasCost() {
    return EXP_OPERATION_BYTE_GAS_COST;
  }

  private static final Gas SELFDESTRUCT_OPERATION_GAS_COST = Gas.of(5_000L);

  private static final Gas SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = Gas.of(30_000L);

  @Override
  public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if ((recipient == null || recipient.isEmpty()) && !inheritance.isZero()) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }
}
