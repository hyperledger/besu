package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.util.bytes.BytesValue;

/** Skeleton class for @{link PrecompileContract} implementations. */
public abstract class AbstractPrecompiledContract implements PrecompiledContract {

  private final GasCalculator gasCalculator;

  private final String name;

  public AbstractPrecompiledContract(final String name, final GasCalculator gasCalculator) {
    this.name = name;
    this.gasCalculator = gasCalculator;
  }

  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public abstract Gas gasRequirement(BytesValue input);

  @Override
  public abstract BytesValue compute(BytesValue input);
}
