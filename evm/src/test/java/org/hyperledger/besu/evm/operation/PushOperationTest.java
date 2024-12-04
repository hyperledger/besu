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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.toy.ToyBlockValues;
import org.hyperledger.besu.evm.toy.ToyWorld;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PushOperationTest {

  private final GasCalculator gasCalculator = new PragueGasCalculator();
  private final CodeFactory codeFactory =
      new CodeFactory(
          EvmSpecVersion.PRAGUE.getMaxEofVersion(), EvmSpecVersion.PRAGUE.getMaxInitcodeSize());

  private static final Bytes byteCode = Bytes.wrap(new byte[] {0x00, 0x01, 0x02, 0x03});

  @Mock private EVM evm;

  private MessageFrame createMessageFrame(final int pc) {
    MessageFrame frame =
        MessageFrame.builder()
            .worldUpdater(new ToyWorld())
            .originator(Address.ZERO)
            .gasPrice(Wei.ONE)
            .blobGasPrice(Wei.ONE)
            .blockValues(new ToyBlockValues())
            .miningBeneficiary(Address.ZERO)
            .blockHashLookup((l) -> Hash.ZERO)
            .type(MessageFrame.Type.MESSAGE_CALL)
            .initialGas(1)
            .address(Address.ZERO)
            .contract(Address.ZERO)
            .inputData(Bytes32.ZERO)
            .sender(Address.ZERO)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(codeFactory.createCode(byteCode))
            .completer(messageFrame -> {})
            .build();
    frame.setPC(pc);
    return frame;
  }

  @Test
  void unpaddedPushDoesntReachEndCode() {
    MessageFrame frame = createMessageFrame(0);
    final Operation operation = new PushOperation(byteCode.size() - 2, gasCalculator);
    operation.execute(frame, evm);
    assertThat(frame.getStackItem(0).equals(Bytes.fromHexString("0x0102"))).isTrue();
  }

  @Test
  void unpaddedPushUpReachesEndCode() {
    MessageFrame frame = createMessageFrame(0);
    final Operation operation = new PushOperation(byteCode.size() - 1, gasCalculator);
    operation.execute(frame, evm);
    assertThat(frame.getStackItem(0).equals(Bytes.fromHexString("0x010203"))).isTrue();
  }

  @Test
  void paddedPush() {
    MessageFrame frame = createMessageFrame(1);
    final Operation operation = new PushOperation(byteCode.size() - 1, gasCalculator);
    operation.execute(frame, evm);
    assertThat(frame.getStackItem(0).equals(Bytes.fromHexString("0x020300"))).isTrue();
  }

  @Test
  void oobPush() {
    MessageFrame frame = createMessageFrame(byteCode.size());
    final Operation operation = new PushOperation(byteCode.size() - 1, gasCalculator);
    operation.execute(frame, evm);
    assertThat(frame.getStackItem(0).equals(Bytes.EMPTY)).isTrue();
  }
}
