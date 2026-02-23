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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class BaseFeeOperationTest {
  private final GasCalculator gasCalculator = new BerlinGasCalculator();

  @Test
  void shouldReturnGasCost() {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockValues(new FakeBlockValues(Optional.of(Wei.of(5L))))
            .build();
    final Operation operation = new BaseFeeOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getGasCost()).isEqualTo(gasCalculator.getBaseTierGasCost());
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void shouldWriteBaseFeeToStack() {
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .blockValues(new FakeBlockValues(Optional.of(Wei.of(5L))))
            .build();
    final Operation operation = new BaseFeeOperation(gasCalculator);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0))
        .isEqualTo(
            org.hyperledger.besu.evm.UInt256.fromBytesBE(Wei.of(5L).toBytes().toArrayUnsafe()));
  }

  @Test
  void shouldHaltIfNoBaseFeeInBlockHeader() {
    final MessageFrame frame =
        new TestMessageFrameBuilder().blockValues(new FakeBlockValues(Optional.empty())).build();
    final Operation operation = new BaseFeeOperation(gasCalculator);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
  }
}
