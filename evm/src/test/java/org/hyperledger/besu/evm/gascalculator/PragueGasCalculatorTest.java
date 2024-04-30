/*
 * Copyright Hyperledger Besu contributors.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PragueGasCalculatorTest {
    @Test
    public void testAuthOperationGasCost() {
        PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
        MessageFrame runningIn = mock(MessageFrame.class);
        Address authority = Address.fromHexString("0xdeadbeef");
        when(runningIn.isAddressWarm(authority)).thenReturn(true);
        long gasSpent = pragueGasCalculator.authOperationGasCost(runningIn, 0, 97, authority);
        assertEquals(3100 + 100 + pragueGasCalculator.memoryExpansionGasCost(runningIn, 0, 97), gasSpent);
    }

    @Test
    public void testAuthCallOperationGasCost() {
        PragueGasCalculator pragueGasCalculator = new PragueGasCalculator();
        MessageFrame runningIn = mock(MessageFrame.class);
        Address authority = Address.fromHexString("0xdeadbeef");
        when(runningIn.isAddressWarm(authority)).thenReturn(true);
        long gasSpent = pragueGasCalculator.callOperationGasCost(runningIn, 63, 0, 97, 100, 97, Wei.ONE, );
        assertEquals(3100 + 100 + pragueGasCalculator.memoryExpansionGasCost(runningIn, 0, 97), gasSpent);
    }
}
