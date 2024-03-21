package org.hyperledger.besu.evm.precompile;

import java.util.function.Supplier;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;

public abstract class AbstractStatefulPrecompiledContract extends AbstractPrecompiledContract {

  private Supplier<MutableWorldView> mutableWorldView;

  protected AbstractStatefulPrecompiledContract(final String name,
      final GasCalculator gasCalculator) {
    super(name, gasCalculator);
  }

  MutableWorldView get() {
    return mutableWorldView.get();
  }
}
