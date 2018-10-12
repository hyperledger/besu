package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256;

public class TangerineWhistleGasCalculator extends HomesteadGasCalculator {

  private static final Gas BALANCE_OPERATION_GAS_COST = Gas.of(400L);

  private static final Gas CALL_OPERATION_BASE_GAS_COST = Gas.of(700L);

  private static final Gas EXT_CODE_BASE_GAS_COST = Gas.of(700L);

  private static final Gas SELFDESTRUCT_OPERATION_GAS_COST = Gas.of(5_000L);

  private static final Gas SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = Gas.of(30_000L);

  private static final Gas SLOAD_OPERATION_GAS_COST = Gas.of(200L);

  @Override
  public Gas getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  // Returns all but 1/64 (n - floor(n /16)) of the provided value
  private static Gas allButOneSixtyFourth(final Gas value) {
    return value.minus(value.dividedBy(64));
  }

  @Override
  protected Gas callOperationBaseGasCost() {
    return CALL_OPERATION_BASE_GAS_COST;
  }

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

    if (recipient == null) {
      cost = cost.plus(newAccountGasCost());
    }

    return cost;
  }

  private static Gas gasCap(final Gas remaining, final Gas stipend) {
    return allButOneSixtyFourth(remaining).min(stipend);
  }

  @Override
  public Gas gasAvailableForChildCall(
      final MessageFrame frame, final Gas stipend, final boolean transfersValue) {
    final Gas gasCap = gasCap(frame.getRemainingGas(), stipend);

    // TODO: Integrate this into AbstractCallOperation since it's
    // a little out of place to mutate the frame here.
    frame.decrementRemainingGas(gasCap);

    if (transfersValue) {
      return gasCap.plus(additionalCallStipend());
    } else {
      return gasCap;
    }
  }

  @Override
  public Gas gasAvailableForChildCreate(final Gas stipend) {
    return allButOneSixtyFourth(stipend);
  }

  @Override
  protected Gas extCodeBaseGasCost() {
    return EXT_CODE_BASE_GAS_COST;
  }

  @Override
  public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if (recipient == null) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }

  @Override
  public Gas getSloadOperationGasCost() {
    return SLOAD_OPERATION_GAS_COST;
  }
}
