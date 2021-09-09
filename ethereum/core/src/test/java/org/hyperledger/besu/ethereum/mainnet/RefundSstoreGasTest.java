/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet;

import static org.apache.tuweni.units.bigints.UInt256.ONE;
import static org.apache.tuweni.units.bigints.UInt256.ZERO;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;

import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RefundSstoreGasTest {

  private static final UInt256 TWO = UInt256.valueOf(2);

  @Parameters(name = "calculator: {0}, original: {2}, current: {3}, new: {4}")
  public static Object[][] scenarios() {
    final GasCalculator constantinople = new ConstantinopleGasCalculator();
    final GasCalculator petersburg = new PetersburgGasCalculator();
    final GasCalculator istanbul = new IstanbulGasCalculator();
    return new Object[][] {
      // Zero no-op
      {"constantinople", constantinople, ZERO, ZERO, ZERO, Gas.of(200), Gas.ZERO},
      {"petersburg", petersburg, ZERO, ZERO, ZERO, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ZERO, ZERO, ZERO, Gas.of(800), Gas.ZERO},

      // Zero fresh change
      {"constantinople", constantinople, ZERO, ZERO, ONE, Gas.of(20_000), Gas.ZERO},
      {"petersburg", petersburg, ZERO, ZERO, ONE, Gas.of(20_000), Gas.ZERO},
      {"istanbul", istanbul, ZERO, ZERO, ONE, Gas.of(20_000), Gas.ZERO},

      // Dirty, reset to zero
      {"constantinople", constantinople, ZERO, ONE, ZERO, Gas.of(200), Gas.of(19_800)},
      {"petersburg", petersburg, ZERO, ONE, ZERO, Gas.of(5_000), Gas.of(15_000)},
      {"istanbul", istanbul, ZERO, ONE, ZERO, Gas.of(800), Gas.of(19_200)},

      // Dirty, changed but not reset
      {"constantinople", constantinople, ZERO, ONE, TWO, Gas.of(200), Gas.ZERO},
      {"petersburg", petersburg, ZERO, ONE, TWO, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ZERO, ONE, TWO, Gas.of(800), Gas.ZERO},

      // Dirty no-op
      {"constantinople", constantinople, ZERO, ONE, ONE, Gas.of(200), Gas.ZERO},
      {"petersburg", petersburg, ZERO, ONE, ONE, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ZERO, ONE, ONE, Gas.of(800), Gas.ZERO},

      // Dirty, zero no-op
      {"constantinople", constantinople, ONE, ZERO, ZERO, Gas.of(200), Gas.ZERO},
      {"petersburg", petersburg, ONE, ZERO, ZERO, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ONE, ZERO, ZERO, Gas.of(800), Gas.ZERO},

      // Dirty, reset to non-zero
      {
        "constantinople",
        constantinople,
        ONE,
        ZERO,
        ONE,
        Gas.of(200),
        Gas.of(-15_000).plus(Gas.of(4_800))
      },
      {"petersburg", petersburg, ONE, ZERO, ONE, Gas.of(20_000), Gas.ZERO},
      {"istanbul", istanbul, ONE, ZERO, ONE, Gas.of(800), Gas.of(-15_000).plus(Gas.of(4_200))},

      // Fresh change to zero
      {"constantinople", constantinople, ONE, ONE, ZERO, Gas.of(5_000), Gas.of(15_000)},
      {"petersburg", petersburg, ONE, ONE, ZERO, Gas.of(5_000), Gas.of(15_000)},
      {"istanbul", istanbul, ONE, ONE, ZERO, Gas.of(5_000), Gas.of(15_000)},

      // Fresh change with all non-zero
      {"constantinople", constantinople, ONE, ONE, TWO, Gas.of(5_000), Gas.ZERO},
      {"petersburg", petersburg, ONE, ONE, TWO, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ONE, ONE, TWO, Gas.of(5_000), Gas.ZERO},

      // Dirty, clear originally set value
      {"constantinople", constantinople, ONE, TWO, ZERO, Gas.of(200), Gas.of(15_000)},
      {"petersburg", petersburg, ONE, TWO, ZERO, Gas.of(5_000), Gas.of(15_000)},
      {"istanbul", istanbul, ONE, TWO, ZERO, Gas.of(800), Gas.of(15_000)},

      // Non-zero no-op
      {"constantinople", constantinople, ONE, ONE, ONE, Gas.of(200), Gas.ZERO},
      {"petersburg", petersburg, ONE, ONE, ONE, Gas.of(5_000), Gas.ZERO},
      {"istanbul", istanbul, ONE, ONE, ONE, Gas.of(800), Gas.ZERO},
    };
  }

  @Parameter public String forkName;

  @Parameter(value = 1)
  public GasCalculator gasCalculator = new ConstantinopleGasCalculator();

  @Parameter(value = 2)
  public UInt256 originalValue;

  @Parameter(value = 3)
  public UInt256 currentValue;

  @Parameter(value = 4)
  public UInt256 newValue;

  @Parameter(value = 5)
  public Gas expectedGasCost;

  @Parameter(value = 6)
  public Gas expectedGasRefund;

  private final Account account = mock(Account.class);

  @Before
  public void setUp() {
    when(account.getOriginalStorageValue(UInt256.ZERO)).thenReturn(originalValue);
    when(account.getStorageValue(UInt256.ZERO)).thenReturn(currentValue);
  }

  @Test
  public void shouldChargeCorrectGas() {
    Assertions.assertThat(gasCalculator.calculateStorageCost(account, UInt256.ZERO, newValue))
        .isEqualTo(expectedGasCost);
  }

  @Test
  public void shouldRefundCorrectGas() {
    Assertions.assertThat(
            gasCalculator.calculateStorageRefundAmount(account, UInt256.ZERO, newValue))
        .isEqualTo(expectedGasRefund);
  }
}
