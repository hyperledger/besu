package tech.pegasys.pantheon.ethereum.mainnet.precompiles;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
