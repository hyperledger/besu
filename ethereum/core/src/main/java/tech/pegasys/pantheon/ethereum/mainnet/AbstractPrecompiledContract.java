package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
