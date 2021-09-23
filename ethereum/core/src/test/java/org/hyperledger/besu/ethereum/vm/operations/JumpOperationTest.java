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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.OperationRegistry;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.operation.JumpDestOperation;
import org.hyperledger.besu.evm.operation.JumpOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;

public class JumpOperationTest {

  private static final IstanbulGasCalculator gasCalculator = new IstanbulGasCalculator();

  private static final int CURRENT_PC = 1;

  private Blockchain blockchain;
  private Address address;
  private WorldUpdater worldStateUpdater;
  private EVM evm;

  private MessageFrameTestFixture createMessageFrameBuilder(final Gas initialGas) {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    return new MessageFrameTestFixture()
        .address(address)
        .worldUpdater(worldStateUpdater)
        .blockHeader(blockHeader)
        .blockchain(blockchain)
        .initialGas(initialGas);
  }

  @Before
  public void init() {
    blockchain = mock(Blockchain.class);

    address = Address.fromHexString("0x18675309");

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(address).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();

    final OperationRegistry registry = new OperationRegistry();
    registry.put(new JumpOperation(gasCalculator));
    registry.put(new JumpDestOperation(gasCalculator));
    evm = new EVM(registry, gasCalculator);
  }

  @Test
  public void shouldJumpWhenLocationIsJumpDest() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(10_000))
            .pushStackItem(UInt256.fromHexString("0x03"))
            .code(new Code(Bytes.fromHexString("0x6003565b00")))
            .build();
    frame.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frame, evm);
    assertThat(result.getHaltReason()).isEmpty();
  }

  @Test
  public void shouldJumpWhenLocationIsJumpDestAndAtEndOfCode() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(10_000))
            .pushStackItem(UInt256.fromHexString("0x03"))
            .code(new Code(Bytes.fromHexString("0x6003565b")))
            .build();
    frame.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frame, evm);
    assertThat(result.getHaltReason()).isEmpty();
  }

  @Test
  public void shouldHaltWithInvalidJumDestinationWhenLocationIsOutsideOfCodeRange() {
    final JumpOperation operation = new JumpOperation(gasCalculator);
    final MessageFrame frameDestinationGreaterThanCodeSize =
        createMessageFrameBuilder(Gas.of(100))
            .pushStackItem(UInt256.fromHexString("0xFFFFFFFF"))
            .code(new Code(Bytes.fromHexString("0x6801000000000000000c565b00")))
            .build();
    frameDestinationGreaterThanCodeSize.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frameDestinationGreaterThanCodeSize, null);
    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);

    final MessageFrame frameDestinationEqualsToCodeSize =
        createMessageFrameBuilder(Gas.of(100))
            .pushStackItem(UInt256.fromHexString("0x04"))
            .code(new Code(Bytes.fromHexString("0x60045600")))
            .build();
    frameDestinationEqualsToCodeSize.setPC(CURRENT_PC);

    final OperationResult result2 = operation.execute(frameDestinationEqualsToCodeSize, null);
    assertThat(result2.getHaltReason()).contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  }
}
