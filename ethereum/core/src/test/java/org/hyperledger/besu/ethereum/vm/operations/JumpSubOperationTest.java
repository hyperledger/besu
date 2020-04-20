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
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.BerlinGasCalculator;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationRegistry;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public class JumpSubOperationTest {

  private static final GasCalculator gasCalculator = new BerlinGasCalculator();

  private static final int CURRENT_PC = 1;
  private static final Gas JUMP_SUB_GAS_COST = Gas.of(8);

  private Blockchain blockchain;
  private Address address;
  private WorldStateArchive worldStateArchive;
  private WorldUpdater worldStateUpdater;
  private EVM evm;

  private MessageFrameTestFixture createMessageFrameBuilder(final Gas initialGas) {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    return new MessageFrameTestFixture()
        .address(address)
        .worldState(worldStateUpdater)
        .blockHeader(blockHeader)
        .blockchain(blockchain)
        .initialGas(initialGas);
  }

  @Before
  public void init() {
    blockchain = mock(Blockchain.class);

    address = Address.fromHexString("0x18675309");

    worldStateArchive = createInMemoryWorldStateArchive();

    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(address).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();

    final OperationRegistry registry = new OperationRegistry();
    registry.put(new BeginSubOperation(gasCalculator), 0);
    registry.put(new JumpSubOperation(gasCalculator), 0);
    registry.put(new ReturnOperation(gasCalculator), 0);
    evm = new EVM(registry, gasCalculator);
  }

  @Test
  public void shouldCalculateGasPrice() {

    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);
    assertThat(operation.cost(frame)).isEqualTo(JUMP_SUB_GAS_COST);
  }

  @Test
  public void shouldHaltWithInvalidJumDestinationWhenLocationIsNotBeginSub() {
    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .pushStackItem(Bytes32.fromHexString("0x05"))
            .code(new Code(Bytes.fromHexString("0x6004b300b2b7")))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);
    assertThat(operation.exceptionalHaltCondition(frame, null, evm))
        .contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }

  @Test
  public void shouldJumpWhenLocationIsBeginSub() {
    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .pushStackItem(Bytes32.fromHexString("0x04"))
            .code(new Code(Bytes.fromHexString("0x6004b300b2b7")))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);

    assertThat(operation.exceptionalHaltCondition(frame, null, evm)).isNotPresent();
    operation.execute(frame);
    assertThat(frame.popReturnStackItem()).isEqualTo(CURRENT_PC + 1);
  }

  @Test
  public void shouldHaltWithInvalidJumDestinationWhenLocationIsOutsideOfCodeRange() {
    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .pushStackItem(Bytes32.fromHexString("0xFFFFFFFF"))
            .code(new Code(Bytes.fromHexString("0x6801000000000000000cb300b26011b3b7b2b7")))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);

    assertThat(operation.exceptionalHaltCondition(frame, null, null))
        .contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }

  @Test
  public void shouldHaltWithInvalidJumDestinationWhenLocationIsNotAvailable() {
    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);

    assertThat(operation.exceptionalHaltCondition(frame, null, null))
        .contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }

  @Test
  public void shouldHaltWithTooManyStackItemsWhenReturnStackIsFull() {
    final JumpSubOperation operation = new JumpSubOperation(gasCalculator);
    final ReturnStack returnStack = new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1)).returnStack(returnStack).build();
    frame.setPC(CURRENT_PC);

    assertThat(frame.isReturnStackFull()).isFalse();

    IntStream.rangeClosed(1, MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE)
        .forEach(frame::pushReturnStackItem);
    assertThat(frame.isReturnStackFull()).isTrue();
    assertThat(returnStack.size()).isEqualTo(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE);
    assertThat(operation.exceptionalHaltCondition(frame, null, null))
        .contains(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
  }
}
