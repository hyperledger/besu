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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JumpServiceTest {
  private JumpService jumpService;
  private MessageFrame frame;
  private final Operation.OperationResult SUCCESS = new Operation.OperationResult(0, null);
  private final Operation.OperationResult FAILURE = new Operation.OperationResult(1, null);

  @BeforeEach
  public void setUp() {
    jumpService = spy(new JumpService());
    frame = mock(MessageFrame.class);
  }

  @Test
  public void shouldJumpToValidJumpDest() {
    // bytecode: 0x5B (JUMPDEST), 0x00 (STOP)
    Bytes bytecode = Bytes.fromHexString("0x5b00");
    Code code = new CodeV0(bytecode);
    when(frame.getCode()).thenReturn(code);

    Operation.OperationResult result =
        jumpService.performJump(frame, Bytes.of(0), SUCCESS, FAILURE);

    assertThat(result).isEqualTo(SUCCESS);
    verify(frame).setPC(0);
  }

  @Test
  public void shouldFailForNonJumpDestByte() {
    // bytecode: PUSH1 0x00
    Bytes bytecode = Bytes.fromHexString("0x6000");
    Code code = new CodeV0(bytecode);
    when(frame.getCode()).thenReturn(code);

    Operation.OperationResult result =
        jumpService.performJump(frame, Bytes.of(0), SUCCESS, FAILURE);

    assertThat(result).isEqualTo(FAILURE);
    verify(frame, never()).setPC(anyInt());
  }

  @Test
  public void shouldFailForOutOfBoundsJump() {
    Bytes bytecode = Bytes.fromHexString("0x5b");
    Code code = new CodeV0(bytecode);
    when(frame.getCode()).thenReturn(code);

    Operation.OperationResult result =
        jumpService.performJump(frame, Bytes.of(10), SUCCESS, FAILURE);

    assertThat(result).isEqualTo(FAILURE);
    verify(frame, never()).setPC(anyInt());
  }

  @Test
  public void shouldFailOnInvalidJumpBytes() {
    Bytes bytecode = Bytes.fromHexString("0x5b");
    Code code = new CodeV0(bytecode);
    when(frame.getCode()).thenReturn(code);

    Bytes invalid = Bytes.fromHexString("0xffffffffffffffff"); // too large for int

    Operation.OperationResult result = jumpService.performJump(frame, invalid, SUCCESS, FAILURE);

    assertThat(result).isEqualTo(FAILURE);
    verify(frame, never()).setPC(anyInt());
  }

  @Test
  public void shouldCacheJumpDestBitmask() {
    Bytes bytecode = Bytes.fromHexString("0x5b5b00");
    Code code = spy(new CodeV0(bytecode));
    when(frame.getCode()).thenReturn(code);

    // First jump call — should calculate bitmask
    jumpService.performJump(frame, Bytes.of(0), SUCCESS, FAILURE);
    // Second jump call — should use cached bitmask
    jumpService.performJump(frame, Bytes.of(0), SUCCESS, FAILURE);

    verify(jumpService, atMost(1)).calculateJumpDestBitMask(any());
  }
}
