/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.ConstantinopleGasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

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
  private final MessageFrame messageFrame = mock(MessageFrame.class);
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
    when(messageFrame.getRecipientAddress()).thenReturn(Address.fromHexString(sender));
    final Bytes32 memoryOffset = Bytes32.fromHexString("0xFF");
    final BytesValue codeBytes = BytesValue.fromHexString(code);
    final UInt256 memoryLength = UInt256.of(codeBytes.size());
    when(messageFrame.getStackItem(1)).thenReturn(memoryOffset);
    when(messageFrame.getStackItem(2)).thenReturn(memoryLength.getBytes());
    when(messageFrame.getStackItem(3)).thenReturn(Bytes32.fromHexString(salt));
    when(messageFrame.readMemory(memoryOffset.asUInt256(), memoryLength)).thenReturn(codeBytes);
    when(messageFrame.memoryWordSize()).thenReturn(UInt256.of(500));
    when(messageFrame.calculateMemoryExpansion(any(), any())).thenReturn(UInt256.of(500));
  }

  @Test
  public void shouldCalculateAddress() {
    final Address targetContractAddress = operation.targetContractAddress(messageFrame);
    assertThat(targetContractAddress).isEqualTo(Address.fromHexString(expectedAddress));
  }

  @Test
  public void shouldCalculateGasPrice() {
    assertThat(operation.cost(messageFrame)).isEqualTo(Gas.of(expectedGas));
  }
}
