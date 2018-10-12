package tech.pegasys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.util.uint.UInt256.ONE;
import static tech.pegasys.pantheon.util.uint.UInt256.ZERO;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantinopleSstoreGasTest {

  private static final UInt256 TWO = UInt256.of(2);

  private final ConstantinopleGasCalculator gasCalculator = new ConstantinopleGasCalculator();

  @Parameters(name = "original: {0}, current: {1}, new: {2}")
  public static Object[][] scenarios() {
    return new Object[][] {
      // Zero no-op
      {ZERO, ZERO, ZERO, Gas.of(200), Gas.ZERO},

      // Zero fresh change
      {ZERO, ZERO, ONE, Gas.of(20_000), Gas.ZERO},

      // Dirty, reset to zero
      {ZERO, ONE, ZERO, Gas.of(200), Gas.of(19800)},

      // Dirty, changed but not reset
      {ZERO, ONE, TWO, Gas.of(200), Gas.ZERO},

      // Dirty no-op
      {ZERO, ONE, ONE, Gas.of(200), Gas.ZERO},

      // Dirty, zero no-op
      {ONE, ZERO, ZERO, Gas.of(200), Gas.ZERO},

      // Dirty, reset to non-zero
      {ONE, ZERO, ONE, Gas.of(200), Gas.of(-15000).plus(Gas.of(4800))},

      // Fresh change to zero
      {ONE, ONE, ZERO, Gas.of(5000), Gas.of(15000)},

      // Fresh change with all non-zero
      {ONE, ONE, TWO, Gas.of(5000), Gas.ZERO},

      // Dirty, clear originally set value
      {ONE, TWO, ZERO, Gas.of(200), Gas.of(15000)},

      // Non-zero no-op
      {ONE, ONE, ONE, Gas.of(200), Gas.ZERO},
    };
  }

  @Parameter public UInt256 originalValue;

  @Parameter(value = 1)
  public UInt256 currentValue;

  @Parameter(value = 2)
  public UInt256 newValue;

  @Parameter(value = 3)
  public Gas expectedGasCost;

  @Parameter(value = 4)
  public Gas expectedGasRefund;

  @Test
  public void shouldChargeCorrectGas() {
    assertThat(gasCalculator.calculateStorageCost(() -> originalValue, currentValue, newValue))
        .isEqualTo(expectedGasCost);
  }

  @Test
  public void shouldRefundCorrectGas() {
    assertThat(
            gasCalculator.calculateStorageRefundAmount(() -> originalValue, currentValue, newValue))
        .isEqualTo(expectedGasRefund);
  }
}
