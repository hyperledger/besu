package tech.pegasys.pantheon.ethereum.mainnet.precompiles;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
