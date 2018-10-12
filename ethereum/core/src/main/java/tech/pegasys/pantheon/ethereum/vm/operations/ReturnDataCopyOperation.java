package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Optional;

public class ReturnDataCopyOperation extends AbstractOperation {

  public ReturnDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x3E, "RETURNDATACOPY", 3, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();
    final UInt256 length = frame.getStackItem(2).asUInt256();

    return gasCalculator().dataCopyOperationGasCost(frame, offset, length);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final BytesValue returnData = frame.getReturnData();

    final UInt256 memOffset = frame.popStackItem().asUInt256();
    final UInt256 sourceOffset = frame.popStackItem().asUInt256();
    final UInt256 numBytes = frame.popStackItem().asUInt256();

    frame.writeMemory(memOffset, sourceOffset, numBytes, returnData);
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    final BytesValue returnData = frame.getReturnData();

    final UInt256 start = frame.getStackItem(1).asUInt256();
    final UInt256 length = frame.getStackItem(2).asUInt256();
    final UInt256 returnDataLength = UInt256.of(returnData.size());

    if (!start.fitsInt()
        || !length.fitsInt()
        || start.plus(length).compareTo(returnDataLength) > 0) {
      return Optional.of(ExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS);
    } else {
      return Optional.empty();
    }
  }
}
