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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ExtStaticCallOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private static final EVM EOF_EVM = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
  public static final Code LEGACY_CODE =
      EOF_EVM.getCodeUncached(Bytes.of(ExtStaticCallOperation.OPCODE, 1));
  public static final Code SIMPLE_EOF =
      EOF_EVM.getCodeUncached(Bytes.fromHexString("0xEF00010100040200010001040000000080000000"));
  public static final Code INVALID_EOF =
      EOF_EVM.getCodeUncached(Bytes.fromHexString("0xEF00010100040200010001040000000080000023"));
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
    final ExtStaticCallOperation operation = new ExtStaticCallOperation(new OsakaGasCalculator());

    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(parentGas)
            .code(SIMPLE_EOF)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .build();
    if (warmAddress) {
      messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    }
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getCodeHash()).thenReturn((validCode ? SIMPLE_EOF : INVALID_EOF).getCodeHash());
    when(account.getCode()).thenReturn((validCode ? SIMPLE_EOF : INVALID_EOF).getBytes());
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    var result = operation.execute(messageFrame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(chargedGas);
    assertThat(result.getHaltReason()).isEqualTo(haltReason);

    MessageFrame childFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childFrame.getRemainingGas()).isEqualTo(childGas);

    MessageFrame parentFrame = messageFrame.getMessageFrameStack().getLast();
    assertThat(parentFrame.getStackItem(0)).isEqualTo(stackItem);
  }

  @Test
  void overflowTest() {
    final ExtStaticCallOperation operation = new ExtStaticCallOperation(new OsakaGasCalculator());
    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(400000)
            .code(SIMPLE_EOF)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getCodeHash()).thenReturn(SIMPLE_EOF.getCodeHash());
    when(account.getCode()).thenReturn(SIMPLE_EOF.getBytes());
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);
    while (messageFrame.getDepth() < 1024) {
      messageFrame.getMessageFrameStack().add(messageFrame);
    }

    var result = operation.execute(messageFrame, EOF_EVM);

    assertThat(result.getGasCost()).isEqualTo(100);
    assertThat(result.getHaltReason()).isNull();

    MessageFrame childFrame = messageFrame.getMessageFrameStack().getFirst();
    assertThat(childFrame.getRemainingGas()).isEqualTo(400000L);

    MessageFrame parentFrame = messageFrame.getMessageFrameStack().getLast();
    assertThat(parentFrame.getStackItem(0))
        .isEqualTo(AbstractExtCallOperation.EOF1_EXCEPTION_STACK_ITEM);
  }

  @Test
  void legacyTest() {
    final ExtStaticCallOperation operation = new ExtStaticCallOperation(new OsakaGasCalculator());

    final var messageFrame =
        new TestMessageFrameBuilder()
            .initialGas(400000)
            .code(LEGACY_CODE)
            .pushStackItem(CONTRACT_ADDRESS) // canary for non-returning
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(Bytes.EMPTY)
            .pushStackItem(CONTRACT_ADDRESS)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.warmUpAddress(CONTRACT_ADDRESS);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getCodeHash()).thenReturn(SIMPLE_EOF.getCodeHash());
    when(account.getCode()).thenReturn(SIMPLE_EOF.getBytes());
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    var result = operation.execute(messageFrame, EOF_EVM);

    assertThat(result.getGasCost()).isZero();
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
  }
}
