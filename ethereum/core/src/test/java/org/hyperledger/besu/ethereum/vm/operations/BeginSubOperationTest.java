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
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation.OperationResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public class BeginSubOperationTest {

  private static final IstanbulGasCalculator gasCalculator = new IstanbulGasCalculator();

  private static final int CURRENT_PC = 1;
  private static final Gas BEGIN_SUB_GAS_COST = Gas.of(2);

  private Blockchain blockchain;
  private Address address;
  private WorldUpdater worldStateUpdater;

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

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(address).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();
  }

  @Test
  public void shouldCalculateGasPrice() {

    final BeginSubOperation operation = new BeginSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(100)).returnStack(new ReturnStack()).build();
    frame.setPC(CURRENT_PC);
    final OperationResult result = operation.execute(frame, null);
    assertThat(result.getGasCost()).contains(BEGIN_SUB_GAS_COST);
  }

  @Test
  public void shouldHaltWithInvalidSubRoutineEntryWhenBeginSubIsExecuted() {
    final BeginSubOperation operation = new BeginSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(100))
            .pushStackItem(Bytes32.fromHexString("0x04"))
            .code(new Code(Bytes.fromHexString("0x6104005c")))
            .returnStack(new ReturnStack())
            .build();
    frame.setPC(CURRENT_PC);
    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).contains(ExceptionalHaltReason.INVALID_SUB_ROUTINE_ENTRY);
  }
}
