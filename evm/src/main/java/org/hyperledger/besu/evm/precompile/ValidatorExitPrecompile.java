package org.hyperledger.besu.evm.precompile;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class ValidatorExitPrecompile extends AbstractStatefulPrecompiledContract {

  public ValidatorExitPrecompile(final GasCalculator gasCalculator) {
    super("ValidatorExitPrecompile", gasCalculator);
  }

  @Override
  protected GasCalculator gasCalculator() {
    return super.gasCalculator();
  }

  @Override
  public String getName() {
    return super.getName();
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return 0;
  }
}
