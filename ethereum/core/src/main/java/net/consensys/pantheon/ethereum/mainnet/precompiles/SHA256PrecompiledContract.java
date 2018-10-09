package net.consensys.pantheon.ethereum.mainnet.precompiles;

import net.consensys.pantheon.crypto.Hash;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.util.bytes.BytesValue;

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
