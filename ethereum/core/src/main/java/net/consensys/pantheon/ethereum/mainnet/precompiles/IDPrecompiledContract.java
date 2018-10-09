package net.consensys.pantheon.ethereum.mainnet.precompiles;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.util.bytes.BytesValue;

public class IDPrecompiledContract extends AbstractPrecompiledContract {

  public IDPrecompiledContract(final GasCalculator gasCalculator) {
    super("ID", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCalculator().idPrecompiledContractGasCost(input);
  }

  @Override
  public BytesValue compute(final BytesValue input) {
    return input;
  }
}
