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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.LondonGasCalculator;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation.OperationResult;
import org.hyperledger.besu.ethereum.vm.OperationRegistry;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.Mockito.mock;

public class CallOperationTest {

  private static final GasCalculator gasCalculator = mock(GasCalculator.class);

  private static final int CURRENT_PC = 1;

  private Blockchain blockchain;
  private Address caller = Address.fromHexString("0x000000000000000000000000000000ca1100f022");
  private Address callee = Address.fromHexString("0x00000000000000000000000000000000b0b0face");
  private WorldUpdater worldStateUpdater;
  private EVM evm;

  private MessageFrameTestFixture createMessageFrameBuilder(final Gas initialGas) {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();
    return new MessageFrameTestFixture()
        .address(caller)
        .worldState(worldStateUpdater)
        .blockHeader(blockHeader)
        .blockchain(blockchain)
        .initialGas(initialGas);
  }

  @Before
  public void init() {
    blockchain = mock(Blockchain.class);

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    worldStateUpdater = worldStateArchive.getMutable().updater();
    worldStateUpdater.getOrCreate(caller).getMutable().setBalance(Wei.of(1));
    worldStateUpdater.commit();

    final OperationRegistry registry = new OperationRegistry();
    registry.put(new CallOperation(gasCalculator), 0);
    evm = new EVM(registry, gasCalculator);
  }

  @Test
  public void shouldNotRecalcJumpsOnSubcall () {
    final CallOperation operation = new CallOperation(gasCalculator);
    final MessageFrame frameDestinationGreaterThanCodeSize =
            createMessageFrameBuilder(Gas.of(100))
                    .pushStackItem(UInt256.fromHexString("0xFFFFFFFF"))
                    .code(new Code(Bytes.fromHexString("0x6801000000000000000c565b00")))
                    .build();
    frameDestinationGreaterThanCodeSize.setPC(CURRENT_PC);

    final OperationResult result = operation.execute(frameDestinationGreaterThanCodeSize, null);
  }
}
