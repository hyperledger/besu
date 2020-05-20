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
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import org.junit.Before;
import org.junit.Test;

public class ReturnSubOperationTest {

  private static final GasCalculator gasCalculator = new BerlinGasCalculator();

  private static final int CURRENT_PC = 1;
  private static final Gas RETURN_SUB_GAS_COST = Gas.of(2);

  private Blockchain blockchain;
  private Address address;
  private WorldStateArchive worldStateArchive;
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

    worldStateArchive = createInMemoryWorldStateArchive();

    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(address).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();
  }

  @Test
  public void shouldCalculateGasPrice() {

    final ReturnSubOperation operation = new ReturnSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);
    assertThat(operation.cost(frame)).isEqualTo(RETURN_SUB_GAS_COST);
  }

  @Test
  public void shouldHaltWithInvalidRetSubWhenReturnStackIsEmpty() {
    final ReturnSubOperation operation = new ReturnSubOperation(gasCalculator);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1))
            .returnStack(new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE))
            .build();
    frame.setPC(CURRENT_PC);
    assertThat(operation.exceptionalHaltCondition(frame, null, null))
        .contains(ExceptionalHaltReason.INVALID_RETSUB);
  }

  @Test
  public void shouldReturnToTheLastLocation() {
    final int RETURN_LOCATION = 0;
    final ReturnSubOperation operation = new ReturnSubOperation(gasCalculator);
    final ReturnStack returnStack = new ReturnStack(MessageFrame.DEFAULT_MAX_RETURN_STACK_SIZE);
    final MessageFrame frame =
        createMessageFrameBuilder(Gas.of(1)).returnStack(returnStack).build();
    frame.setPC(CURRENT_PC);
    returnStack.push(RETURN_LOCATION);
    assertThat(operation.exceptionalHaltCondition(frame, null, null)).isNotPresent();
    operation.execute(frame);
    assertThat(frame.getPC()).isEqualTo(RETURN_LOCATION);
  }
}
