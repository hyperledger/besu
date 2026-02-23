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

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class RevertOperationTest {

  private final RevertOperation operation = new RevertOperation(new ConstantinopleGasCalculator());

  @Test
  void shouldReturnReason() {
    final Bytes revertReasonBytes = Bytes.fromHexString("726576657274206d657373616765");
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .pushStackItem(Bytes32.fromHexStringLenient("0x0e")) // length = 14
            .pushStackItem(Bytes32.fromHexStringLenient("0x00")) // from = 0
            .initialGas(10_000L)
            .build();
    // Write revert reason to memory
    frame.writeMemory(0, 14, revertReasonBytes, true);
    operation.execute(frame, null);
    assertThat(frame.getRevertReason()).isPresent();
    assertThat(frame.getRevertReason().get()).isEqualTo(revertReasonBytes);
    assertThat(frame.getState()).isEqualTo(MessageFrame.State.REVERT);
  }
}
