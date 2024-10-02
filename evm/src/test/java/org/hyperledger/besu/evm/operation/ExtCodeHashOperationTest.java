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
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.testutils.FakeBlockValues;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.toy.ToyWorld;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class ExtCodeHashOperationTest {

  private static final Address REQUESTED_ADDRESS = Address.fromHexString("0x22222222");

  private final ToyWorld toyWorld = new ToyWorld();
  private final WorldUpdater worldStateUpdater = toyWorld.updater();

  private final ExtCodeHashOperation operation =
      new ExtCodeHashOperation(new ConstantinopleGasCalculator(), false);
  private final ExtCodeHashOperation operationIstanbul =
      new ExtCodeHashOperation(new IstanbulGasCalculator(), false);
  private final ExtCodeHashOperation operationEOF =
      new ExtCodeHashOperation(new PragueGasCalculator(), true);

  @Test
  void shouldCharge400Gas() {
    final OperationResult result = operation.execute(createMessageFrame(REQUESTED_ADDRESS), null);
    assertThat(result.getGasCost()).isEqualTo(400L);
  }

  @Test
  void istanbulShouldCharge700Gas() {
    final OperationResult result =
        operationIstanbul.execute(createMessageFrame(REQUESTED_ADDRESS), null);
    assertThat(result.getGasCost()).isEqualTo(700L);
  }

  @Test
  void shouldReturnZeroWhenAccountDoesNotExist() {
    final Bytes result = executeOperation(REQUESTED_ADDRESS);
    assertThat(result.trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldReturnHashOfEmptyDataWhenAccountExistsButDoesNotHaveCode() {
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).setBalance(Wei.of(1));
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.EMPTY);
  }

  @Test
  void shouldReturnZeroWhenAccountExistsButIsEmpty() {
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    assertThat(executeOperation(REQUESTED_ADDRESS).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldReturnZeroWhenPrecompiledContractHasNoBalance() {
    assertThat(executeOperation(Address.ECREC).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldReturnEmptyCodeHashWhenPrecompileHasBalance() {
    // Sending money to a precompile causes it to exist in the world state archive.
    worldStateUpdater.getOrCreate(Address.ECREC).setBalance(Wei.of(10));
    assertThat(executeOperation(Address.ECREC)).isEqualTo(Hash.EMPTY);
  }

  @Test
  void shouldGetHashOfAccountCodeWhenCodeIsPresent() {
    final Bytes code = Bytes.fromHexString("0xabcdef");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    account.setCode(code);
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.hash(code));
  }

  @Test
  void shouldZeroOutLeftMostBitsToGetAddress() {
    // If EXTCODEHASH of A is X, then EXTCODEHASH of A + 2**160 is X.
    final Bytes code = Bytes.fromHexString("0xabcdef");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    account.setCode(code);
    final UInt256 value =
        UInt256.fromBytes(Words.fromAddress(REQUESTED_ADDRESS))
            .add(UInt256.valueOf(2).pow(UInt256.valueOf(160)));
    final MessageFrame frame = createMessageFrame(value);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(Hash.hash(code));
  }

  @Test
  void shouldGetNonEOFHash() {
    final Bytes code = Bytes.fromHexString("0xEFF09f918bf09f9fa9");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    account.setCode(code);
    final UInt256 value =
        UInt256.fromBytes(Words.fromAddress(REQUESTED_ADDRESS))
            .add(UInt256.valueOf(2).pow(UInt256.valueOf(160)));

    final MessageFrame frame = createMessageFrame(value);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(Hash.hash(code));

    final MessageFrame frameIstanbul = createMessageFrame(value);
    operationIstanbul.execute(frameIstanbul, null);
    assertThat(frameIstanbul.getStackItem(0)).isEqualTo(Hash.hash(code));

    final MessageFrame frameEOF = createMessageFrame(value);
    operationEOF.execute(frameEOF, null);
    assertThat(frameEOF.getStackItem(0)).isEqualTo(Hash.hash(code));
  }

  @Test
  void shouldGetEOFHash() {
    final Bytes code = Bytes.fromHexString("0xEF009f918bf09f9fa9");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    account.setCode(code);
    final UInt256 value =
        UInt256.fromBytes(Words.fromAddress(REQUESTED_ADDRESS))
            .add(UInt256.valueOf(2).pow(UInt256.valueOf(160)));

    final MessageFrame frame = createMessageFrame(value);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(Hash.hash(code));

    final MessageFrame frameIstanbul = createMessageFrame(value);
    operationIstanbul.execute(frameIstanbul, null);
    assertThat(frameIstanbul.getStackItem(0)).isEqualTo(Hash.hash(code));

    final MessageFrame frameEOF = createMessageFrame(value);
    operationEOF.execute(frameEOF, null);
    assertThat(frameEOF.getStackItem(0)).isEqualTo(Hash.hash(Bytes.fromHexString("0xef00")));
  }

  private Bytes executeOperation(final Address requestedAddress) {
    final MessageFrame frame = createMessageFrame(requestedAddress);
    operation.execute(frame, null);
    return frame.getStackItem(0);
  }

  private MessageFrame createMessageFrame(final Address requestedAddress) {
    final UInt256 stackItem = Words.fromAddress(requestedAddress);
    return createMessageFrame(stackItem);
  }

  private MessageFrame createMessageFrame(final UInt256 stackItem) {
    final BlockValues blockValues = new FakeBlockValues(1337);
    final MessageFrame frame =
        new TestMessageFrameBuilder()
            .worldUpdater(worldStateUpdater)
            .blockValues(blockValues)
            .build();

    frame.pushStackItem(stackItem);
    return frame;
  }
}
