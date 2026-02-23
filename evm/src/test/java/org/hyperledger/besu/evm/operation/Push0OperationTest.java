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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.junit.jupiter.api.Test;

class Push0OperationTest {

  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  void shouldPush0OntoStack() {
    final MessageFrame frame = new TestMessageFrameBuilder().build();
    final Operation operation = new Push0Operation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(UInt256.ZERO);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertThat(result).isNotNull();
    assertThat(result.getHaltReason()).isNull();
  }
}
