package org.hyperledger.besu.evm.gascalculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CancunGasCalculatorTest {

  private final GasCalculator gasCalculator = new CancunGasCalculator();

  @Test
  public void shouldCalculateExcessDataGasCorrectly() {

    long target = CancunGasCalculator.getTargetDataGasPerBlock();
    long excess = 1L;

    assertThat(gasCalculator.computeExcessDataGas(0L, 0L)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(target, 0L)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(0L, target)).isEqualTo(0);

    assertThat(gasCalculator.computeExcessDataGas(excess, target)).isEqualTo(excess);

    assertThat(gasCalculator.computeExcessDataGas(target, excess)).isEqualTo(excess);

    assertThat(gasCalculator.computeExcessDataGas(target, target)).isEqualTo(target);
  }
}
