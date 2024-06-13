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
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PragueEOFGasCalculator;
import org.hyperledger.besu.evm.operation.AbstractExtCallOperation;
import org.hyperledger.besu.evm.operation.ExtCallOperation;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ExtCallOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private final EVM evm = mock(EVM.class);
  public static final Code SIMPLE_EOF =
      CodeFactory.createCode(Bytes.fromHexString("0xEF00010100040200010001040000000080000000"), 1);
  public static final Code INVALID_EOF =
      CodeFactory.createCode(Bytes.fromHexString("0xEF00010100040200010001040000000080000023"), 1);
  private static final Address CONTRACT_ADDRESS = Address.fromHexString("0xc0de");

  static Iterable<Arguments> data() {
    return List.of(
        Arguments.of(
            "gas",
            99,
            100,
            99,
            ExceptionalHaltReason.INSUFFICIENT_GAS,
            CONTRACT_ADDRESS,
            true,
            true),
        Arguments.of(
            "gas",
            5000,
            100,
            5000,
            null,
            AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM,
            true,
            true),
        Arguments.of(
            "gas",
            7300,
            100,
            7300,
            null,
            AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM,
            true,
            true),
        Arguments.of(
            "Cold Address",
            7300,
            2600,
            7300,
            null,
            AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM,
            true,
            false),
        Arguments.of("gas", 64000, 59000, 58900, null, CONTRACT_ADDRESS, true, true),
        Arguments.of("gas", 384100, 378100, 378000, null, CONTRACT_ADDRESS, true, true),
        Arguments.of(
            "Invalid code",
            384100,
            100,
            384100,
            ExceptionalHaltReason.INVALID_CODE,
            CONTRACT_ADDRESS,
            false,
            true));
  }

  @ParameterizedTest(name = "{index}: {0} {1}")
  @MethodSource("data")
  void gasTest(
      final String name,
      final long parentGas,
      final long chargedGas,
      final long childGas,
      final ExceptionalHaltReason haltReason,
      final Bytes stackItem,
      final boolean validCode,
      final boolean warmAddress) {
    final ExtCallOperation operation = new ExtCallOperation(new PragueEOFGasCalculator());

    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(parentGas)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .build();
    if (warmAddress) {
      messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    }
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);
    when(evm.getCode(any(), any())).thenReturn(validCode ? SIMPLE_EOF : INVALID_EOF);

    var result = operation.execute(messageFrame, evm);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    MessageFrame childFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childFrame.getRemainingGas()).isEqualTo(childGas);

    MessageFrame parentFrame = messageFrame.getMessageFrameStack().getLast();
    assertThat(parentFrame.getStackItem(0)).isEqualTo(stackItem);
  }

  static Iterable<Arguments> valueData() {
    return List.of(
        Arguments.of(
            "enough value",
            40000,
            35000,
            25900,
            null,
            CONTRACT_ADDRESS,
            Wei.of(100),
            Wei.of(200),
            false),
        Arguments.of(
            "static context",
            40000,
            9000,
            40000,
            ExceptionalHaltReason.ILLEGAL_STATE_CHANGE,
            CONTRACT_ADDRESS,
            Wei.of(100),
            Wei.of(200),
            true),
        Arguments.of(
            "not enough value",
            40000,
            9100,
            40000,
            null,
            AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM,
            Wei.of(1000),
            Wei.of(200),
            false),
        Arguments.of(
            "too little gas",
            5000,
            9100,
            5000,
            ExceptionalHaltReason.INSUFFICIENT_GAS,
            CONTRACT_ADDRESS,
            Wei.of(100),
            Wei.of(200),
            false));
  }

  @ParameterizedTest(name = "{index}: {0} {1}")
  @MethodSource("valueData")
  void callWithValueTest(
      final String name,
      final long parentGas,
      final long chargedGas,
      final long childGas,
      final ExceptionalHaltReason haltReason,
      final Bytes stackItem,
      final Wei valueSent,
      final Wei valueWeiHave,
      final boolean isStatic) {
    final ExtCallOperation operation = new ExtCallOperation(new PragueEOFGasCalculator());

    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(parentGas)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(valueSent)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .isStatic(isStatic)
            .build();
    messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    when(account.getBalance()).thenReturn(valueWeiHave);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);
    when(evm.getCode(any(), any())).thenReturn(SIMPLE_EOF);

    var result = operation.execute(messageFrame, evm);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    MessageFrame childFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childFrame.getRemainingGas()).isEqualTo(childGas);

    MessageFrame parentFrame = messageFrame.getMessageFrameStack().getLast();
    assertThat(parentFrame.getStackItem(0)).isEqualTo(stackItem);
  }

  @Test
  void overflowTest() {
    final ExtCallOperation operation = new ExtCallOperation(new PragueEOFGasCalculator());

    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(400000)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);
    when(evm.getCode(any(), any())).thenReturn(SIMPLE_EOF);
    while (messageFrame.getDepth() < 1024) {
      messageFrame.getMessageFrameStack().add(messageFrame);
    }

    var result = operation.execute(messageFrame, evm);

    assertThat(result.getGasCost()).isEqualTo(100);
    assertThat(result.getHaltReason()).isNull();

    MessageFrame childFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childFrame.getRemainingGas()).isEqualTo(400000L);

    MessageFrame parentFrame = messageFrame.getMessageFrameStack().getLast();
    assertThat(parentFrame.getStackItem(0))
        .isEqualTo(AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM);
  }
}
