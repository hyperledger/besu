/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.gascalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.junit.jupiter.api.Test;

public class PragueGasCalculatorTest {
  @Test
  public void testAuthOperationGasCost() {
    PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
    MessageFrame runningIn = mock(MessageFrame.class);
    Address authority = Address.fromHexString("0xdeadbeef");
    when(runningIn.isAddressWarm(authority)).thenReturn(true);
    long gasSpent = pragueGasCalculator.authOperationGasCost(runningIn, 0, 97, authority);
    assertEquals(
        3100 + 100 + pragueGasCalculator.memoryExpansionGasCost(runningIn, 0, 97), gasSpent);
  }

  @Test
  public void testAuthCallOperationGasCostWithTransfer() {
    PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
    MessageFrame runningIn = mock(MessageFrame.class);
    Account invoker = mock(MutableAccount.class);
    when(invoker.getAddress()).thenReturn(Address.fromHexString("0xCafeBabe"));
    Address invokee = Address.fromHexString("0xdeadbeef");
    when(runningIn.isAddressWarm(invokee)).thenReturn(true);
    long gasSpentInAuthCall =
        pragueGasCalculator.authCallOperationGasCost(
            runningIn, 63, 0, 97, 100, 97, Wei.ONE, invoker, invokee, true);
    long gasSpentInCall =
        pragueGasCalculator.callOperationGasCost(
            runningIn, 63, 0, 97, 100, 97, Wei.ONE, invoker, invokee, true);
    assertEquals(gasSpentInCall - 2300, gasSpentInAuthCall);
  }

  @Test
  public void testAuthCallOperationGasCostNoTransfer() {
    PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
    MessageFrame runningIn = mock(MessageFrame.class);
    Account invoker = mock(MutableAccount.class);
    when(invoker.getAddress()).thenReturn(Address.fromHexString("0xCafeBabe"));
    Address invokee = Address.fromHexString("0xdeadbeef");
    when(runningIn.isAddressWarm(invokee)).thenReturn(true);
    long gasSpentInAuthCall =
        pragueGasCalculator.authCallOperationGasCost(
            runningIn, 63, 0, 97, 100, 97, Wei.ZERO, invoker, invokee, true);
    long gasSpentInCall =
        pragueGasCalculator.callOperationGasCost(
            runningIn, 63, 0, 97, 100, 97, Wei.ZERO, invoker, invokee, true);
    assertEquals(gasSpentInCall, gasSpentInAuthCall);
  }
}
