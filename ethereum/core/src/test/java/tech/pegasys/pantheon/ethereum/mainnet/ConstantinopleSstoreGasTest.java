/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.util.uint.UInt256.ONE;
import static tech.pegasys.pantheon.util.uint.UInt256.ZERO;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Before;
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

  private final Account account = mock(Account.class);

  @Before
  public void setUp() {
    when(account.getOriginalStorageValue(UInt256.ZERO)).thenReturn(originalValue);
    when(account.getStorageValue(UInt256.ZERO)).thenReturn(currentValue);
  }

  @Test
  public void shouldChargeCorrectGas() {
    assertThat(gasCalculator.calculateStorageCost(account, UInt256.ZERO, newValue))
        .isEqualTo(expectedGasCost);
  }

  @Test
  public void shouldRefundCorrectGas() {
    assertThat(gasCalculator.calculateStorageRefundAmount(account, UInt256.ZERO, newValue))
        .isEqualTo(expectedGasRefund);
  }
}
