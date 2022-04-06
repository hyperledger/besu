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
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class ExtCodeHashOperationTest {

  private static final Address REQUESTED_ADDRESS = AddressHelpers.ofValue(22222222);

  private final Blockchain blockchain = mock(Blockchain.class);

  private final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
  private final WorldUpdater worldStateUpdater = worldStateArchive.getMutable().updater();

  private final ExtCodeHashOperation operation =
      new ExtCodeHashOperation(new ConstantinopleGasCalculator());
  private final ExtCodeHashOperation operationIstanbul =
      new ExtCodeHashOperation(new IstanbulGasCalculator());

  @Test
  public void shouldCharge400Gas() {
    final OperationResult result = operation.execute(createMessageFrame(REQUESTED_ADDRESS), null);
    assertThat(result.getGasCost().isPresent()).isTrue();
    assertThat(result.getGasCost().getAsLong()).isEqualTo(400L);
  }

  @Test
  public void istanbulShouldCharge700Gas() {
    final OperationResult result =
        operationIstanbul.execute(createMessageFrame(REQUESTED_ADDRESS), null);
    assertThat(result.getGasCost().isPresent()).isTrue();
    assertThat(result.getGasCost().getAsLong()).isEqualTo(700L);
  }

  @Test
  public void shouldReturnZeroWhenAccountDoesNotExist() {
    final Bytes result = executeOperation(REQUESTED_ADDRESS);
    assertThat(result).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldReturnHashOfEmptyDataWhenAccountExistsButDoesNotHaveCode() {
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).getMutable().setBalance(Wei.of(1));
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.EMPTY);
  }

  @Test
  public void shouldReturnZeroWhenAccountExistsButIsEmpty() {
    worldStateUpdater.getOrCreate(REQUESTED_ADDRESS);
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldReturnZeroWhenPrecompiledContractHasNoBalance() {
    assertThat(executeOperation(Address.ECREC)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldReturnEmptyCodeHashWhenPrecompileHasBalance() {
    // Sending money to a precompile causes it to exist in the world state archive.
    worldStateUpdater.getOrCreate(Address.ECREC).getMutable().setBalance(Wei.of(10));
    assertThat(executeOperation(Address.ECREC)).isEqualTo(Hash.EMPTY);
  }

  @Test
  public void shouldGetHashOfAccountCodeWhenCodeIsPresent() {
    final Bytes code = Bytes.fromHexString("0xabcdef");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).getMutable();
    account.setCode(code);
    assertThat(executeOperation(REQUESTED_ADDRESS)).isEqualTo(Hash.hash(code));
  }

  @Test
  public void shouldZeroOutLeftMostBitsToGetAddress() {
    // If EXTCODEHASH of A is X, then EXTCODEHASH of A + 2**160 is X.
    final Bytes code = Bytes.fromHexString("0xabcdef");
    final MutableAccount account = worldStateUpdater.getOrCreate(REQUESTED_ADDRESS).getMutable();
    account.setCode(code);
    final UInt256 value =
        UInt256.fromBytes(Words.fromAddress(REQUESTED_ADDRESS))
            .add(UInt256.valueOf(2).pow(UInt256.valueOf(160)));
    final MessageFrame frame = createMessageFrame(value);
    operation.execute(frame, null);
    assertThat(frame.getStackItem(0)).isEqualTo(Hash.hash(code));
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
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    final MessageFrame frame =
        new MessageFrameTestFixture()
            .worldUpdater(worldStateUpdater)
            .blockHeader(blockHeader)
            .blockchain(blockchain)
            .build();

    frame.pushStackItem(stackItem);
    return frame;
  }
}
