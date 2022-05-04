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
package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.operation.Create2Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayDeque;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Create2OperationTest {

  private final String sender;
  private final String salt;
  private final String code;
  private final String expectedAddress;
  private final int expectedGas;
  private MessageFrame messageFrame;
  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final WrappedEvmAccount account = mock(WrappedEvmAccount.class);
  private final MutableAccount mutableAccount = mock(MutableAccount.class);
  private final EVM evm = mock(EVM.class);
  private final Create2Operation operation =
      new Create2Operation(new ConstantinopleGasCalculator());

  @Parameters(name = "sender: {0}, salt: {1}, code: {2}")
  public static Object[][] params() {
    return new Object[][] {
      {
        "0x0000000000000000000000000000000000000000",
        "0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x00",
        "0x4D1A2e2bB4F88F0250f26Ffff098B0b30B26BF38",
        32006
      },
      {
        "0xdeadbeef00000000000000000000000000000000",
        "0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x00",
        "0xB928f69Bb1D91Cd65274e3c79d8986362984fDA3",
        32006
      },
      {
        "0xdeadbeef00000000000000000000000000000000",
        "0x000000000000000000000000feed000000000000000000000000000000000000",
        "0x00",
        "0xD04116cDd17beBE565EB2422F2497E06cC1C9833",
        32006
      },
      {
        "0x0000000000000000000000000000000000000000",
        "0x0000000000000000000000000000000000000000000000000000000000000000",
        "0xdeadbeef",
        "0x70f2b2914A2a4b783FaEFb75f459A580616Fcb5e",
        32006
      },
      {
        "0x00000000000000000000000000000000deadbeef",
        "0x00000000000000000000000000000000000000000000000000000000cafebabe",
        "0xdeadbeef",
        "0x60f3f640a8508fC6a86d45DF051962668E1e8AC7",
        32006
      },
      {
        "0x00000000000000000000000000000000deadbeef",
        "0x00000000000000000000000000000000000000000000000000000000cafebabe",
        "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
        "0x1d8bfDC5D46DC4f61D6b6115972536eBE6A8854C",
        32012
      },
      {
        "0x0000000000000000000000000000000000000000",
        "0x0000000000000000000000000000000000000000000000000000000000000000",
        "0x",
        "0xE33C0C7F7df4809055C3ebA6c09CFe4BaF1BD9e0",
        32000
      }
    };
  }

  public Create2OperationTest(
      final String sender,
      final String salt,
      final String code,
      final String expectedAddress,
      final int expectedGas) {
    this.sender = sender;
    this.salt = salt;
    this.code = code;
    this.expectedAddress = expectedAddress;
    this.expectedGas = expectedGas;
  }

  @Before
  public void setUp() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final Bytes codeBytes = Bytes.fromHexString(code);
    final UInt256 memoryLength = UInt256.valueOf(codeBytes.size());
    when(account.getMutable()).thenReturn(mutableAccount);
    messageFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .contract(Address.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(Address.fromHexString(sender))
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(Code.createLegacyCode(codeBytes, Hash.hash(codeBytes)))
            .depth(1)
            .completer(__ -> {})
            .address(Address.fromHexString(sender))
            .blockHashLookup(mock(BlockHashLookup.class))
            .blockValues(mock(ProcessableBlockHeader.class))
            .gasPrice(Wei.ZERO)
            .messageFrameStack(new ArrayDeque<>())
            .miningBeneficiary(Address.ZERO)
            .originator(Address.ZERO)
            .initialGas(100_000L)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.pushStackItem(UInt256.fromHexString(salt));
    messageFrame.pushStackItem(memoryLength);
    messageFrame.pushStackItem(memoryOffset);
    messageFrame.pushStackItem(UInt256.ZERO);
    messageFrame.expandMemory(0, 500);
    messageFrame.writeMemory(memoryOffset.trimLeadingZeros().toInt(), code.length(), codeBytes);

    when(mutableAccount.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.updater()).thenReturn(worldUpdater);
    when(evm.getCode(any(), any()))
        .thenAnswer(
            invocation ->
                Code.createLegacyCode(invocation.getArgument(1), invocation.getArgument(0)));
  }

  @Test
  public void shouldCalculateAddress() {
    final Address targetContractAddress = operation.targetContractAddress(messageFrame);
    assertThat(targetContractAddress).isEqualTo(Address.fromHexString(expectedAddress));
  }

  @Test
  public void shouldCalculateGasPrice() {
    final OperationResult result = operation.execute(messageFrame, evm);
    assertThat(result.getHaltReason()).isEmpty();
    assertThat(result.getGasCost().isPresent()).isTrue();
    assertThat(result.getGasCost().getAsLong()).isEqualTo(expectedGas);
  }
}
