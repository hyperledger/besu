package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SDivOperation;

public class SDivOperationBenchmark extends BinaryOperationBenchmark {
  @Override
  protected Operation.OperationResult invoke(final MessageFrame frame) {
    return SDivOperation.staticOperation(frame);
  }
}
