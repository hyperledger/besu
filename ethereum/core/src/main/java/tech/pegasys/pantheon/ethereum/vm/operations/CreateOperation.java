package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

public class CreateOperation extends AbstractCreateOperation {

  public CreateOperation(final GasCalculator gasCalculator) {
    super(0xF0, "CREATE", 3, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().createOperationGasCost(frame);
  }

  @Override
  protected Address targetContractAddress(final MessageFrame frame) {
    final Account sender = frame.getWorldState().get(frame.getRecipientAddress());
    // Decrement nonce by 1 to normalize the effect of transaction execution
    return Address.contractAddress(frame.getRecipientAddress(), sender.getNonce() - 1L);
  }
}
