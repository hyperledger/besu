package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

public class ConstantinopleGasCalculator extends SpuriousDragonGasCalculator {

  @Override
  public Gas create2OperationGasCost(final MessageFrame frame) {
    final UInt256 initCodeLength = frame.getStackItem(2).asUInt256();
    final UInt256 numWords = initCodeLength.dividedCeilBy(Bytes32.SIZE);
    final Gas initCodeHashCost = SHA3_OPERATION_WORD_GAS_COST.times(Gas.of(numWords));
    return createOperationGasCost(frame).plus(initCodeHashCost);
  }
}
