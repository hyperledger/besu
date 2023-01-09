/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeSection;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.ReturnStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.RetFOperation;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class RetFOperationTest {

  @Test
  void retFHappyPath() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final Code mockCode = mock(Code.class);
    final Bytes code = Bytes.fromHexString("00" + "b1" + "00");
    when(mockCode.getBytes()).thenReturn(code);

    final CodeSection codeSection = new CodeSection(0, 1, 2, 3, 0);
    when(mockCode.getCodeSection(1)).thenReturn(codeSection);

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(1)
            .section(1)
            .initialGas(10L)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .build();
    messageFrame.pushReturnStackItem(new ReturnStack.ReturnStackItem(2, 3, 1));

    RetFOperation retF = new RetFOperation(gasCalculator);
    Operation.OperationResult retFResult = retF.execute(messageFrame, null);

    assertThat(retFResult.getHaltReason()).isNull();
    assertThat(retFResult.getPcIncrement()).isEqualTo(1);
    assertThat(messageFrame.getSection()).isEqualTo(2);
    assertThat(messageFrame.getPC()).isEqualTo(3);
    assertThat(messageFrame.returnStackSize()).isEqualTo(1);
  }

  @Test
  void retFFinalReturn() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final Code mockCode = mock(Code.class);
    final Bytes code = Bytes.fromHexString("00" + "b1" + "00");
    when(mockCode.getBytes()).thenReturn(code);

    final CodeSection codeSection = new CodeSection(0, 1, 2, 3, 0);
    when(mockCode.getCodeSection(1)).thenReturn(codeSection);

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(1)
            .section(1)
            .initialGas(10L)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .build();

    RetFOperation retF = new RetFOperation(gasCalculator);
    Operation.OperationResult retFResult = retF.execute(messageFrame, null);

    assertThat(retFResult.getHaltReason()).isNull();
    assertThat(retFResult.getPcIncrement()).isEqualTo(1);
    assertThat(messageFrame.getState()).isEqualTo(MessageFrame.State.CODE_SUCCESS);
    assertThat(messageFrame.getOutputData()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void retFIncorrectOutput() {
    final GasCalculator gasCalculator = mock(GasCalculator.class);
    final Code mockCode = mock(Code.class);
    final Bytes code = Bytes.fromHexString("00" + "b1" + "00");
    when(mockCode.getBytes()).thenReturn(code);

    final CodeSection codeSection = new CodeSection(0, 1, 2, 3, 0);
    when(mockCode.getCodeSection(1)).thenReturn(codeSection);

    MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(mockCode)
            .pc(1)
            .section(1)
            .initialGas(10L)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .build();

    RetFOperation retF = new RetFOperation(gasCalculator);
    Operation.OperationResult retFResult = retF.execute(messageFrame, null);

    assertThat(retFResult.getHaltReason())
        .isEqualTo(ExceptionalHaltReason.INCORRECT_CODE_SECTION_RETURN_OUTPUTS);
    assertThat(retFResult.getPcIncrement()).isEqualTo(1);
    assertThat(messageFrame.getSection()).isEqualTo(1);
    assertThat(messageFrame.getPC()).isEqualTo(1);
    assertThat(messageFrame.returnStackSize()).isZero();
    assertThat(messageFrame.peekReturnStack()).isNull();
  }
}
