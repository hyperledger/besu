package net.consensys.pantheon.ethereum.mainnet.precompiles;

import net.consensys.pantheon.crypto.Hash;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

public class RIPEMD160PrecompiledContract extends AbstractPrecompiledContract {

  public RIPEMD160PrecompiledContract(final GasCalculator gasCalculator) {
    super("RIPEMD160", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCalculator().ripemd160PrecompiledContractGasCost(input);
  }

  @Override
  public BytesValue compute(final BytesValue input) {
    return Bytes32.leftPad(Hash.ripemd160(input));
  }
}
