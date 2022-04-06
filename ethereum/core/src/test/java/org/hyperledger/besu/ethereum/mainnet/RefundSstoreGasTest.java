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

import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
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
      {"constantinople", constantinople, ZERO, ZERO, ZERO, 200L, 0L},
      {"petersburg", petersburg, ZERO, ZERO, ZERO, 5_000L, 0L},
      {"istanbul", istanbul, ZERO, ZERO, ZERO, 800L, 0L},

      // Zero fresh change
      {"constantinople", constantinople, ZERO, ZERO, ONE, 20_000L, 0L},
      {"petersburg", petersburg, ZERO, ZERO, ONE, 20_000L, 0L},
      {"istanbul", istanbul, ZERO, ZERO, ONE, 20_000L, 0L},

      // Dirty, reset to zero
      {"constantinople", constantinople, ZERO, ONE, ZERO, 200L, 19_800L},
      {"petersburg", petersburg, ZERO, ONE, ZERO, 5_000L, 15_000L},
      {"istanbul", istanbul, ZERO, ONE, ZERO, 800L, 19_200L},

      // Dirty, changed but not reset
      {"constantinople", constantinople, ZERO, ONE, TWO, 200L, 0L},
      {"petersburg", petersburg, ZERO, ONE, TWO, 5_000L, 0L},
      {"istanbul", istanbul, ZERO, ONE, TWO, 800L, 0L},

      // Dirty no-op
      {"constantinople", constantinople, ZERO, ONE, ONE, 200L, 0L},
      {"petersburg", petersburg, ZERO, ONE, ONE, 5_000L, 0L},
      {"istanbul", istanbul, ZERO, ONE, ONE, 800L, 0L},

      // Dirty, zero no-op
      {"constantinople", constantinople, ONE, ZERO, ZERO, 200L, 0L},
      {"petersburg", petersburg, ONE, ZERO, ZERO, 5_000L, 0L},
      {"istanbul", istanbul, ONE, ZERO, ZERO, 800L, 0L},

      // Dirty, reset to non-zero
      {"constantinople", constantinople, ONE, ZERO, ONE, 200L, -15_000L + 4_800L},
      {"petersburg", petersburg, ONE, ZERO, ONE, 20_000L, 0L},
      {"istanbul", istanbul, ONE, ZERO, ONE, 800L, -15_000L + 4_200L},

      // Fresh change to zero
      {"constantinople", constantinople, ONE, ONE, ZERO, 5_000L, 15_000L},
      {"petersburg", petersburg, ONE, ONE, ZERO, 5_000L, 15_000L},
      {"istanbul", istanbul, ONE, ONE, ZERO, 5_000L, 15_000L},

      // Fresh change with all non-zero
      {"constantinople", constantinople, ONE, ONE, TWO, 5_000L, 0L},
      {"petersburg", petersburg, ONE, ONE, TWO, 5_000L, 0L},
      {"istanbul", istanbul, ONE, ONE, TWO, 5_000L, 0L},

      // Dirty, clear originally set value
      {"constantinople", constantinople, ONE, TWO, ZERO, 200L, 15_000L},
      {"petersburg", petersburg, ONE, TWO, ZERO, 5_000L, 15_000L},
      {"istanbul", istanbul, ONE, TWO, ZERO, 800L, 15_000L},

      // Non-zero no-op
      {"constantinople", constantinople, ONE, ONE, ONE, 200L, 0L},
      {"petersburg", petersburg, ONE, ONE, ONE, 5_000L, 0L},
      {"istanbul", istanbul, ONE, ONE, ONE, 800L, 0L},
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
  public long expectedGasCost;

  @Parameter(value = 6)
  public long expectedGasRefund;

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
