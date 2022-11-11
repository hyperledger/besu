package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.Code.EMPTY_CODE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class Push0OperationTest {

  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  public void shouldPush0OntoStack() {
    final MessageFrame frame = createMessageFrame(100, Optional.of(Wei.of(5L)));
    final Operation operation = new PushOperation(0, gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    Mockito.verify(frame).pushStackItem(Bytes.EMPTY);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertSuccessResult(result);
  }
  private void assertSuccessResult(final OperationResult result) {
    assertThat(result).isNotNull();
    assertThat(result.getHaltReason()).isNull();
  }
  private MessageFrame createMessageFrame(final long initialGas, final Optional<Wei> baseFee) {
    final MessageFrame frame = mock(MessageFrame.class);
    when(frame.getRemainingGas()).thenReturn(initialGas);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(baseFee);
    when(frame.getBlockValues()).thenReturn(blockHeader);
    when(frame.getCode()).thenReturn(EMPTY_CODE);
    return frame;
  }
}
