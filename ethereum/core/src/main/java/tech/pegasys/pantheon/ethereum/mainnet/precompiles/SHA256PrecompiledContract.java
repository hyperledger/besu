package tech.pegasys.pantheon.ethereum.mainnet.precompiles;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class SHA256PrecompiledContract extends AbstractPrecompiledContract {

  public SHA256PrecompiledContract(final GasCalculator gasCalculator) {
    super("SHA256", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCalculator().sha256PrecompiledContractGasCost(input);
  }

  @Override
  public BytesValue compute(final BytesValue input) {
    return Hash.sha256(input);
  }
}
