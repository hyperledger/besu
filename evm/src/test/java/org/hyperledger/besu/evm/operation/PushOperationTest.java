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
import static org.hyperledger.besu.evm.operation.PushOperation.staticOperation;

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class PushOperationTest {

  private static final byte[] byteCode = new byte[] {0x00, 0x01, 0x02, 0x03};

  private MessageFrame createFrame() {
    return new TestMessageFrameBuilder().build();
  }

  @Test
  void unpaddedPushDoesntReachEndCode() {
    final MessageFrame frame = createFrame();
    staticOperation(frame, frame.stackData(), byteCode, 0, byteCode.length - 2);
    assertThat(frame.getStackItem(0))
        .isEqualTo(UInt256.fromBytesBE(Bytes.fromHexString("0x0102").toArrayUnsafe()));
  }

  @Test
  void unpaddedPushUpReachesEndCode() {
    final MessageFrame frame = createFrame();
    staticOperation(frame, frame.stackData(), byteCode, 0, byteCode.length - 1);
    assertThat(frame.getStackItem(0))
        .isEqualTo(UInt256.fromBytesBE(Bytes.fromHexString("0x010203").toArrayUnsafe()));
  }

  @Test
  void paddedPush() {
    final MessageFrame frame = createFrame();
    staticOperation(frame, frame.stackData(), byteCode, 1, byteCode.length - 1);
    assertThat(frame.getStackItem(0))
        .isEqualTo(UInt256.fromBytesBE(Bytes.fromHexString("0x020300").toArrayUnsafe()));
  }

  @Test
  void oobPush() {
    final MessageFrame frame = createFrame();
    staticOperation(frame, frame.stackData(), byteCode, byteCode.length, byteCode.length - 1);
    assertThat(frame.getStackItem(0)).isEqualTo(UInt256.ZERO);
  }
}
